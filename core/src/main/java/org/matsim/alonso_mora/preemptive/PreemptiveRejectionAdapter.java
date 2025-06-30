package org.matsim.alonso_mora.preemptive;

import java.io.IOException;
import java.net.URL;

import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.extension.DrtWithExtensionsConfigGroup;
import org.matsim.contrib.drt.extension.preemptive_rejection.PreemptiveRejectionOptimizer;
import org.matsim.contrib.drt.extension.preemptive_rejection.PreemptiveRejectionParams;
import org.matsim.contrib.drt.optimizer.DefaultDrtOptimizer;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.Controler;

public class PreemptiveRejectionAdapter extends AbstractDvrpModeQSimModule {
    PreemptiveRejectionAdapter(String mode) {
        super(mode);
    }

    @Override
    protected void configureQSim() {
        for (DrtConfigGroup generalConfig : MultiModeDrtConfigGroup.get(getConfig()).getModalElements()) {
            if (generalConfig.getMode().equals(getMode())) {
                DrtWithExtensionsConfigGroup extensionConfig = (DrtWithExtensionsConfigGroup) generalConfig;
                PreemptiveRejectionParams params = extensionConfig.getPreemptiveRejectionParams().get();
                URL source = ConfigGroup.getInputFileURL(getConfig().getContext(), params.getInputPath());

                bindModal(PreemptiveRejectionOptimizer.class).toProvider(modalProvider(getter -> {
                    DrtOptimizer delegate = getter.getModal(DefaultDrtOptimizer.class);

                    EventsManager eventsManager = getter.get(EventsManager.class);
                    Population population = getter.get(Population.class);

                    try {
                        return new PreemptiveRejectionOptimizer(getMode(), delegate, eventsManager, population, source);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));

                addModalComponent(DrtOptimizer.class,
                        modalKey(PreemptiveRejectionOptimizer.class));
            }
        }
    }

    static public void configure(Controler controller, String mode) {
        controller.addOverridingQSimModule(new PreemptiveRejectionAdapter(mode));
    }
}
