package org.matsim.alonso_mora.shifts;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.AlonsoMoraOptimizer;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicleFactory;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.Constraint;
import org.matsim.alonso_mora.scheduling.AlonsoMoraScheduler;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler.OperationalVoter;
import org.matsim.alonso_mora.travel_time.TravelTimeEstimator;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.extension.operations.DrtWithOperationsConfigGroup;
import org.matsim.contrib.drt.extension.operations.shifts.dispatcher.DrtShiftDispatcher;
import org.matsim.contrib.drt.extension.operations.shifts.optimizer.ShiftDrtOptimizer;
import org.matsim.contrib.drt.extension.operations.shifts.schedule.ShiftDrtStayTaskEndTimeCalculator;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.schedule.DrtStayTaskEndTimeCalculator;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater.StayTaskEndTimeCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;

public class ShiftAlonsoMoraModule extends AbstractDvrpModeQSimModule {
	private final DrtWithOperationsConfigGroup drtConfig;
	private final AlonsoMoraConfigGroup amConfig;

	public ShiftAlonsoMoraModule(DrtWithOperationsConfigGroup drtConfig, AlonsoMoraConfigGroup amConfig) {
		super(drtConfig.getMode());
		this.drtConfig = drtConfig;
		this.amConfig = amConfig;
	}

	@Override
	protected void configureQSim() {
		bindModal(AlonsoMoraVehicleFactory.class).toProvider(modalProvider(getter -> {
			return v -> new ShiftAlonsoMoraVehicle(v);
		}));

		// TODO: This can become a general binding in DRT
		bindModal(StayTaskEndTimeCalculator.class).toProvider(modalProvider(getter -> {
			return new ShiftDrtStayTaskEndTimeCalculator(drtConfig.getDrtOperationsParams().getShiftsParams().orElseThrow(),
					new DrtStayTaskEndTimeCalculator((dvrpVehicle, dropoffRequests, pickupRequests) -> drtConfig.stopDuration));
		}));

		bindModal(OperationalVoter.class).toProvider(modalProvider(getter -> {
			return new ShiftOperationalVoter();
		}));

		bindModal(Constraint.class).toProvider(modalProvider(getter -> {
			TravelTimeEstimator travelTimeEstimator = getter.getModal(TravelTimeEstimator.class);
			return new AlonsoMoraShiftConstraint(travelTimeEstimator);
		}));

		// Wrap the Shift opimizer around the AlonsoMoraOptimizer instead of
		// DefaultDrtOptimizer
		addModalComponent(DrtOptimizer.class,
				modalProvider((getter) -> new ShiftDrtOptimizer(getter.getModal(AlonsoMoraOptimizer.class),
						getter.getModal(DrtShiftDispatcher.class), getter.getModal(ScheduleTimingUpdater.class))));

		bindModal(AlonsoMoraScheduler.class).toProvider(modalProvider(getter -> {
			StayTaskEndTimeCalculator endTimeCalculator = getter.getModal(StayTaskEndTimeCalculator.class);
			DrtTaskFactory taskFactory = getter.getModal(DrtTaskFactory.class);
			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);

			TravelTime travelTime = getter.getModal(TravelTime.class);
			Network network = getter.getModal(Network.class);

			OperationalVoter operationalVoter = getter.getModal(OperationalVoter.class);

			return new ShiftAlonsoMoraScheduler(taskFactory, drtConfig.stopDuration,
					amConfig.getCheckDeterminsticTravelTimes(), amConfig.getRerouteDuringScheduling(), travelTime,
					network, endTimeCalculator, router, operationalVoter);
		}));
	}
}
