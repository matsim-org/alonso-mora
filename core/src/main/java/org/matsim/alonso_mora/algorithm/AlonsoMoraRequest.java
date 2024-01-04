package org.matsim.alonso_mora.algorithm;

import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.passenger.AcceptedDrtRequest;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.schedule.DrtStopTask;

/**
 * Represents an aggregated requests for the algorithm by Alonso-Mora et al. See
 * {@link DefaultAlonsoMoraRequest} for more information.
 * 
 * @author sebhoerl
 */
public interface AlonsoMoraRequest extends Comparable<AlonsoMoraRequest> {
	void setVehicle(AlonsoMoraVehicle vehicle);

	AlonsoMoraVehicle getVehicle();

	void setPickupTask(AlonsoMoraVehicle vehicle, DrtStopTask pickupTask);

	void setDropoffTask(AlonsoMoraVehicle vehicle, DrtStopTask dropoffTask);

	DrtStopTask getPickupTask();

	DrtStopTask getDropoffTask();

	void unassign();

	boolean isPickedUp();

	boolean isDroppedOff();

	double getLatestAssignmentTime();

	DrtRequest getDrtRequest();
	
	AcceptedDrtRequest getAcceptedDrtRequest();

	public int getSize();

	boolean isAssigned();

	public Link getPickupLink();

	public Link getDropoffLink();

	public double getLatestPickupTime();

	public double getLatestDropoffTime();

	public double getDirectArivalTime();

	public double getPlannedPickupTime();

	public void setPlannedPickupTime(double plannedPickupTime);

	public double getDirectRideDistance();

	public double getEarliestPickupTime();
}
