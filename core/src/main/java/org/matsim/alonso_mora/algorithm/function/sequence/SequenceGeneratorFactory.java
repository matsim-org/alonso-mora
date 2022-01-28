package org.matsim.alonso_mora.algorithm.function.sequence;

import java.util.Collection;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;

/**
 * Creates a {#link SequenceGenerator}.
 * 
 * @author sebhoerl
 */
public interface SequenceGeneratorFactory {
	SequenceGenerator createGenerator(AlonsoMoraVehicle vehicle, Collection<AlonsoMoraRequest> onboardRequests,
			Collection<AlonsoMoraRequest> requests, double now);
}
