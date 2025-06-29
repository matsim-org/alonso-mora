package org.matsim.alonso_mora.algorithm;

import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.dvrp.load.DvrpLoadType;

/**
 * Default implementation for creating a request for the algorithm by
 * Alonso-Mora et al. from a {#link DrtRequest}. It covers setting the maximum
 * allowable queue time for this request.
 * 
 * @author sebhoerl
 */
public class DefaultAlonsoMoraRequestFactory implements AlonsoMoraRequestFactory {
	private final double maximumQueueTime;
	private final DvrpLoadType loadType;
	private final ItemsProvider itemsProvider;

	public DefaultAlonsoMoraRequestFactory(double maximumQueueTime, DvrpLoadType loadType, ItemsProvider itemsProvider) {
		this.maximumQueueTime = maximumQueueTime;
		this.loadType = loadType;
		this.itemsProvider = itemsProvider;
	}

	@Override
	public AlonsoMoraRequest createRequest(DrtRequest request, double directArrvialTime, double earliestDepartureTime,
			double directRideDistance) {
		double latestAssignmentTime = earliestDepartureTime + maximumQueueTime;
		double latestPickupTime = request.getLatestStartTime();
		latestAssignmentTime = Math.min(latestAssignmentTime, latestPickupTime);
		int items = itemsProvider.getItems(request.getLoad());

		return new DefaultAlonsoMoraRequest(request, latestAssignmentTime, directArrvialTime, directRideDistance, loadType, items);
	}
}
