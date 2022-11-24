package org.matsim.alonso_mora.scheduling;

import org.matsim.api.core.v01.network.Link;

/**
 * @author mga, nkuehnel / MOIA
 */
public interface AlonsoMoraTaskFactory {


    WaitForStopTask createWaitForStopTask(double beginTime, double endTime, Link link);

}
