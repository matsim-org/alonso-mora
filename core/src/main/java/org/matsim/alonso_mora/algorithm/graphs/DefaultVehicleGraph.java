package org.matsim.alonso_mora.algorithm.graphs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop.StopType;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.function.AlonsoMoraFunction;

import com.google.common.base.Verify;

/**
 * Default implementation for the trip-vehicle graph which creates all possible
 * trips (routes) for the requests and one vehicle.
 * 
 * @author sebhoerl
 */
public class DefaultVehicleGraph implements VehicleGraph {
	private final RequestGraph requestGraph;
	private final AlonsoMoraFunction function;

	private final AlonsoMoraVehicle vehicle;

	private final List<List<AlonsoMoraTrip>> trips = new ArrayList<>();
	private final Set<AlonsoMoraRequest> requests = new HashSet<>();

	private final int tripLimitPerVehicle;
	private final int tripLimitPerSequenceLength;

	private int numberOfTrips = 0;

	public DefaultVehicleGraph(AlonsoMoraFunction function, RequestGraph requestGraph, AlonsoMoraVehicle vehicle,
			int tripLimitPerVehicle, int tripLimitPerSequenceLength) {
		this.vehicle = vehicle;
		this.requestGraph = requestGraph;
		this.function = function;
		this.tripLimitPerVehicle = tripLimitPerVehicle;
		this.tripLimitPerSequenceLength = tripLimitPerSequenceLength;

		ensureTripListSize(vehicle.getVehicle().getCapacity() * 2);
	}

	private void ensureTripListSize(int numberOfPassengers) {
		while (trips.size() < numberOfPassengers) {
			trips.add(new LinkedList<>());
		}
	}

	double startTime = Double.NaN;
	double nextMessageTime = Double.NaN;
	double messageInterval = 60.0;

	Logger logger = Logger.getLogger(DefaultVehicleGraph.class);

	private void messageCall() {
		double currentTime = System.nanoTime() * 1e-9;

		if (Double.isNaN(startTime)) {
			startTime = currentTime;
			nextMessageTime = currentTime + messageInterval;
		} else if (currentTime >= nextMessageTime) {
			nextMessageTime += messageInterval;

			double elapsed = currentTime - startTime;
			logger.info(hashCode() + " elapsed:" + elapsed + " trips:" + numberOfTrips + " requests:" + requests.size()
					+ " levels:" + trips.stream().map(l -> String.valueOf(l.size())).collect(Collectors.joining(",")));
		}
	}

	@Override
	public void addRequest(AlonsoMoraRequest request, double now, boolean considerThresholds) {
		Optional<AlonsoMoraFunction.Result> unpooledResult = function.calculateRoute(Arrays.asList(request), vehicle,
				now);

		if (unpooledResult.isPresent()) {
			addRequest(request, now, unpooledResult.get(), considerThresholds);
		}
	}

	@Override
	public void addRequest(AlonsoMoraRequest request, double now, AlonsoMoraFunction.Result unpooledResult,
			boolean considerThresholds) {
		Verify.verify(requests.add(request), "Request is already registered");
		messageCall();

		if (considerThresholds && tripLimitPerVehicle > 0 && numberOfTrips >= tripLimitPerVehicle) {
			return; // Limit has been reached
		}

		if (!(tripLimitPerSequenceLength > 0 && trips.get(0).size() >= tripLimitPerSequenceLength)) {
			// Limit has not been reached
			trips.get(0).add(new AlonsoMoraTrip(vehicle, Arrays.asList(request), unpooledResult));
			numberOfTrips++;
		}

		List<AlonsoMoraTrip> currentLevelTrips = new LinkedList<>();

		for (AlonsoMoraRequest pairableRequest : requestGraph.getShareableRequests(request)) {
			messageCall();

			if (considerThresholds && tripLimitPerSequenceLength > 0
					&& trips.get(1).size() >= tripLimitPerSequenceLength) {
				break; // Limit has been reached
			}

			if (considerThresholds && tripLimitPerVehicle > 0 && numberOfTrips >= tripLimitPerVehicle) {
				return; // Limit has been reached
			}

			if (requests.contains(pairableRequest)) {
				Optional<AlonsoMoraFunction.Result> pairedResult = function
						.calculateRoute(Arrays.asList(request, pairableRequest), vehicle, now);

				if (pairedResult.isPresent()) {
					AlonsoMoraTrip trip = new AlonsoMoraTrip(vehicle, Arrays.asList(request, pairableRequest),
							pairedResult.get());
					trips.get(1).add(trip);
					currentLevelTrips.add(trip);
					numberOfTrips++;
				}
			}
		}

		if (currentLevelTrips.size() > 0) {
			constructTrips(currentLevelTrips, 2, now, considerThresholds);
		}
	}

	private void constructTrips(List<AlonsoMoraTrip> previousLevelTrips, int level, double now,
			boolean considerThresholds) {
		ensureTripListSize(level + 1);
		Collections.sort(previousLevelTrips);
		List<AlonsoMoraTrip> currentLevelTrips = new ArrayList<>();

		for (int i = 0; i < previousLevelTrips.size(); i++) {
			for (int j = i + 1; j < previousLevelTrips.size(); j++) {
				messageCall();

				if (considerThresholds && tripLimitPerSequenceLength > 0
						&& trips.get(level).size() >= tripLimitPerSequenceLength) {
					break; // Limit has been reached
				}

				if (considerThresholds && tripLimitPerVehicle > 0 && numberOfTrips >= tripLimitPerVehicle) {
					return; // Limit has been reached
				}

				AlonsoMoraTrip firstTrip = previousLevelTrips.get(i);
				AlonsoMoraTrip secondTrip = previousLevelTrips.get(j);

				Set<AlonsoMoraRequest> requests = new HashSet<>();
				requests.addAll(firstTrip.getRequests());
				requests.addAll(secondTrip.getRequests());

				if (requests.size() == firstTrip.getRequests().size() + 1) {
					// One more requests on a trip

					boolean allSubtripsExist = true;
					List<AlonsoMoraRequest> requestList = new ArrayList<>(requests);
					Collections.sort(requestList);

					boolean alreadyExists = false;

					for (int k = 0; k < currentLevelTrips.size() && !alreadyExists; k++) {
						if (checkSame(requestList, currentLevelTrips.get(k).getRequests())) {
							alreadyExists = true;
							break;
						}
					}

					if (!alreadyExists) {
						for (int k = 0; k < requests.size() && allSubtripsExist; k++) {
							List<AlonsoMoraRequest> reducedList = new ArrayList<>(requestList);
							reducedList.remove(k);

							boolean foundSubtrip = false;

							for (AlonsoMoraTrip trip : trips.get(level - 1)) {
								if (checkSame(reducedList, trip.getRequests())) {
									foundSubtrip = true;
									break;
								}
							}

							if (!foundSubtrip) {
								allSubtripsExist = false;
							}
						}

						if (allSubtripsExist) {
							Optional<AlonsoMoraFunction.Result> result = function.calculateRoute(requestList, vehicle,
									now);

							if (result.isPresent()) {
								AlonsoMoraTrip trip = new AlonsoMoraTrip(vehicle, requestList, result.get());
								trips.get(level).add(trip);
								currentLevelTrips.add(trip);
								numberOfTrips++;
							}
						}
					}
				}
			}
		}

		if (currentLevelTrips.size() > 0) {
			constructTrips(currentLevelTrips, level + 1, now, considerThresholds);
		}
	}

	/**
	 * This assumes that the lists are already ordered!
	 */
	private boolean checkSame(List<AlonsoMoraRequest> first, List<AlonsoMoraRequest> second) {
		for (int u = 0; u < first.size(); u++) {
			if (first.get(u) != second.get(u)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public void preserveVehicleAssignment(double now) {
		List<AlonsoMoraRequest> assignedRequests = vehicle.getRoute().stream() //
				.filter(s -> s.getType().equals(StopType.Pickup)) //
				.map(s -> s.getRequest()) //
				.filter(r -> !r.isPickedUp()) //
				.sorted() //
				.collect(Collectors.toList());

		Collections.sort(assignedRequests);

		if (assignedRequests.size() > 0) {
			for (AlonsoMoraTrip candidate : trips.get(assignedRequests.size() - 1)) {
				if (checkSame(candidate.getRequests(), assignedRequests)) {
					return; // Sequence is already included in the optimization set.
				}
			}

			// Sequence does not exist anymore, so add it manually if feasible.
			Optional<AlonsoMoraFunction.Result> result = function.calculateRoute(assignedRequests, vehicle, now);

			if (result.isPresent()) {
				AlonsoMoraTrip trip = new AlonsoMoraTrip(vehicle, assignedRequests, result.get());
				trips.get(assignedRequests.size() - 1).add(trip);
			}
		}
	}

	@Override
	public Stream<AlonsoMoraTrip> stream() {
		return trips.stream().flatMap(list -> list.stream());
	}

	@Override
	public int getSize() {
		return trips.stream().mapToInt(t -> t.size()).sum();
	}
}
