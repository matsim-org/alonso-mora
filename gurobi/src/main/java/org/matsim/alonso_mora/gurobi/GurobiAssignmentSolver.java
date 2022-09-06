package org.matsim.alonso_mora.gurobi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.log4j.Logger;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.Solution.Status;
import org.matsim.alonso_mora.algorithm.assignment.GreedyVehicleFirstSolver;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import gurobi.GurobiJni;

/**
 * Solves the assignment problem as described by Alonso-Mora et al. using Gurobi
 * for Java. The Gurobi classes must be found in the class path for this to
 * work.
 * 
 * @author sebhoerl
 */
public class GurobiAssignmentSolver implements AssignmentSolver {
	public static final String TYPE = "Gurobi";

	private static final Logger logger = Logger.getLogger(GurobiAssignmentSolver.class);

	private final double unassignmentPenalty;
	private final double rejectionPenalty;

	private final int numberOfThreads;
	private final double timeLimit;
	private final double optimalityGap;

	public GurobiAssignmentSolver(double unassignmentPenalty, double rejectionPenalty, int numberOfThreads,
			double timeLimit, double optimalityGap) {
		this.unassignmentPenalty = unassignmentPenalty;
		this.rejectionPenalty = rejectionPenalty;
		this.numberOfThreads = numberOfThreads;
		this.timeLimit = timeLimit;
		this.optimalityGap = optimalityGap;
	}

	@Override
	public Solution solve(Stream<AlonsoMoraTrip> candidates) {
		List<AlonsoMoraTrip> tripList = candidates.collect(Collectors.toList());
		List<AlonsoMoraRequest> requestList = new ArrayList<>(
				tripList.stream().flatMap(t -> t.getRequests().stream()).distinct().collect(Collectors.toList()));
		List<AlonsoMoraVehicle> vehicleList = new ArrayList<>(
				tripList.stream().map(t -> t.getVehicle()).distinct().collect(Collectors.toList()));

		if (requestList.size() == 0) {
			return new Solution(Status.OPTIMAL, Collections.emptySet());
		}

		try {
			GRBEnv env = new GRBEnv(true);
			env.set(GRB.IntParam.LogToConsole, 0);
			env.set(GRB.IntParam.Threads, numberOfThreads);
			env.set(GRB.DoubleParam.TimeLimit, timeLimit);
			env.set(GRB.DoubleParam.MIPGap, optimalityGap);
			env.start();

			GRBModel model = new GRBModel(env);

			// Create variables

			List<GRBVar> tripVariables = new ArrayList<>(tripList.size());

			for (int i = 0; i < tripList.size(); i++) {
				tripVariables.add(model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "T" + i));
			}

			List<GRBVar> requestVariables = new ArrayList<>(requestList.size());

			for (int k = 0; k < requestList.size(); k++) {
				requestVariables.add(model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x" + k));
			}

			// Add constraints

			// ... (1) one trip per vehicle

			for (int j = 0; j < vehicleList.size(); j++) {
				AlonsoMoraVehicle vehicle = vehicleList.get(j);
				GRBLinExpr expression = new GRBLinExpr();

				for (int i = 0; i < tripList.size(); i++) {
					if (tripList.get(i).getVehicle() == vehicle) {
						expression.addTerm(1.0, tripVariables.get(i));
					}
				}

				model.addConstr(expression, GRB.LESS_EQUAL, 1.0, "V" + j);
			}

			// ... (2) either one assignment or none per request

			for (int k = 0; k < requestList.size(); k++) {
				AlonsoMoraRequest request = requestList.get(k);
				GRBLinExpr expression = new GRBLinExpr();

				for (int i = 0; i < tripList.size(); i++) {
					if (tripList.get(i).getRequests().contains(request)) {
						expression.addTerm(1.0, tripVariables.get(i));
					}
				}

				expression.addTerm(1.0, requestVariables.get(k));
				model.addConstr(expression, GRB.EQUAL, 1.0, "R" + k);
			}

			// Objective

			GRBLinExpr objective = new GRBLinExpr();

			for (int i = 0; i < tripVariables.size(); i++) {
				AlonsoMoraTrip trip = tripList.get(i);
				objective.addTerm(trip.getResult().getCost(), tripVariables.get(i));
			}

			for (int k = 0; k < requestVariables.size(); k++) {
				AlonsoMoraRequest request = requestList.get(k);
				double penalty = request.isAssigned() ? unassignmentPenalty : rejectionPenalty;
				objective.addTerm(penalty, requestVariables.get(k));
			}

			model.setObjective(objective, GRB.MINIMIZE);

			{ // Find heuristic solution and implement
				GreedyVehicleFirstSolver heuristicSolver = new GreedyVehicleFirstSolver();
				Solution heuristicSolution = heuristicSolver.solve(tripList.stream());

				for (int i = 0; i < requestList.size(); i++) {
					requestVariables.get(i).set(GRB.DoubleAttr.Start, 1.0);
				}

				for (int i = 0; i < tripList.size(); i++) {
					tripVariables.get(i).set(GRB.DoubleAttr.Start, 0.0);
				}

				for (AlonsoMoraTrip trip : heuristicSolution.trips) {
					tripVariables.get(tripList.indexOf(trip)).set(GRB.DoubleAttr.Start, 1.0);

					for (AlonsoMoraRequest request : trip.getRequests()) {
						requestVariables.get(requestList.indexOf(request)).set(GRB.DoubleAttr.Start, 0.0);
					}
				}
			}

			// Start optimization
			model.optimize();

			int status = model.get(GRB.IntAttr.Status);
			Solution solution = new Solution(Status.FAILURE, Collections.emptyList());

			if (status == GRB.Status.OPTIMAL) {
				List<AlonsoMoraTrip> selection = new LinkedList<>();
				boolean isInvalid = false;

				for (int i = 0; i < tripList.size(); i++) {
					if (tripVariables.get(i).get(GRB.DoubleAttr.X) > 0.9) {
						selection.add(tripList.get(i));
					} else if (tripVariables.get(i).get(GRB.DoubleAttr.X) > 0.1) {
						isInvalid = true;
					}
				}

				if (!isInvalid) {
					solution = new Solution(Status.OPTIMAL, selection);
				}
			}

			model.dispose();
			env.dispose();

			return solution;
		} catch (GRBException e) {
			throw new RuntimeException(e);
		}
	}

	static public boolean checkAvailability() {
		try {
			GurobiJni.version(new int[] { 0, 0, 0, 0, 0, 0 });
			return true;
		} catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
			e.printStackTrace();
		}

		logger.error( //
				"Gurobi is not set up correctly. Make sure to pass the path to Gurobi via the"
						+ " command line, e.g. -Djava.library.path=/path/to/gurobi9.1.2_linux64/gurobi912/linux64/lib . Furthermore, "
						+ "set LD_LIBRARY_PATH to /path/to/gurobi9.1.2_linux64/gurobi912/linux64/lib . Provide the license file through an environment "
						+ " variable like GRB_LICENSE_FILE=/path/to/gurobi.lic");

		return false;
	}
}
