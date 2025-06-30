package org.matsim.alonso_mora.preemptive;

import org.matsim.alonso_mora.AlonsoMoraOptimizer;
import org.matsim.contrib.drt.extension.preemptive_rejection.PreemptiveRejectionModeQSimModule;
import org.matsim.core.controler.Controler;

public class PreemptiveRejectionAdapter {
    static public void configure(Controler controller, String mode) {
        controller.addOverridingQSimModule(new PreemptiveRejectionModeQSimModule(mode, AlonsoMoraOptimizer.class));
    }
}
