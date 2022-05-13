package org.matsim.alonso_mora.examples.analysis;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestSubmittedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestSubmittedEventHandler;

/**
 * This class collects information about the detailed timing of passenger
 * requests.
 * 
 * @author Sebastian Hörl, IRT SystemX
 */
class RequestAnalysisHandler implements PassengerRequestSubmittedEventHandler, PassengerRequestScheduledEventHandler,
		PassengerRequestRejectedEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {
	private final String mode;
	private final Network network;

	private final Map<Id<Request>, RequestItem> requests = new LinkedHashMap<>();
	private final Map<Id<Person>, RequestItem> waitingPersons = new HashMap<>();
	private final Map<Id<Person>, RequestItem> onboardPersons = new HashMap<>();

	public RequestAnalysisHandler(String mode, Network network) {
		this.mode = mode;
		this.network = network;
	}

	public List<RequestItem> consolidate() {
		return requests.values().stream().collect(Collectors.toList());
	}

	@Override
	public void handleEvent(PassengerRequestSubmittedEvent event) {
		if (event.getMode().equals(mode)) {
			RequestItem item = new RequestItem();
			item.requestTime = event.getTime();

			item.pickupX = network.getLinks().get(event.getFromLinkId()).getCoord().getX();
			item.pickupY = network.getLinks().get(event.getFromLinkId()).getCoord().getY();
			item.dropoffX = network.getLinks().get(event.getToLinkId()).getCoord().getX();
			item.dropoffY = network.getLinks().get(event.getToLinkId()).getCoord().getY();

			item.requestId = event.getRequestId();
			item.personId = event.getPersonId();

			requests.put(event.getRequestId(), item);
			waitingPersons.put(event.getPersonId(), item);

			if (event instanceof DrtRequestSubmittedEvent) {
				DrtRequestSubmittedEvent drtEvent = (DrtRequestSubmittedEvent) event;
				item.latestPickupTime = drtEvent.getLatestPickupTime();
				item.latestDropoffTime = drtEvent.getLatestDropoffTime();
			}
		}
	}

	@Override
	public void handleEvent(PassengerRequestRejectedEvent event) {
		if (event.getMode().equals(mode)) {
			requests.get(event.getRequestId()).rejectionTime = event.getTime();
			waitingPersons.remove(event.getPersonId());
		}
	}

	@Override
	public void handleEvent(PassengerRequestScheduledEvent event) {
		if (event.getMode().equals(mode)) {
			RequestItem request = requests.get(event.getRequestId());

			if (Double.isInfinite(request.firstAssignmentTime)) {
				request.firstAssignmentTime = event.getTime();
				request.firstExpectedPickupTime = event.getPickupTime();
				request.firstExpectedDropoffTime = event.getDropoffTime();
			}

			request.lastAssignmentTime = event.getTime();
			request.lastExpectedPickupTime = event.getPickupTime();
			request.lastExpectedDropoffTime = event.getDropoffTime();
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (waitingPersons.containsKey(event.getPersonId())) {
			RequestItem item = waitingPersons.remove(event.getPersonId());
			item.pickupTime = event.getTime();

			onboardPersons.put(event.getPersonId(), item);
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (onboardPersons.containsKey(event.getPersonId())) {
			onboardPersons.remove(event.getPersonId()).dropoffTime = event.getTime();
		}
	}

	public class RequestItem {
		public Id<Request> requestId;
		public Id<Person> personId;

		public double requestTime = Double.POSITIVE_INFINITY;
		public double rejectionTime = Double.POSITIVE_INFINITY;

		public double firstAssignmentTime = Double.POSITIVE_INFINITY;
		public double firstExpectedPickupTime = Double.POSITIVE_INFINITY;
		public double firstExpectedDropoffTime = Double.POSITIVE_INFINITY;

		public double lastAssignmentTime = Double.POSITIVE_INFINITY;
		public double lastExpectedPickupTime = Double.POSITIVE_INFINITY;
		public double lastExpectedDropoffTime = Double.POSITIVE_INFINITY;

		public double pickupTime = Double.POSITIVE_INFINITY;
		public double dropoffTime = Double.POSITIVE_INFINITY;

		boolean hasDeparted = false;

		public double pickupX = Double.NaN;
		public double pickupY = Double.NaN;

		public double dropoffX = Double.NaN;
		public double dropoffY = Double.NaN;

		public double latestPickupTime = Double.NaN;
		public double latestDropoffTime = Double.NaN;
	}
}
