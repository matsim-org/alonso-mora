package org.matsim.alonso_mora.examples;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;

public class ConvertOSM {
	static public void main(String[] args) throws ConfigurationException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("input-path", "output-path", "crs") //
				.build();

		String inputPath = cmd.getOptionStrict("input-path");
		String outputPath = cmd.getOptionStrict("output-path");
		String crs = cmd.getOptionStrict("crs");

		Network network = new SupersonicOsmNetworkReader.Builder() //
				.setCoordinateTransformation(new GeotoolsTransformation("EPSG:4326", crs)) //
				.build() //
				.read(inputPath);

		new NetworkCleaner().run(network);

		new NetworkWriter(network).write(outputPath);
	}
}
