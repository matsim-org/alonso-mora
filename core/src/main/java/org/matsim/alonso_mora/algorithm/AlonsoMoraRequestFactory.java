package org.matsim.alonso_mora.algorithm;

import org.matsim.contrib.drt.passenger.DrtRequest;

/**
 * Creates an aggregated request for the algorithm based on a list of
 * aggregateable individual DRT requests.
 * 
 * @author sebhoerl
 */
public interface AlonsoMoraRequestFactory {
	AlonsoMoraRequest createRequest(DrtRequest request, double directArrvialTime, double earliestDepartureTime,
			double directRideDistance);
}
