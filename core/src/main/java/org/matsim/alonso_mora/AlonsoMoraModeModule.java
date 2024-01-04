package org.matsim.alonso_mora;

import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;

/**
 * Registers all components for the Alonso-Mora dispatcher in the MATSim
 * controller scope.
 */
public class AlonsoMoraModeModule extends AbstractDvrpModeModule {
	public AlonsoMoraModeModule(DrtConfigGroup drtConfig) {
		super(drtConfig.getMode());
	}

	@Override
	public void install() {
		bindModal(InformationCollector.class).toProvider(modalProvider(getter -> {
			return new InformationCollector();
		})).asEagerSingleton();

		bindModal(AnalysisListener.class).toProvider(modalProvider(getter -> {
			return new AnalysisListener( //
					getter.getModal(InformationCollector.class), //
					getter.get(OutputDirectoryHierarchy.class) //
			);
		})).asEagerSingleton();

		addControlerListenerBinding().to(modalKey(AnalysisListener.class));
	}
}
