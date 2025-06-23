package org.matsim.alonso_mora.algorithm;

import java.util.Comparator;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.passenger.AcceptedDrtRequest;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.schedule.DrtStopTask;
import org.matsim.contrib.dvrp.load.DvrpLoad;
import org.matsim.contrib.dvrp.load.DvrpLoadType;
import org.matsim.contrib.dvrp.load.IntegerLoad;
import org.matsim.contrib.dvrp.load.IntegerLoadType;
import org.matsim.contrib.dvrp.load.IntegersLoad;
import org.matsim.contrib.dvrp.load.IntegersLoadType;
import org.matsim.contrib.dvrp.schedule.Task.TaskStatus;

import com.google.common.base.Preconditions;
import com.google.common.base.Verify;

/**
 * This class represents a request in the context of the dispatching algorithm
 * of Alonso-Mora et al. It has multiple purposes. (1) It holds static
 * information such as the origin and destination of a request, as well as time
 * constraints. (2) It represents already an aggregated constraint in case where
 * multiple requests have the same origin and destination. (3) It holds
 * information to which vehicle a request has been assigned. This includes the
 * DVRP tasks for pickup and dropoff.
 * 
 * @author sebhoerl
 */
public class DefaultAlonsoMoraRequest implements AlonsoMoraRequest {
	private final DrtRequest drtRequest;
	private AcceptedDrtRequest acceptedDrtRequest;

	private DrtStopTask pickupTask;
	private DrtStopTask dropoffTask;

	private double latestAssignmentTime;

	private AlonsoMoraVehicle vehicle;

	private double plannedPickupTime = Double.NaN;
	private final double directArrivalTime;

	private final int cachedHashCode;

	private final Link pickupLink;
	private final Link dropoffLink;
	private final double latestPickupTime;
	private final double latestDropoffTime;
	private final double earliestPickupTime;
	private final int items;

	private final double directRideDistance;

	public DefaultAlonsoMoraRequest(DrtRequest drtRequest, double latestAssignmentTime, double directArrivalTime,
			double directRideDistance, DvrpLoadType loadType, int items) {
		this.directArrivalTime = directArrivalTime;
		this.directRideDistance = directRideDistance;
		this.drtRequest = drtRequest;
		this.cachedHashCode = drtRequest.getId().index();
		this.items = items;

		this.pickupLink = this.drtRequest.getFromLink();
		this.dropoffLink = this.drtRequest.getToLink();

		this.latestPickupTime = this.drtRequest.getLatestStartTime();
		this.latestDropoffTime = this.drtRequest.getLatestArrivalTime();
		this.earliestPickupTime = this.drtRequest.getEarliestStartTime();

		Verify.verify(this.pickupLink.equals(drtRequest.getFromLink()));
		Verify.verify(this.dropoffLink.equals(drtRequest.getToLink()));

		this.latestAssignmentTime = Math.min(getLatestPickupTime(), latestAssignmentTime);

		// TODO: Remove this once DvrpLoad implements Comparable
		this.loadComparator = new LoadComparator(loadType.size());
	}

	private class LoadComparator implements Comparator<DvrpLoad> {
		private int size;

		LoadComparator(int size) {
			this.size = size;
		}

		@Override
		public int compare(DvrpLoad a, DvrpLoad b) {
			int result = 0;

			for (int k = 0; k < size; k++) {
				result = Integer.compare((int) a.getElement(k), (int) b.getElement(k));
				if (result != 0) break;
			}

			return result;
		}
	}

	// TODO: Start remove once DvrpLoad implements Comparable

	private final Comparator<DvrpLoad> loadComparator;

	@Override
	public int compareTo(AlonsoMoraRequest otherRequest) {
		if (otherRequest instanceof DefaultAlonsoMoraRequest) {
			int loadComparison = loadComparator.compare(
				drtRequest.getLoad(), otherRequest.getDrtRequest().getLoad());

			if (loadComparison != 0) {
				return loadComparison;
			}

			return Integer.compare(drtRequest.getId().index(), otherRequest.getDrtRequest().getId().index());
		}

		throw new IllegalStateException();
	}

	// TODO: End remove once DvrpLoad implements Comparable

	@Override
	public boolean equals(Object otherObject) {
		if (otherObject instanceof DefaultAlonsoMoraRequest) {
			DefaultAlonsoMoraRequest otherRequest = (DefaultAlonsoMoraRequest) otherObject;
			return compareTo(otherRequest) == 0;
		}

		throw new IllegalStateException("Cannot compare against unknown object");
	}

	@Override
	public int hashCode() {
		return cachedHashCode;
	}

	public void setPickupTask(AlonsoMoraVehicle vehicle, DrtStopTask pickupTask) {
		Verify.verify(!isPickedUp());
		Verify.verify(!isDroppedOff());

		Verify.verifyNotNull(pickupTask);
		Verify.verifyNotNull(vehicle);

		this.pickupTask = pickupTask;
		this.vehicle = vehicle;
	}

	public void setDropoffTask(AlonsoMoraVehicle vehicle, DrtStopTask dropoffTask) {
		Verify.verify(!isDroppedOff());
		Verify.verify(vehicle == this.vehicle);

		Verify.verifyNotNull(dropoffTask);
		this.dropoffTask = dropoffTask;
	}

	public void unassign() {
		Verify.verify(!isPickedUp());

		this.pickupTask = null;
		this.dropoffTask = null;
		this.vehicle = null;
	}

	public boolean isPickedUp() {
		return pickupTask != null && !pickupTask.getStatus().equals(TaskStatus.PLANNED);
	}

	public boolean isDroppedOff() {
		return dropoffTask != null && !dropoffTask.getStatus().equals(TaskStatus.PLANNED);
	}

	public double getLatestAssignmentTime() {
		return latestAssignmentTime;
	}

	public DrtRequest getDrtRequest() {
		return drtRequest;
	}

	public AcceptedDrtRequest getAcceptedDrtRequest() {
		return acceptedDrtRequest;
	}

	@Override
	public void setVehicle(AlonsoMoraVehicle vehicle) {
		this.vehicle = vehicle;
	}

	@Override
	public AlonsoMoraVehicle getVehicle() {
		return vehicle;
	}

	@Override
	public DrtStopTask getPickupTask() {
		return pickupTask;
	}

	@Override
	public DrtStopTask getDropoffTask() {
		return dropoffTask;
	}

	@Override
	public boolean isAssigned() {
		return this.vehicle != null;
	}

	@Override
	public int getItems() {
		return items;
	}

	@Override
	public Link getPickupLink() {
		return pickupLink;
	}

	@Override
	public Link getDropoffLink() {
		return dropoffLink;
	}

	@Override
	public double getLatestPickupTime() {
		return latestPickupTime;
	}

	@Override
	public double getLatestDropoffTime() {
		return latestDropoffTime;
	}

	@Override
	public double getDirectArivalTime() {
		return directArrivalTime;
	}

	@Override
	public double getPlannedPickupTime() {
		if (Double.isNaN(plannedPickupTime)) {
			return latestPickupTime;
		} else {
			return plannedPickupTime;
		}
	}

	@Override
	public double getDirectRideDistance() {
		return directRideDistance;
	}

	@Override
	public double getEarliestPickupTime() {
		return earliestPickupTime;
	}

	@Override
	public String toString() {
		return drtRequest.toString();
	}

	@Override
	public void accept(AcceptedDrtRequest acceptedRequest) {
		Preconditions.checkArgument(this.acceptedDrtRequest == null);
		this.acceptedDrtRequest = acceptedRequest;
		this.plannedPickupTime = acceptedRequest.getLatestStartTime();
	}

	@Override
	public void setPlannedPickupTime(double plannedPickupTime) {
		throw new IllegalStateException();
	}
}
