package org.matsim.alonso_mora.example;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

public class CreateMeanNetwork {
	static public void main(String[] args) throws ConfigurationException, InterruptedException {
		CommandLine cmd = new CommandLine.Builder(args) //
				.allowPositionalArguments(true) //
				.requireOptions("output-path") //
				.build();

		Network mainNetwork = null;
		int networkCount = 0;

		for (String networkPath : cmd.getPositionalArguments()) {
			Network currentNetwork = NetworkUtils.createNetwork();
			new MatsimNetworkReader(currentNetwork).readFile(networkPath);

			if (mainNetwork == null) {
				mainNetwork = currentNetwork;
			} else {
				for (Link currentLink : currentNetwork.getLinks().values()) {
					Link mainLink = mainNetwork.getLinks().get(currentLink.getId());
					mainLink.setFreespeed(mainLink.getFreespeed() + currentLink.getFreespeed());
				}
			}

			networkCount++;
		}

		for (Link mainLink : mainNetwork.getLinks().values()) {
			mainLink.setFreespeed(mainLink.getFreespeed() / networkCount);
		}

		new NetworkWriter(mainNetwork).write(cmd.getOptionStrict("output-path"));
	}
}
