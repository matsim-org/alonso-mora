package org.matsim.alonso_mora.examples.vehicle_locations;

import java.io.File;
import java.io.IOException;

import org.matsim.contrib.dvrp.fleet.FleetReader;
import org.matsim.contrib.dvrp.fleet.FleetSpecification;
import org.matsim.contrib.dvrp.fleet.FleetSpecificationImpl;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class RunExtractVehicleLocations {
	static public void main(String[] args)
			throws ConfigurationException, JsonGenerationException, JsonMappingException, IOException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("fleet-path", "events-path", "output-path") //
				.build();

		File eventsPath = new File(cmd.getOptionStrict("events-path"));
		File outputPath = new File(cmd.getOptionStrict("output-path"));
		File fleetPath = new File(cmd.getOptionStrict("fleet-path"));

		FleetSpecification fleetSpecification = new FleetSpecificationImpl();
		new FleetReader(fleetSpecification).readFile(fleetPath.toString());

		EventsManager eventsManager = EventsUtils.createEventsManager();

		VehicleLocationListener listener = new VehicleLocationListener(fleetSpecification);
		eventsManager.addHandler(listener);

		eventsManager.initProcessing();
		new MatsimEventsReader(eventsManager).readFile(eventsPath.toString());
		eventsManager.finishProcessing();

		new VehicleLocationsWriter(listener.getVehicleLocations()).write(outputPath);
	}
}
