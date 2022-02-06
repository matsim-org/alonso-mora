package org.matsim.alonso_mora.algorithm.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.Solution.Status;

/**
 * Greedy heuristic solver as described in the SI of the original paper.
 * 
 * @author sebhoerl
 */
public class GreedyVehicleFirstSolver implements AssignmentSolver {
	static public final String TYPE = "GreedyVehicleFirst";

	static private class TripComparator implements Comparator<AlonsoMoraTrip> {
		@Override
		public int compare(AlonsoMoraTrip a, AlonsoMoraTrip b) {
			return Double.compare(a.getResult().getCost(), b.getResult().getCost());
		}
	}

	@Override
	public Solution solve(Stream<AlonsoMoraTrip> candidates) {
		List<AlonsoMoraTrip> candidateList = new LinkedList<>(candidates.collect(Collectors.toList()));

		List<AlonsoMoraVehicle> vehicleList = new ArrayList<>(
				candidateList.stream().map(t -> t.getVehicle()).collect(Collectors.toSet()));
		Collections.sort(vehicleList, (a, b) -> {
			return a.getVehicle().getId().compareTo(b.getVehicle().getId());
		});

		Set<AlonsoMoraRequest> selectedRequests = new HashSet<>();
		List<AlonsoMoraTrip> solution = new LinkedList<>();

		for (AlonsoMoraVehicle vehicle : vehicleList) {
			List<AlonsoMoraTrip> vehicleTrips = candidateList.stream().filter(t -> t.getVehicle() == vehicle)
					.collect(Collectors.toList());
			Collections.sort(vehicleTrips, new TripComparator());

			while (vehicleTrips.size() > 0) {
				AlonsoMoraTrip selectedTrip = vehicleTrips.remove(0);

				boolean skip = false;

				for (AlonsoMoraRequest request : selectedTrip.getRequests()) {
					if (selectedRequests.contains(request)) {
						skip = true;
						break;
					}
				}

				if (!skip) {
					selectedRequests.addAll(selectedTrip.getRequests());
					solution.add(selectedTrip);
					break;
				}
			}
		}

		return new Solution(Status.OPTIMAL, solution);
	}
}
