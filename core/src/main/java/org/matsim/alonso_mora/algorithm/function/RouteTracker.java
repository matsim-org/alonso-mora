package org.matsim.alonso_mora.algorithm.function;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop.StopType;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.travel_time.TravelTimeEstimator;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.schedule.DrtDriveTask;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.load.DvrpLoad;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.schedule.DriveTask;

/**
 * This class is a helpful utility to track occupancy and tiing of stop
 * sequences. See {{@link #update(List)}.
 * 
 * @author sebhoerl
 */
public class RouteTracker {
	private final TravelTimeEstimator estimator;
	private final AlonsoMoraVehicle vehicle;

	private final PassengerStopDurationProvider stopDurationProvider;
	private final double vehicleStopDuration;

	private final DvrpLoad initialOccupancy;
	private final double initialDepartureTime;
	private final Optional<Link> initialLink;

	private final LinkedList<Double> departureTimes = new LinkedList<>();
	private final LinkedList<Double> arrivalTimes = new LinkedList<>();
	private final LinkedList<DvrpLoad> occupancies = new LinkedList<>();

	private final Map<AlonsoMoraRequest, Double> requiredPickupTimes;
	private final Map<AlonsoMoraRequest, Double> requiredDropoffTimes;

	public RouteTracker(AlonsoMoraVehicle vehicle, TravelTimeEstimator estimator,
			PassengerStopDurationProvider stopDurationProvider, double vehicleStopDuration, DvrpLoad initialOccupancy,
			double initialDepartureTime, Optional<Link> initialLink) {
		this.estimator = estimator;
		this.stopDurationProvider = stopDurationProvider;
		this.initialDepartureTime = initialDepartureTime;
		this.initialOccupancy = initialOccupancy;
		this.initialLink = initialLink;
		this.vehicleStopDuration = vehicleStopDuration;
		this.vehicle = vehicle;

		this.requiredPickupTimes = Collections.emptyMap();
		this.requiredDropoffTimes = Collections.emptyMap();
	}

	public RouteTracker(AlonsoMoraVehicle vehicle, TravelTimeEstimator estimator,
			PassengerStopDurationProvider stopDurationProvider, double vehicleStopDuration, DvrpLoad initialOccupancy,
			double initialDepartureTime, Optional<Link> initialLink, Map<AlonsoMoraRequest, Double> requiredPickupTimes,
			Map<AlonsoMoraRequest, Double> requiredDropoffTimes) {
		this.estimator = estimator;
		this.stopDurationProvider = stopDurationProvider;
		this.initialDepartureTime = initialDepartureTime;
		this.initialOccupancy = initialOccupancy;
		this.initialLink = initialLink;
		this.vehicleStopDuration = vehicleStopDuration;
		this.vehicle = vehicle;

		this.requiredPickupTimes = requiredPickupTimes;
		this.requiredDropoffTimes = requiredDropoffTimes;
	}

	private boolean isCurrentlyDriving = false;
	private boolean needsDrivingModeSwitch = false;

	/**
	 * Sets driving information for the vehicle. If the vehicle is driving it means
	 * that it can be diverted instead of departing from a stop. This has
	 * implications on the exact calculation of travel times in free-flow
	 * conditions. Likewise, if a vehicle is currently driving for relocation, the
	 * vehicle needs to be stopped to switch to a normal driving task. This also has
	 * implications on travel time.
	 */
	public void setDrivingState(AlonsoMoraVehicle vehicle) {
		isCurrentlyDriving = vehicle.getVehicle().getSchedule().getCurrentTask() instanceof DriveTask;

		if (isCurrentlyDriving) {
			DriveTask driveTask = (DriveTask) vehicle.getVehicle().getSchedule().getCurrentTask();
			needsDrivingModeSwitch = !driveTask.getTaskType().equals(DrtDriveTask.TYPE);
		}
	}

	/**
	 * This method proposes an updated stop sequence to the class. First, the method
	 * will find the initial part of the route that matches with the route that has
	 * previously proposed to the RouteTracker. After, the class will reconstruct
	 * the timing of pickup and dropoff stops, as well the occupancy. Occupancy can
	 * be obtained from this class at the end of the stop sequence and times are
	 * written directly into the stops along the sequence.
	 */
	public int update(List<AlonsoMoraStop> stops) {
		while (departureTimes.size() > stops.size() - 1) {
			departureTimes.removeLast();
			arrivalTimes.removeLast();
			occupancies.removeLast();
		}

		int partialIndex = departureTimes.size();

		for (int i = partialIndex; i < stops.size(); i++) {
			Link fromLink = null;
			double departureTime = Double.NaN;
			DvrpLoad occupancy = null;

			if (i == 0) {
				fromLink = initialLink.orElseGet(stops.get(0)::getLink);
				departureTime = initialDepartureTime;
				occupancy = initialOccupancy;
			} else {
				fromLink = stops.get(i - 1).getLink();
				departureTime = departureTimes.get(i - 1);
				occupancy = occupancies.get(i - 1);
			}

			Link toLink = stops.get(i).getLink();

			/*
			 * sehoerl, January 2014: Do we need this additional condition here for i == 0?
			 * Need to modify the else block to make it possible that persons are inserted
			 * into ongoing stop tasks, like it is done in DRT.
			 */
			if (fromLink != toLink || i == 0) {
				double arrivalTimeThreshold = Double.POSITIVE_INFINITY;

				if (stops.get(i).getType().equals(StopType.Pickup)) {
					arrivalTimeThreshold = requiredPickupTimes.getOrDefault(stops.get(i).getRequest(),
							Double.POSITIVE_INFINITY);
				} else {
					arrivalTimeThreshold = requiredDropoffTimes.getOrDefault(stops.get(i).getRequest(),
							Double.POSITIVE_INFINITY);
				}

				double arrivalTime = estimator.estimateTravelTime(fromLink, toLink, departureTime, arrivalTimeThreshold)
						+ departureTime;

				if (i == 0) {
					// If this is the first drive, we may need to modify the arrival time to reflect
					// how the service will be scheduled and simulated (i.e. delay for entering the
					// first link when departing from a task versus only diverting a current drive
					// task).
					arrivalTime = correctArrivalTime(arrivalTime, fromLink != toLink);
				}

				AlonsoMoraStop stop = stops.get(i);

				final double stopArrivalTime;
				if (stop.getType().equals(StopType.Pickup)) {
					// The following is only relevant for pre-booked requests
					// If we are too early, we add a waiting time to the vehicle until the earliest
					// pickup time

					stopArrivalTime = Math.max(arrivalTime, stop.getRequest().getEarliestPickupTime());
				} else {
					stopArrivalTime = arrivalTime;
				}

				final double vehicleDepartureTime = stopArrivalTime + vehicleStopDuration;

				final double stopDepartureTime;
				if (stop.getType().equals(StopType.Pickup)) {
					double passengerPickupTime = stopArrivalTime + stopDurationProvider
							.calcPickupDuration(dvrpVehicle(vehicle), stop.getRequest().getDrtRequest());
					stop.setTime(passengerPickupTime);

					stopDepartureTime = Math.max(passengerPickupTime, vehicleDepartureTime);
				} else if (stop.getType().equals(StopType.Dropoff)) {
					double passengerDropoffTime = stopArrivalTime + stopDurationProvider
							.calcDropoffDuration(dvrpVehicle(vehicle), stop.getRequest().getDrtRequest());
					stop.setTime(passengerDropoffTime);

					stopDepartureTime = Math.max(passengerDropoffTime, vehicleDepartureTime);
				} else if (stop.getType().equals(StopType.Relocation)) {
					stopDepartureTime = stopArrivalTime; // relocation
				} else {
					throw new IllegalStateException();
				}

				arrivalTimes.add(stopArrivalTime);
				departureTimes.add(stopDepartureTime);
			} else {
				// We don't move.

				final double stopArrivalTime = arrivalTimes.get(i - 1);
				AlonsoMoraStop stop = stops.get(i);

				final double vehicleDepartureTime = stopArrivalTime + vehicleStopDuration;
				double stopDepartureTime = Math.max(departureTimes.get(i - 1), vehicleDepartureTime);
				
				if (stop.getType().equals(StopType.Pickup)) {
					double passengerDepartureTime = Math.max(stopArrivalTime,
							stop.getRequest().getEarliestPickupTime());
					double passengerPickupTime = passengerDepartureTime + stopDurationProvider
							.calcPickupDuration(dvrpVehicle(vehicle), stop.getRequest().getDrtRequest());
					stop.setTime(passengerPickupTime);

					stopDepartureTime = Math.max(passengerPickupTime, stopDepartureTime);
				} else if (stop.getType().equals(StopType.Dropoff)) {
					double passengerDropoffTime = stopArrivalTime + stopDurationProvider
							.calcDropoffDuration(dvrpVehicle(vehicle), stop.getRequest().getDrtRequest());
					stop.setTime(passengerDropoffTime);

					stopDepartureTime = Math.max(passengerDropoffTime, stopDepartureTime);
				} else if (stop.getType().equals(StopType.Relocation)) {
					stopDepartureTime = stopArrivalTime; // relocation
				} else {
					throw new IllegalStateException();
				}

				arrivalTimes.add(stopArrivalTime);
				departureTimes.add(stopDepartureTime);
			}

			if (stops.get(i).getType().equals(StopType.Relocation)) {
				occupancies.add(occupancy);
			} else if (stops.get(i).getType().equals(StopType.Pickup)) {
				occupancies.add(occupancy.add(stops.get(i).getRequest().getDrtRequest().getLoad()));
			} else {
				occupancies.add(occupancy.subtract(stops.get(i).getRequest().getDrtRequest().getLoad()));
			}
		}

		return partialIndex;
	}

	private final static double DRIVE_TASK_SWITCH_OFFSET = 1.0; // One second to stop and depart again

	private double correctArrivalTime(double arrivalTime, boolean needsMoving) {
		if (isCurrentlyDriving) {
			if (needsMoving) {
				// Vehicle is driving, but we are not on the final link and potentially going a
				// different direction. Hence, the vehicle needs to be diverted or the route
				// needs to be updated. Since the travel time estiator assumes departure from a
				// stop, we can remove the offset for entering the first link here.
				arrivalTime -= VrpPaths.FIRST_LINK_TT;

				if (needsDrivingModeSwitch) {
					// However, the vehicle is currently relocating or on some other type of drive
					// task (not a standard one that goes to a pickup or dropoff). This means the
					// vehicle needs to stop briefly and switch the drive mode. This costs a
					// specific delay.

					arrivalTime += DRIVE_TASK_SWITCH_OFFSET;
				}
			} else {
				// Vehicle is currently driving, but we have already arrived at the correct
				// link. We will not pass the last node, hence, we can remove it here (as our
				// arrival/departure time is based on the diversion point).
				arrivalTime -= VrpPaths.NODE_TRANSITION_TIME;
			}
		}

		return arrivalTime;
	}

	private DvrpVehicle dvrpVehicle(AlonsoMoraVehicle vehicle) {
		return vehicle == null ? null : vehicle.getVehicle();
	}

	public double getDepartureTime(int index) {
		return departureTimes.get(index);
	}

	public double getArrivalTime(int index) {
		return arrivalTimes.get(index);
	}

	public DvrpLoad getOccupancyAfter(int index) {
		return occupancies.get(index);
	}

	public DvrpLoad getOccupancyBefore(int index) {
		if (index == 0) {
			return initialOccupancy;
		} else {
			return occupancies.get(index - 1);
		}
	}
}
