package org.matsim.alonso_mora.examples.vehicle_locations;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetSpecification;

public class VehicleLocationListener implements ActivityStartEventHandler {
	private final IdMap<Person, Id<Link>> locations = new IdMap<>(Person.class);
	private final IdSet<Person> relevantIds = new IdSet<>(Person.class);

	public VehicleLocationListener(FleetSpecification fleetSpecification) {
		for (DvrpVehicleSpecification vehicle : fleetSpecification.getVehicleSpecifications().values()) {
			relevantIds.add(Id.createPersonId(vehicle.getId()));
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (relevantIds.contains(event.getPersonId())) {
			locations.put(event.getPersonId(), event.getLinkId());
		}
	}

	public VehicleLocations getVehicleLocations() {
		VehicleLocations result = new VehicleLocations();

		for (Map.Entry<Id<Person>, Id<Link>> entry : locations.entrySet()) {
			VehicleLocation location = new VehicleLocation();
			location.vehicleId = entry.getKey().toString();
			location.linkId = entry.getValue().toString();
			result.locations.add(location);
		}

		return result;
	}
}
