package org.matsim.alonso_mora.examples.vehicle_locations;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VehicleLocationsWriter {
	private final VehicleLocations locations;

	public VehicleLocationsWriter(VehicleLocations locations) {
		this.locations = locations;
	}

	public void write(File path) throws JsonGenerationException, JsonMappingException, IOException {
		new ObjectMapper().writeValue(path, locations);
	}
}
