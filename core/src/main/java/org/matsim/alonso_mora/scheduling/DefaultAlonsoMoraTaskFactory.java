package org.matsim.alonso_mora.scheduling;

import org.matsim.api.core.v01.network.Link;

/**
 * @author mga, nkuehnel / MOIA
 */
public class DefaultAlonsoMoraTaskFactory implements AlonsoMoraTaskFactory {

    @Override
    public WaitForStopTask createWaitForStopTask(double beginTime, double endTime, Link link) {
        return new WaitForStopTask(beginTime, endTime, link);
    }
}
