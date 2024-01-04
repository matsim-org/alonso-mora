package org.matsim.alonso_mora;

import java.util.Optional;

import org.matsim.contrib.drt.passenger.AcceptedDrtRequest;
import org.matsim.contrib.drt.passenger.DrtOfferAcceptor;
import org.matsim.contrib.drt.passenger.DrtRequest;

public class AlonsoMoraOfferAcceptor implements DrtOfferAcceptor {
	@Override
	public Optional<AcceptedDrtRequest> acceptDrtOffer(DrtRequest request, double departureTime, double arrivalTime) {
		return Optional.of(AcceptedDrtRequest.newBuilder() //
				.request(request) //
				.earliestStartTime(request.getEarliestStartTime()) //
				.latestArrivalTime(request.getLatestArrivalTime()) //
				.latestStartTime(departureTime) //
				.build());
	}
}
