package org.matsim.alonso_mora.examples.vehicle_locations;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VehicleLocations {
	@JsonProperty
	public List<VehicleLocation> locations = new LinkedList<>();
}
