package org.matsim.alonso_mora.algorithm.function;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop;
import org.matsim.alonso_mora.algorithm.AlonsoMoraStop.StopType;
import org.matsim.alonso_mora.travel_time.TravelTimeEstimator;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.drt.stops.StaticPassengerStopDurationProvider;
import org.mockito.Mockito;

public class RouteTrackerTest {
	@Test
	public void testInitializationWithoutLink() {
		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		TravelTimeEstimator estimator = Mockito.mock(TravelTimeEstimator.class);
		Mockito.when(
				estimator.estimateTravelTime(Mockito.any(), Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble()))
				.thenReturn(100.0);
		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkA), Mockito.eq(linkA), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(0.0);

		AlonsoMoraRequest request = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request.getSize()).thenReturn(1);

		RouteTracker tracker = new RouteTracker(null, estimator, StaticPassengerStopDurationProvider.of(5.0, 0.0), 5.0, 3, 7000.0, Optional.empty());

		List<AlonsoMoraStop> initialStops = new LinkedList<>();
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));

		tracker.update(initialStops);

		assertEquals(7000.0, tracker.getArrivalTime(0), 1e-3);
		assertEquals(7005.0, tracker.getDepartureTime(0), 1e-3);
		assertEquals(7105.0, tracker.getArrivalTime(1), 1e-3);
		assertEquals(7110.0, tracker.getDepartureTime(1), 1e-3);

		assertEquals(4, tracker.getOccupancyAfter(0));
		assertEquals(3, tracker.getOccupancyAfter(1));
	}

	@Test
	public void testInitializationWithLink() {
		Link linkVehicle = Mockito.mock(Link.class);
		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		TravelTimeEstimator estimator = Mockito.mock(TravelTimeEstimator.class);
		Mockito.when(
				estimator.estimateTravelTime(Mockito.any(), Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble()))
				.thenReturn(100.0);
		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkA), Mockito.eq(linkA), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(0.0);
		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkVehicle), Mockito.eq(linkA), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(100.0);

		AlonsoMoraRequest request = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request.getSize()).thenReturn(1);

		RouteTracker tracker = new RouteTracker(null, estimator, StaticPassengerStopDurationProvider.of(5.0, 0.0), 5.0, 3, 7000.0, Optional.of(linkVehicle));

		List<AlonsoMoraStop> initialStops = new LinkedList<>();
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));

		tracker.update(initialStops);

		assertEquals(7000.0 + 100.0, tracker.getArrivalTime(0), 1e-3);
		assertEquals(7005.0 + 100.0, tracker.getDepartureTime(0), 1e-3);
		assertEquals(7105.0 + 100.0, tracker.getArrivalTime(1), 1e-3);
		assertEquals(7110.0 + 100.0, tracker.getDepartureTime(1), 1e-3);

		assertEquals(4, tracker.getOccupancyAfter(0));
		assertEquals(3, tracker.getOccupancyAfter(1));
	}

	@Test
	public void testMultipleRequests() {
		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		TravelTimeEstimator estimator = Mockito.mock(TravelTimeEstimator.class);
		Mockito.when(
				estimator.estimateTravelTime(Mockito.any(), Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble()))
				.thenReturn(100.0);

		AlonsoMoraRequest request = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request.getSize()).thenReturn(1);

		RouteTracker tracker = new RouteTracker(null, estimator, StaticPassengerStopDurationProvider.of(5.0, 0.0), 5.0, 0, 7000.0, Optional.empty());

		List<AlonsoMoraStop> initialStops = new LinkedList<>();
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkB, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));

		tracker.update(initialStops);

		assertEquals(7100.0, tracker.getArrivalTime(0), 1e-3);
		assertEquals(7105.0, tracker.getDepartureTime(0), 1e-3);

		assertEquals(7205.0, tracker.getArrivalTime(1), 1e-3);
		assertEquals(7210.0, tracker.getDepartureTime(1), 1e-3);

		assertEquals(7310.0, tracker.getArrivalTime(2), 1e-3);
		assertEquals(7315.0, tracker.getDepartureTime(2), 1e-3);

		assertEquals(7415.0, tracker.getArrivalTime(3), 1e-3);
		assertEquals(7420.0, tracker.getDepartureTime(3), 1e-3);

		assertEquals(7520.0, tracker.getArrivalTime(4), 1e-3);
		assertEquals(7525.0, tracker.getDepartureTime(4), 1e-3);

		assertEquals(7625.0, tracker.getArrivalTime(5), 1e-3);
		assertEquals(7630.0, tracker.getDepartureTime(5), 1e-3);

		assertEquals(1, tracker.getOccupancyAfter(0));
		assertEquals(2, tracker.getOccupancyAfter(1));
		assertEquals(1, tracker.getOccupancyAfter(2));
		assertEquals(0, tracker.getOccupancyAfter(3));
		assertEquals(1, tracker.getOccupancyAfter(4));
		assertEquals(0, tracker.getOccupancyAfter(5));
	}

	@Test
	public void testGroupRequests() {
		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		TravelTimeEstimator estimator = Mockito.mock(TravelTimeEstimator.class);
		Mockito.when(
				estimator.estimateTravelTime(Mockito.any(), Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble()))
				.thenReturn(100.0);

		AlonsoMoraRequest request1 = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request1.getSize()).thenReturn(2);

		AlonsoMoraRequest request2 = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request2.getSize()).thenReturn(1);

		AlonsoMoraRequest request3 = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request3.getSize()).thenReturn(3);

		RouteTracker tracker = new RouteTracker(null, estimator, StaticPassengerStopDurationProvider.of(5.0, 0.0), 5.0, 0, 7000.0, Optional.empty());

		List<AlonsoMoraStop> initialStops = new LinkedList<>();
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request1));
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkB, request2));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkA, request2));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request1));
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request3));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request3));

		tracker.update(initialStops);

		assertEquals(2, tracker.getOccupancyAfter(0));
		assertEquals(3, tracker.getOccupancyAfter(1));
		assertEquals(2, tracker.getOccupancyAfter(2));
		assertEquals(0, tracker.getOccupancyAfter(3));
		assertEquals(3, tracker.getOccupancyAfter(4));
		assertEquals(0, tracker.getOccupancyAfter(5));
	}

	@Test
	public void testMultipleRequestsWithStopAggregation() {
		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		TravelTimeEstimator estimator = Mockito.mock(TravelTimeEstimator.class);
		Mockito.when(
				estimator.estimateTravelTime(Mockito.any(), Mockito.any(), Mockito.anyDouble(), Mockito.anyDouble()))
				.thenReturn(100.0);

		RouteTracker tracker = new RouteTracker(null, estimator, StaticPassengerStopDurationProvider.of(5.0, 0.0), 5.0, 0, 7000.0, Optional.empty());

		AlonsoMoraRequest request = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request.getSize()).thenReturn(1);

		List<AlonsoMoraStop> initialStops = new LinkedList<>();
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));
		initialStops.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
		initialStops.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));

		tracker.update(initialStops);

		assertEquals(7100.0, tracker.getArrivalTime(0), 1e-3);
		assertEquals(7105.0, tracker.getDepartureTime(0), 1e-3);

		assertEquals(7100.0, tracker.getArrivalTime(1), 1e-3);
		assertEquals(7105.0, tracker.getDepartureTime(1), 1e-3);

		assertEquals(7205.0, tracker.getArrivalTime(2), 1e-3);
		assertEquals(7210.0, tracker.getDepartureTime(2), 1e-3);

		assertEquals(7205.0, tracker.getArrivalTime(3), 1e-3);
		assertEquals(7210.0, tracker.getDepartureTime(3), 1e-3);

		assertEquals(7310.0, tracker.getArrivalTime(4), 1e-3);
		assertEquals(7315.0, tracker.getDepartureTime(4), 1e-3);

		assertEquals(7415.0, tracker.getArrivalTime(5), 1e-3);
		assertEquals(7420.0, tracker.getDepartureTime(5), 1e-3);

		assertEquals(1, tracker.getOccupancyAfter(0));
		assertEquals(2, tracker.getOccupancyAfter(1));
		assertEquals(1, tracker.getOccupancyAfter(2));
		assertEquals(0, tracker.getOccupancyAfter(3));
		assertEquals(1, tracker.getOccupancyAfter(4));
		assertEquals(0, tracker.getOccupancyAfter(5));
	}

	@Test
	public void testUpdateIndex() {
		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		TravelTimeEstimator estimator = Mockito.mock(TravelTimeEstimator.class);

		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkA), Mockito.eq(linkB), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(100.0);
		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkB), Mockito.eq(linkA), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(10.0);
		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkA), Mockito.eq(linkA), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(0.0);
		Mockito.when(estimator.estimateTravelTime(Mockito.eq(linkB), Mockito.eq(linkB), Mockito.anyDouble(),
				Mockito.anyDouble())).thenReturn(0.0);

		RouteTracker tracker = new RouteTracker(null, estimator, StaticPassengerStopDurationProvider.of(5.0, 0.0), 5.0, 0, 7000.0, Optional.empty());

		AlonsoMoraRequest request = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request.getSize()).thenReturn(1);

		{
			List<AlonsoMoraStop> sequence = new LinkedList<>();
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
			assertEquals(0, tracker.update(sequence));

			assertEquals(7000.0, tracker.getArrivalTime(0), 1e-3);
			assertEquals(7005.0, tracker.getDepartureTime(0), 1e-3);
			assertEquals(1, tracker.getOccupancyAfter(0));
		}

		{
			List<AlonsoMoraStop> sequence = new LinkedList<>();
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkB, request));
			assertEquals(1, tracker.update(sequence));

			assertEquals(7000.0, tracker.getArrivalTime(0), 1e-3);
			assertEquals(7005.0, tracker.getDepartureTime(0), 1e-3);
			assertEquals(1, tracker.getOccupancyAfter(0));

			assertEquals(7105.0, tracker.getArrivalTime(1), 1e-3);
			assertEquals(7110.0, tracker.getDepartureTime(1), 1e-3);
			assertEquals(2, tracker.getOccupancyAfter(1));
		}

		{
			List<AlonsoMoraStop> sequence = new LinkedList<>();
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkB, request));
			sequence.add(new AlonsoMoraStop(StopType.Dropoff, linkA, request));
			sequence.add(new AlonsoMoraStop(StopType.Dropoff, linkB, request));
			assertEquals(2, tracker.update(sequence));

			assertEquals(7000.0, tracker.getArrivalTime(0), 1e-3);
			assertEquals(7005.0, tracker.getDepartureTime(0), 1e-3);
			assertEquals(1, tracker.getOccupancyAfter(0));

			assertEquals(7105.0, tracker.getArrivalTime(1), 1e-3);
			assertEquals(7110.0, tracker.getDepartureTime(1), 1e-3);
			assertEquals(2, tracker.getOccupancyAfter(1));

			assertEquals(7120.0, tracker.getArrivalTime(2), 1e-3);
			assertEquals(7125.0, tracker.getDepartureTime(2), 1e-3);
			assertEquals(1, tracker.getOccupancyAfter(2));

			assertEquals(7225.0, tracker.getArrivalTime(3), 1e-3);
			assertEquals(7230.0, tracker.getDepartureTime(3), 1e-3);
			assertEquals(0, tracker.getOccupancyAfter(3));
		}

		{
			List<AlonsoMoraStop> sequence = new LinkedList<>();
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkA, request));
			sequence.add(new AlonsoMoraStop(StopType.Dropoff, linkA, request));
			assertEquals(1, tracker.update(sequence));

			assertEquals(7000.0, tracker.getArrivalTime(0), 1e-3);
			assertEquals(7005.0, tracker.getDepartureTime(0), 1e-3);
			assertEquals(1, tracker.getOccupancyAfter(0));

			assertEquals(7000.0, tracker.getArrivalTime(1), 1e-3);
			assertEquals(7005.0, tracker.getDepartureTime(1), 1e-3);
			assertEquals(0, tracker.getOccupancyAfter(1));
		}

		{
			List<AlonsoMoraStop> sequence = new LinkedList<>();
			sequence.add(new AlonsoMoraStop(StopType.Pickup, linkB, request));
			assertEquals(0, tracker.update(sequence));

			assertEquals(7000.0, tracker.getArrivalTime(0), 1e-3);
			assertEquals(7005.0, tracker.getDepartureTime(0), 1e-3);
			assertEquals(1, tracker.getOccupancyAfter(0));
		}
	}
}
