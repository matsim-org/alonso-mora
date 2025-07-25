package org.matsim.alonso_mora;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.matsim.alonso_mora.algorithm.AlonsoMoraAlgorithm.Information;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.Solution.Status;

/*
 * Simple helper class that collects information for the Alonso-Mora dispatcher over multiple iterations.
 * 
 * TODO: Currently only public because of shifts (different AM implementation)
 */
class InformationCollector {
	private final List<SolverInformation> solverInformation = new LinkedList<>();
	private final List<GraphInformation> graphInformation = new LinkedList<>();
	private final List<RebalancingInformation> rebalancingInformation = new LinkedList<>();
	private final List<OccupancyInformation> occupancyInformation = new LinkedList<>();
	private final List<ReassignmentInformation> reassignmentInformation = new LinkedList<>();

	public void addInformation(double simulationTime, Information information) {
		reassignmentInformation.add(new ReassignmentInformation(simulationTime, information.numberOfReassignments));
		solverInformation.add(new SolverInformation(simulationTime,
				1e-9 * (information.assignmentEndTime - information.assignmentStartTime), information.solutionStatus));
		graphInformation
				.add(new GraphInformation(simulationTime, information.requestGraphSize, information.vehicleGraphSize,
						1e-9 * (information.requestGraphEndTime - information.requestGraphStartTime),
						1e-9 * (information.vehicleGraphsEndTime - information.vehicleGraphsStartTime)));
		rebalancingInformation.add(new RebalancingInformation(simulationTime, information.numberOfRelocations,
				1e-9 * (information.relocationEndTime - information.relocationStartTime)));
		occupancyInformation.add(new OccupancyInformation(simulationTime, information.occupiedVehiclesByItems,
				information.occupiedVehiclesByRequests));
	}

	public List<SolverInformation> clearSolverInformation() {
		List<SolverInformation> information = new ArrayList<>(solverInformation);
		solverInformation.clear();
		return information;
	}

	public List<GraphInformation> clearGraphInformation() {
		List<GraphInformation> information = new ArrayList<>(graphInformation);
		graphInformation.clear();
		return information;
	}

	public List<RebalancingInformation> clearRebalancingInformation() {
		List<RebalancingInformation> information = new ArrayList<>(rebalancingInformation);
		rebalancingInformation.clear();
		return information;
	}

	public List<OccupancyInformation> clearOccupancyInformation() {
		List<OccupancyInformation> information = new ArrayList<>(occupancyInformation);
		occupancyInformation.clear();
		return information;
	}

	public List<ReassignmentInformation> clearReassignmentInformation() {
		List<ReassignmentInformation> information = new ArrayList<>(reassignmentInformation);
		reassignmentInformation.clear();
		return information;
	}

	public class RebalancingInformation {
		public final double simulationTime;
		public final int rebalancingDirectives;
		public final double rebalancingTime;

		public RebalancingInformation(double simulationTime, int rebalancingDirectives, double rebalancingTime) {
			this.simulationTime = simulationTime;
			this.rebalancingDirectives = rebalancingDirectives;
			this.rebalancingTime = rebalancingTime;
		}
	}

	public class GraphInformation {
		public final double simulationTime;
		public final int requestGraphSize;
		public final int tripGraphSize;
		public final double requestGraphTime;
		public final double tripGraphTime;

		public GraphInformation(double simulationTime, int requestGraphSize, int tripGraphSize, double requestGraphTime,
				double tripGraphTime) {
			this.simulationTime = simulationTime;
			this.requestGraphSize = requestGraphSize;
			this.tripGraphSize = tripGraphSize;
			this.requestGraphTime = requestGraphTime;
			this.tripGraphTime = tripGraphTime;
		}
	}

	public class SolverInformation {
		public final double simulationTime;
		public final double solutionTime;
		public final Status status;

		public SolverInformation(double simulationTime, double solutionTime, Status status) {
			this.simulationTime = simulationTime;
			this.solutionTime = solutionTime;
			this.status = status;
		}
	}

	static public class OccupancyInformation {
		public final double simulationTime;
		public final List<Integer> occupiedCountByRequests;
		public final List<Integer> occupiedCountByItems;

		public OccupancyInformation(double simulationTime, List<Integer> occupiedCountByItems,
				List<Integer> occupiedCountByRequests) {
			this.simulationTime = simulationTime;
			this.occupiedCountByItems = occupiedCountByItems;
			this.occupiedCountByRequests = occupiedCountByRequests;
		}
	}

	static public class ReassignmentInformation {
		public final double simulationTime;
		public final int reassignments;

		public ReassignmentInformation(double simulationTime, int reassignments) {
			this.simulationTime = simulationTime;
			this.reassignments = reassignments;
		}
	}
}
