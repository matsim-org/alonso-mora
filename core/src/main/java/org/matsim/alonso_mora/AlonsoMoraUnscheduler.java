package org.matsim.alonso_mora;

import org.matsim.api.core.v01.Id;
import org.matsim.contrib.drt.prebooking.unscheduler.RequestUnscheduler;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.optimizer.Request;

public class AlonsoMoraUnscheduler implements RequestUnscheduler {
	@Override
	public void unscheduleRequest(double now, Id<DvrpVehicle> vehicleId, Id<Request> requestId) {
		// in AM we do not unschedule any request as the schedules are rebuilt in every
		// step
	}
}
