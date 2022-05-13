package org.matsim.alonso_mora.examples.vehicle_locations;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VehicleLocation {
	@JsonProperty("vehicleId")
	public String vehicleId;

	@JsonProperty("linkId")
	public String linkId;
}
