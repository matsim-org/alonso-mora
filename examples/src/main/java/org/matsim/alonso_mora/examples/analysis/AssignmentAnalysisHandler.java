package org.matsim.alonso_mora.examples.analysis;

import java.util.LinkedList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.optimizer.Request;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;

/**
 * This handler collects information on assignments between vehicles and
 * requests.
 * 
 * @author Sebastian Hörl, IRT SystemX
 */
class AssignmentAnalysisHandler implements PassengerRequestScheduledEventHandler {
	private final String mode;

	private final IdSet<Request> observedRequests = new IdSet<>(Request.class);
	private final List<AssignmentItem> items = new LinkedList<>();

	public AssignmentAnalysisHandler(String mode) {
		this.mode = mode;
	}

	@Override
	public void handleEvent(final PassengerRequestScheduledEvent event) {
		if (event.getMode().equals(mode)) {
			boolean isInitial = !observedRequests.contains(event.getRequestId());

			AssignmentItem item = new AssignmentItem();
			item.time = event.getTime();
			item.personId = event.getPersonId();
			item.vehicleId = event.getVehicleId();
			item.isInitial = isInitial;

			items.add(item);
			observedRequests.add(event.getRequestId());
		}
	}

	public List<AssignmentItem> consolidate() {
		return items;
	}

	public class AssignmentItem {
		public double time;

		public Id<Person> personId;
		public Id<DvrpVehicle> vehicleId;

		boolean isInitial = true;
	}
}
