package org.matsim.alonso_mora.algorithm.assignment;

import java.util.Collection;
import java.util.stream.Stream;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;

/**
 * Solves the assignment problem given a list of potential vehicle trips.
 * 
 * @author sebhoerl
 */
public interface AssignmentSolver {
	Solution solve(Stream<AlonsoMoraTrip> candidates);

	static public class Solution {
		public final Status status;
		public final Collection<AlonsoMoraTrip> trips;

		public enum Status {
			OPTIMAL, FEASIBLE, FAILURE
		}

		public Solution(Status status, Collection<AlonsoMoraTrip> trips) {
			this.status = status;
			this.trips = trips;
		}
	}

	static public interface RejectionPenalty {
		double getPenalty(AlonsoMoraRequest request);
	}

	static public class DefaultRejectionPenalty implements RejectionPenalty {
		private final double unassignmentPenalty;
		private final double rejectionPenalty;

		public DefaultRejectionPenalty(double unassignmentPenalty, double rejectionPenalty) {
			this.unassignmentPenalty = unassignmentPenalty;
			this.rejectionPenalty = rejectionPenalty;
		}

		@Override
		public double getPenalty(AlonsoMoraRequest request) {
			return request.isAssigned() ? unassignmentPenalty : rejectionPenalty;
		}
	}
}
