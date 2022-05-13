package org.matsim.alonso_mora.examples.analysis;

import java.util.Collection;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;

import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * This module adds the new analysis functionality to MATSim.
 * 
 * @author Sebastian Hörl, IRT SystemX
 */
public class AlonsoMoraAnalysisModule extends AbstractModule {
	@Override
	public void install() {
		addControlerListenerBinding().to(AlonsoMoraAnalysisListener.class);
	}

	@Provides
	@Singleton
	public AlonsoMoraAnalysisListener provideAnalysisListener(EventsManager eventsManager,
			OutputDirectoryHierarchy outputHierarchy, MultiModeDrtConfigGroup drtConfig, Network network) {
		Collection<String> modes = drtConfig.getModalElements().stream().map(e -> e.getMode())
				.collect(Collectors.toSet());
		return new AlonsoMoraAnalysisListener(modes, eventsManager, outputHierarchy, network);
	}
}
