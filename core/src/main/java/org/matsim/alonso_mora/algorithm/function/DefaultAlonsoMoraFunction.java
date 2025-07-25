package org.matsim.alonso_mora.algorithm.function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop.StopType;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.function.sequence.ExtensiveSequenceGenerator;
import org.matsim.alonso_mora.algorithm.function.sequence.SequenceGenerator;
import org.matsim.alonso_mora.algorithm.function.sequence.SequenceGeneratorFactory;
import org.matsim.alonso_mora.travel_time.TravelTimeEstimator;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;
import org.matsim.contrib.dvrp.load.DvrpLoad;
import org.matsim.contrib.dvrp.load.DvrpLoadType;
import org.matsim.contrib.dvrp.util.LinkTimePair;

import com.google.common.base.Verify;

/**
 * Default implementation of the "travel" function described by Alonso-Mora et
 * al. See the individual methods for further explanations.
 * 
 * @author sebhoerl
 */
public class DefaultAlonsoMoraFunction implements AlonsoMoraFunction {
	private final TravelTimeEstimator travelTimeEstimator;
	private final SequenceGeneratorFactory generatorFactory;

	private final PassengerStopDurationProvider stopDurationProvider;
	private final double vehicleStopDuration;

	private final boolean allowPickupViolations;
	private final boolean allowPickupsWithDropoffViolations;
	private boolean checkDeterminsticTravelTimes;

	private final Objective objective;
	private final Constraint constraint;

	private final double violationFactor;
	private final double violationOffset;
	private final boolean preferNonViolation;

	private final DvrpLoad emptyLoad;

	public DefaultAlonsoMoraFunction(TravelTimeEstimator travelTimeEstimator, SequenceGeneratorFactory generatorFactory,
			PassengerStopDurationProvider stopDurationProvider, double vehicleStopDuration,
			boolean allowPickupViolations, boolean allowPickupsWithDropoffViolations,
			boolean checkDeterminsticTravelTimes, Objective objective, Constraint constraint, double violationFactor,
			double violationOffset, boolean preferNonViolation, DvrpLoadType loadType) {
		this.travelTimeEstimator = travelTimeEstimator;
		this.vehicleStopDuration = vehicleStopDuration;
		this.stopDurationProvider = stopDurationProvider;
		this.generatorFactory = generatorFactory;

		this.allowPickupViolations = allowPickupViolations;
		this.allowPickupsWithDropoffViolations = allowPickupsWithDropoffViolations;
		this.checkDeterminsticTravelTimes = checkDeterminsticTravelTimes;

		this.objective = objective;
		this.constraint = constraint;

		this.violationFactor = violationFactor;
		this.violationOffset = violationOffset;
		this.preferNonViolation = preferNonViolation;

		this.emptyLoad = loadType.getEmptyLoad();
	}

	/**
	 * Checks whether two request could potentially be pooled. This is the case if a
	 * virtual vehicle starting at the current time at either the location of the
	 * first or the second request and a feasible route fulfilling both requests'
	 * pickup and dropoff constraints is found. Technically, all potential orderings
	 * of pickups and dropoffs of the two requests are iterated through and once a
	 * valid solution is found, it is returned. If no solution is returned, the
	 * requests are not pollable.
	 */
	@Override
	public boolean checkShareability(AlonsoMoraRequest firstRequest, AlonsoMoraRequest secondRequest, double now) {
		SequenceGenerator generator = new ExtensiveSequenceGenerator(Collections.emptyList(),
				Arrays.asList(firstRequest, secondRequest));

		Map<AlonsoMoraRequest, Double> requiredPickupTimes = new HashMap<>();
		Map<AlonsoMoraRequest, Double> requiredDropoffTimes = new HashMap<>();

		requiredPickupTimes.put(firstRequest, firstRequest.getPlannedPickupTime());
		requiredPickupTimes.put(secondRequest, secondRequest.getPlannedPickupTime());
		requiredDropoffTimes.put(firstRequest, firstRequest.getLatestDropoffTime());
		requiredDropoffTimes.put(secondRequest, secondRequest.getLatestDropoffTime());

		RouteTracker tracker = new RouteTracker(null, travelTimeEstimator, stopDurationProvider, vehicleStopDuration, emptyLoad,
				now, Optional.empty(), requiredPickupTimes, requiredDropoffTimes);

		while (generator.hasNext()) {
			List<AlonsoMoraStop> stops = generator.get();
			int startIndex = tracker.update(stops);

			boolean isValid = true;

			for (int i = startIndex; i < stops.size() && isValid; i++) {
				AlonsoMoraStop stop = stops.get(i);

				switch (stop.getType()) {
					case Pickup:
						double maximumPickupTime = stop.getRequest().getPlannedPickupTime();
						double calculatedPickupTime = stop.getTime();

						if (calculatedPickupTime > maximumPickupTime) {
							isValid = false;
						}

						break;
					case Dropoff:
						double maximumDropoffTime = stop.getRequest().getLatestDropoffTime();
						double calculatedDropoffTime = stop.getTime();

						if (calculatedDropoffTime > maximumDropoffTime) {
							isValid = false;
						}

						break;
					default:
						throw new IllegalStateException();
				}
			}

			if (isValid && generator.isComplete()) {
				return true;
			}

			if (isValid) {
				generator.advance();
			} else {
				generator.abort();
			}
		}

		return false;
	}

	@Override
	public Optional<Result> calculateRoute(Collection<AlonsoMoraRequest> requests, AlonsoMoraVehicle vehicle,
			double now) {
		Collection<AlonsoMoraRequest> onboardRequests = vehicle.getOnboardRequests();

		if (requests.size() == 0 && onboardRequests.size() == 0) {
			/*
			 * Shortcut: If there are no on-board requests to finish and no assignable
			 * requests, there is nothing to do and the final route will have no stops.
			 */
			return Optional.of(new Result(0.0, Collections.emptyList()));
		}

		boolean onlyDropoff = requests.size() == 0;

		/*
		 * Update the *required* pickup and dropoff times (see Hörl and Zwick, TRB
		 * 2022). By default, the required pickup and dropoff times are those defined by
		 * the constraints. However, we might ignore violations that are caused by
		 * deteriorating traffic conditions. In that case we set the required values to
		 * the currently expected pickup and dropoff time.
		 * 
		 * Note that this assumes that the timing along the stops is updated with
		 * current traffic conditions *before* this function is called! This is done in
		 * AlonsoMoraAlgorithm when initializing the vehicle graphs.
		 */

		Map<AlonsoMoraRequest, Double> requiredPickupTimes = new HashMap<>();
		Map<AlonsoMoraRequest, Double> requiredDropoffTimes = new HashMap<>();

		// Technically, find the maximum value between the current estimate and the
		// constraint value in case we make use of the respective fix.

		for (int i = 0; i < vehicle.getRoute().size(); i++) {
			AlonsoMoraStop stop = vehicle.getRoute().get(i);
			AlonsoMoraRequest request = stop.getRequest();

			if (stop.getType().equals(StopType.Pickup)) {
				if (allowPickupViolations) {
					requiredPickupTimes.put(stop.getRequest(),
							Math.max(stop.getTime(), request.getPlannedPickupTime()));
				}
			} else if (stop.getType().equals(StopType.Dropoff)) {
				if (allowPickupsWithDropoffViolations) {
					requiredDropoffTimes.put(stop.getRequest(),
							Math.max(stop.getTime(), request.getLatestDropoffTime()));
				}
			}
		}

		// For requests which are not assigned to the vehicle, consider the general
		// constraint values of the request.

		for (AlonsoMoraRequest request : requests) {
			requiredPickupTimes.computeIfAbsent(request, r -> r.getPlannedPickupTime());
			requiredDropoffTimes.computeIfAbsent(request, r -> r.getLatestDropoffTime());
		}

		for (AlonsoMoraRequest request : onboardRequests) {
			requiredPickupTimes.computeIfAbsent(request, r -> r.getPlannedPickupTime());
			requiredDropoffTimes.computeIfAbsent(request, r -> r.getLatestDropoffTime());
		}

		// Set up the sequence generator
		LinkTimePair diversion = vehicle.getNextDiversion(now);
		SequenceGenerator generator = generatorFactory.createGenerator(vehicle, onboardRequests, requests, now);

		// Set up the timing and occupancy tracker
		DvrpLoad startLoad = onboardRequests.stream() //
				.map(AlonsoMoraRequest::getDrtRequest) //
				.map(DrtRequest::getLoad) //
				.reduce(emptyLoad, DvrpLoad::add);

		RouteTracker tracker = new RouteTracker(vehicle, travelTimeEstimator, stopDurationProvider, vehicleStopDuration,
				startLoad, diversion.time,
				Optional.of(diversion.link), requiredPickupTimes, requiredDropoffTimes);

		tracker.setDrivingState(vehicle);

		// Set up tracking of best solution
		List<AlonsoMoraStop> bestSolution = null;
		double bestObjective = Double.POSITIVE_INFINITY;
		boolean bestHasViolations = true;

		// Track violations
		LinkedList<Double> violations = new LinkedList<>();

		// Start looping through the proposed sequences
		while (generator.hasNext()) {
			List<AlonsoMoraStop> stops = generator.get();
			int startIndex = tracker.update(stops);

			while (violations.size() > startIndex) {
				violations.removeLast();
			}

			// Assume the sequence is valid in the beginning
			boolean isValid = true;

			// Check constraints for updated part of the sequence
			for (int i = startIndex; i < stops.size() && isValid; i++) {
				AlonsoMoraStop stop = stops.get(i);
				boolean isOnboard = onboardRequests.contains(stop.getRequest());

				/*
				 * First, check pickup and dropoff time constraints.
				 */

				switch (stop.getType()) {
					case Pickup:
						Verify.verify(!isOnboard, "Cannot pick up onboard requests");

						double requiredPickupTime = requiredPickupTimes.get(stop.getRequest());
						double calculatedPickupTime = stop.getTime();

						if (calculatedPickupTime > requiredPickupTime) {
							// Too late for pickup! Can never happen if vehicle is already assigned to the
							// request and pickup violations are allowed.
							isValid = false;
						}

						violations.add(Math.max(0, calculatedPickupTime - stop.getRequest().getPlannedPickupTime())
								* stop.getRequest().getItems());

						break;
					case Dropoff:
						double requiredDropoffTime = requiredDropoffTimes.get(stop.getRequest());
						double calculatedDropoffTime = stop.getTime();

						if (calculatedDropoffTime > requiredDropoffTime) {
							// Too late for dropoff! Can never happen if vehicle is already assigned to the
							// request and dropoff violations are allowed. Furthermore,

							if (!onlyDropoff) {
								// However, constraint can only be enforced when there are pickups. If we have
								// only dropoffs we need to get rid of the passengers in any case.
								isValid = false;
							}
						}

						violations.add(Math.max(0, calculatedDropoffTime - stop.getRequest().getLatestDropoffTime())
								* stop.getRequest().getItems());

						break;
					default:
						throw new IllegalStateException();
				}

				/*
				 * Second, check occupancy constraint.
				 */

				if (!tracker.getOccupancyAfter(i).fitsIn(vehicle.getVehicle().getCapacity())) {
					// Not a valid solution because we exceed vehicle capacity.
					isValid = false;
				}
			}

			// Check additional constraint
			double constraintViolations = 0.0;

			if (isValid) {
				constraintViolations = constraint.checkAssignment(vehicle, onboardRequests, stops, now,
						generator.isComplete(), tracker);
				isValid &= constraintViolations == 0.0 || onlyDropoff;
			}

			// Calculate objective of the partial sequence
			double partialObjective = objective.calculateObjective(vehicle, requests, stops, now, tracker);

			double totalViolations = violations.stream().mapToDouble(d -> d).sum();
			totalViolations += constraintViolations;

			if (totalViolations > 0.0) {
				// Add factor-based penalty to the objective and constant offset
				partialObjective += totalViolations * violationFactor;
				partialObjective += violationOffset;
			}

			boolean hasViolations = totalViolations > 0.0;

			if (partialObjective > bestObjective) {
				boolean initiallyValid = isValid;
				isValid = false; // Per default invalid because worse than the one we know

				if (initiallyValid && preferNonViolation && bestHasViolations && !hasViolations) {
					isValid = true; // Special case: We prefer non-violating solutions even if objective is worse
				}
			}

			if (isValid && !onlyDropoff) {
				double lastDepartureTime = tracker.getDepartureTime(stops.size() - 1);

				if (lastDepartureTime > vehicle.getVehicle().getServiceEndTime()) {
					// We exceed the service time of the vehicle
					isValid = false;
				}
			}

			// Controlling the sequence generator ...

			if (isValid) {
				if (generator.isComplete()) {
					// We found a new solution that is better than the old one (see objective
					// constraint above)
					bestSolution = copySolution(stops);
					bestObjective = partialObjective;
					bestHasViolations = hasViolations;
				}

				// ... accept this sequence and expand.
				generator.advance();
			} else {
				// ... stop exploring this sequence.
				generator.abort();
			}
		}

		if (bestSolution == null) {
			return Optional.empty();
		}

		if (checkDeterminsticTravelTimes) {
			Verify.verify(!bestHasViolations,
					"Checking for determinstic travel times. In that case, no volutions with violations should be found as best.");
		}

		return Optional.of(new Result(bestObjective, bestSolution));
	}

	/**
	 * This copy function makes sure that timings of stops are saved properly.
	 */
	static public List<AlonsoMoraStop> copySolution(List<AlonsoMoraStop> stops) {
		List<AlonsoMoraStop> copy = new ArrayList<>(stops.size());

		for (AlonsoMoraStop stop : stops) {
			copy.add(new AlonsoMoraStop(stop.getType(), stop.getLink(), stop.getRequest(), stop.getTime()));
		}

		return copy;
	}

	/**
	 * Checks whether a relocation trip with a given destination can be assigned to
	 * the vehicle. The major condition here is that the vehicle arrives at the
	 * destination before its service time ends. If the relocation trip does not
	 * violate this constraint, the estimated arrival time is returned.
	 */
	@Override
	public Optional<Double> checkRelocation(AlonsoMoraVehicle vehicle, Link destination, double now) {
		if (!constraint.checkRelocation(vehicle, destination, now)) {
			return Optional.empty();
		}

		LinkTimePair diversion = vehicle.getNextDiversion(now);
		double travelTime = travelTimeEstimator.estimateTravelTime(diversion.link, destination, diversion.time,
				vehicle.getVehicle().getServiceEndTime());
		double arrivalTime = diversion.time + travelTime;

		if (arrivalTime >= vehicle.getVehicle().getServiceEndTime()) {
			return Optional.empty();
		}

		return Optional.of(arrivalTime);
	}

	public interface Objective {
		double calculateObjective(AlonsoMoraVehicle vehicle, Collection<AlonsoMoraRequest> requests,
				List<AlonsoMoraStop> stops, double now, RouteTracker tracker);
	}

	public interface Constraint {
		double checkAssignment(AlonsoMoraVehicle vehicle, Collection<AlonsoMoraRequest> requests,
				List<AlonsoMoraStop> stops, double now, boolean isComplete, RouteTracker tracker);

		boolean checkRelocation(AlonsoMoraVehicle vehicle, Link destination, double now);
	}

	public static final class NoopConstraint implements Constraint {
		@Override
		public double checkAssignment(AlonsoMoraVehicle vehicle, Collection<AlonsoMoraRequest> requests,
				List<AlonsoMoraStop> stops, double now, boolean isComplete, RouteTracker tracker) {
			return 0.0;
		}

		@Override
		public boolean checkRelocation(AlonsoMoraVehicle vehicle, Link destination, double now) {
			return true;
		}
	}

	public static class MinimumDelay implements Objective {
		@Override
		public double calculateObjective(AlonsoMoraVehicle vehicle, Collection<AlonsoMoraRequest> requests,
				List<AlonsoMoraStop> stops, double now, RouteTracker tracker) {
			double objective = 0.0;

			for (AlonsoMoraStop stop : stops) {
				if (stop.getType().equals(StopType.Dropoff)) {
					double calculatedDropoffTime = stop.getTime();
					double directDropoffTime = stop.getRequest().getDirectArivalTime();
					double delay = Math.max(0.0, calculatedDropoffTime - directDropoffTime);
					objective += stop.getRequest().getItems() * delay;
				}
			}

			return objective;
		}
	}
}
