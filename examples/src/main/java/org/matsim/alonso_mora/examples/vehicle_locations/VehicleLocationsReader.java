package org.matsim.alonso_mora.examples.vehicle_locations;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VehicleLocationsReader {
	public VehicleLocations read(File path) throws JsonGenerationException, JsonMappingException, IOException {
		return new ObjectMapper().readValue(path, VehicleLocations.class);
	}
}
