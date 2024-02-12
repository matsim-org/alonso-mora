package org.matsim.alonso_mora.gurobi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.DefaultRejectionPenalty;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.RejectionPenalty;
import org.matsim.alonso_mora.algorithm.function.AlonsoMoraFunction.Result;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.mockito.Mockito;

public class GurobiAssignmentSolverTest {
	@BeforeAll
	static public void checkSolver() {
		assertTrue(GurobiAssignmentSolver.checkAvailability(), "Checking for availability of GLPK solver");
	}

	private AlonsoMoraRequest mockRequest() {
		return Mockito.mock(AlonsoMoraRequest.class);
	}

	private AlonsoMoraVehicle mockVehicle(int index) {
		AlonsoMoraVehicle vehicle = Mockito.mock(AlonsoMoraVehicle.class);
		DvrpVehicle dvrpVehicle = Mockito.mock(DvrpVehicle.class);
		Id<DvrpVehicle> vehicleId = Id.create(index, DvrpVehicle.class);
		Mockito.when(dvrpVehicle.getId()).thenReturn(vehicleId);
		Mockito.when(vehicle.getVehicle()).thenReturn(dvrpVehicle);
		return vehicle;
	}

	private AlonsoMoraTrip mockTrip(AlonsoMoraVehicle vehicle, double cost, AlonsoMoraRequest... requests) {
		AlonsoMoraTrip trip = Mockito.mock(AlonsoMoraTrip.class);
		Mockito.when(trip.getVehicle()).thenReturn(vehicle);
		Mockito.when(trip.getRequests()).thenReturn(Arrays.asList(requests));

		Result result = Mockito.mock(Result.class);
		Mockito.when(trip.getResult()).thenReturn(result);

		Mockito.when(result.getCost()).thenReturn(cost);

		return trip;
	}

	@Test
	public void testOneVehicleOneRequestExample() {
		RejectionPenalty rejectionPenalty = new DefaultRejectionPenalty(9000.0, 9000.0);
		AssignmentSolver solver = new GurobiAssignmentSolver(rejectionPenalty, 1000, 10.0, 0.1);

		AlonsoMoraVehicle vehicle = mockVehicle(0);
		AlonsoMoraRequest request = mockRequest();
		AlonsoMoraTrip trip = mockTrip(vehicle, 100.0, request);

		List<AlonsoMoraTrip> candidates = Arrays.asList(trip);
		Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

		assertEquals(1, selection.size());
		assertTrue(selection.contains(trip));
	}

	@Test
	public void testTwoIndependentRequests() {
		RejectionPenalty rejectionPenalty = new DefaultRejectionPenalty(9000.0, 9000.0);
		AssignmentSolver solver = new GurobiAssignmentSolver(rejectionPenalty, 1000, 10.0, 0.1);

		AlonsoMoraVehicle vehicle1 = mockVehicle(1);
		AlonsoMoraRequest request1 = mockRequest();
		AlonsoMoraTrip trip1 = mockTrip(vehicle1, 100.0, request1);

		AlonsoMoraVehicle vehicle2 = mockVehicle(2);
		AlonsoMoraRequest request2 = mockRequest();
		AlonsoMoraTrip trip2 = mockTrip(vehicle2, 200.0, request2);

		List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2);
		Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

		assertEquals(2, selection.size());
		assertTrue(selection.contains(trip1));
		assertTrue(selection.contains(trip2));
	}

	@Test
	public void testTwoRequestsWithOneVehicle() {
		RejectionPenalty rejectionPenalty = new DefaultRejectionPenalty(9000.0, 9000.0);
		AssignmentSolver solver = new GurobiAssignmentSolver(rejectionPenalty, 1000, 10.0, 0.1);

		AlonsoMoraVehicle vehicle = mockVehicle(0);
		AlonsoMoraRequest request1 = mockRequest();
		AlonsoMoraRequest request2 = mockRequest();

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle, 100.0, request1);
			AlonsoMoraTrip trip2 = mockTrip(vehicle, 200.0, request2);
			AlonsoMoraTrip trip3 = mockTrip(vehicle, 300.0, request1, request2);

			// Must take trip 3 as the first two are not independent, but penalty leads us
			// to assign two requests

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(1, selection.size());
			assertTrue(selection.contains(trip3));
		}
	}

	@Test
	public void testTwoRequestsWithOneVehicleLowPenalty() {
		RejectionPenalty rejectionPenalty = new DefaultRejectionPenalty(250.0, 250.0);
		AssignmentSolver solver = new GurobiAssignmentSolver(rejectionPenalty, 1000, 10.0, 0.1);

		AlonsoMoraVehicle vehicle = mockVehicle(0);
		AlonsoMoraRequest request1 = mockRequest();
		AlonsoMoraRequest request2 = mockRequest();

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle, 100.0, request1);
			AlonsoMoraTrip trip2 = mockTrip(vehicle, 200.0, request2);
			AlonsoMoraTrip trip3 = mockTrip(vehicle, 600.0, request1, request2);

			// Must take trip 1 as trip3 is higher than the penalty.

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(1, selection.size());
			assertTrue(selection.contains(trip1));
		}
	}
}
