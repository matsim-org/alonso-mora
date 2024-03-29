package org.matsim.alonso_mora.algorithm.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.function.AlonsoMoraFunction.Result;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.mockito.Mockito;

public class GreedyVehicleFirstAssignmentSolverTest {
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
		AssignmentSolver solver = new GreedyVehicleFirstSolver();

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
		AssignmentSolver solver = new GreedyVehicleFirstSolver();

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
		AssignmentSolver solver = new GreedyVehicleFirstSolver();

		AlonsoMoraVehicle vehicle = mockVehicle(0);
		AlonsoMoraRequest request1 = mockRequest();
		AlonsoMoraRequest request2 = mockRequest();

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle, 100.0, request1); // Trip 1 is best
			AlonsoMoraTrip trip2 = mockTrip(vehicle, 200.0, request2);
			AlonsoMoraTrip trip3 = mockTrip(vehicle, 300.0, request1, request2);

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(1, selection.size());
			assertTrue(selection.contains(trip1));
		}

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle, 100.0, request1);
			AlonsoMoraTrip trip2 = mockTrip(vehicle, 50.0, request2); // Trip 2 is best
			AlonsoMoraTrip trip3 = mockTrip(vehicle, 200.0, request1, request2);

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(1, selection.size());
			assertTrue(selection.contains(trip2));
		}

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle, 300.0, request1);
			AlonsoMoraTrip trip2 = mockTrip(vehicle, 200.0, request2);
			AlonsoMoraTrip trip3 = mockTrip(vehicle, 50.0, request1, request2); // Combination is best

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(1, selection.size());
			assertTrue(selection.contains(trip3));
		}
	}

	@Test
	public void testTwoRequestsWithTwoVehicles() {
		AssignmentSolver solver = new GreedyVehicleFirstSolver();

		AlonsoMoraVehicle vehicle1 = mockVehicle(1);
		AlonsoMoraVehicle vehicle2 = mockVehicle(2);

		AlonsoMoraRequest request1 = mockRequest();
		AlonsoMoraRequest request2 = mockRequest();

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle1, 100.0, request1);
			AlonsoMoraTrip trip2 = mockTrip(vehicle2, 200.0, request2);
			AlonsoMoraTrip trip3 = mockTrip(vehicle1, 50.0, request1, request2); // Trip 3 is best
			AlonsoMoraTrip trip4 = mockTrip(vehicle2, 300.0, request1, request2);

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3, trip4);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(1, selection.size());
			assertTrue(selection.contains(trip3));
		}

		{
			AlonsoMoraTrip trip1 = mockTrip(vehicle1, 100.0, request1); // Trip 1 + 2 are best
			AlonsoMoraTrip trip2 = mockTrip(vehicle2, 200.0, request2); // Trip 1 + 2 are best
			AlonsoMoraTrip trip3 = mockTrip(vehicle1, 450.0, request1, request2);
			AlonsoMoraTrip trip4 = mockTrip(vehicle2, 300.0, request1, request2);

			List<AlonsoMoraTrip> candidates = Arrays.asList(trip1, trip2, trip3, trip4);
			Collection<AlonsoMoraTrip> selection = solver.solve(candidates.stream()).trips;

			assertEquals(2, selection.size());
			assertTrue(selection.contains(trip1));
			assertTrue(selection.contains(trip2));
		}
	}
}
