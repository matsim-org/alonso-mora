package org.matsim.alonso_mora;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import org.matsim.alonso_mora.algorithm.AlonsoMoraAlgorithm;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequestFactory;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.mobsim.framework.events.MobsimBeforeSimStepEvent;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;

/**
 * Entry point for the Alonso-Mora dispatcher in DRT that implements
 * DrtOptimizer, receives requests, and delegates dispatching in each time step.
 */
public class AlonsoMoraOptimizer implements DrtOptimizer {
	private final ScheduleTimingUpdater scheduleTimingUpdater;
	private final Fleet fleet;

	private final AlonsoMoraAlgorithm algorithm;
	private final AlonsoMoraRequestFactory requestFactory;

	private final List<DrtRequest> submittedRequests = new LinkedList<>();
	private final double assignmentInterval;

	private final ForkJoinPool forkJoinPool;
	private final LeastCostPathCalculator router;
	private final TravelTime travelTime;

	private final InformationCollector collector;

	public AlonsoMoraOptimizer(AlonsoMoraAlgorithm algorithm, AlonsoMoraRequestFactory requestFactory,
			ScheduleTimingUpdater scheduleTimingUpdater, Fleet fleet, double assignmentInterval,
			ForkJoinPool forkJoinPool, LeastCostPathCalculator router, TravelTime travelTime,
			InformationCollector collector) {
		this.algorithm = algorithm;
		this.requestFactory = requestFactory;
		this.assignmentInterval = assignmentInterval;
		this.scheduleTimingUpdater = scheduleTimingUpdater;
		this.fleet = fleet;
		this.forkJoinPool = forkJoinPool;
		this.router = router;
		this.travelTime = travelTime;
		this.collector = collector;
	}

	@Override
	public void requestSubmitted(Request request) {
		submittedRequests.add((DrtRequest) request);
	}

	@Override
	public void notifyMobsimBeforeSimStep(@SuppressWarnings("rawtypes") MobsimBeforeSimStepEvent e) {
		double now = e.getSimulationTime();

		if (now % assignmentInterval == 0) {
			List<AlonsoMoraRequest> processedRequests = new LinkedList<>();

			List<DrtRequest> submittedRequests = new ArrayList<>(this.submittedRequests);
			this.submittedRequests.clear();

			List<VrpPathWithTravelData> paths = new ArrayList<>(Collections.nCopies(submittedRequests.size(), null));

			// Here this direct routing is performed
			forkJoinPool.submit(() -> {
				IntStream.range(0, submittedRequests.size()).parallel().forEach(i -> {
					DrtRequest request = submittedRequests.get(i);
					paths.set(i, VrpPaths.calcAndCreatePath(request.getFromLink(), request.getToLink(),
							request.getEarliestStartTime(), router, travelTime));
				});
			}).join();

			for (int i = 0; i < submittedRequests.size(); i++) {
				DrtRequest drtRequest = submittedRequests.get(i);

				double earliestDepartureTime = drtRequest.getEarliestStartTime();
				double directArrivalTime = paths.get(i).getTravelTime() + earliestDepartureTime;
				double directRideDistance = VrpPaths.calcDistance(paths.get(i));

				AlonsoMoraRequest request = requestFactory.createRequest(drtRequest, directArrivalTime,
						earliestDepartureTime, directRideDistance);

				processedRequests.add(request);
			}

			for (DvrpVehicle v : fleet.getVehicles().values()) {
				scheduleTimingUpdater.updateTimings(v);
			}

			Optional<AlonsoMoraAlgorithm.Information> information = algorithm.run(processedRequests,
					e.getSimulationTime());

			if (information.isPresent()) {
				collector.addInformation(e.getSimulationTime(), information.get());
			}
		}
	}

	@Override
	public void nextTask(DvrpVehicle vehicle) {
		scheduleTimingUpdater.updateBeforeNextTask(vehicle);
		vehicle.getSchedule().nextTask();
	}
}
