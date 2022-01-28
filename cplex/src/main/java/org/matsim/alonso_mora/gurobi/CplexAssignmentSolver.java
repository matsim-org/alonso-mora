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

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;

/**
 * Solves the assignment problem as described by Alonso-Mora et al. using CPLEX
 * for Java. The CPLEX classes must be found in the class path for this to work.
 * 
 * @author sebhoerl
 */
public class CplexAssignmentSolver implements AssignmentSolver {
	public static final String TYPE = "CPLEX";

	private static final Logger logger = Logger.getLogger(CplexAssignmentSolver.class);

	private final double unassignmentPenalty;
	private final double rejectionPenalty;

	private final int numberOfThreads;

	public CplexAssignmentSolver(double unassignmentPenalty, double rejectionPenalty, int numberOfThreads) {
		this.unassignmentPenalty = unassignmentPenalty;
		this.rejectionPenalty = rejectionPenalty;
		this.numberOfThreads = numberOfThreads;
	}

	@Override
	public Solution solve(Stream<AlonsoMoraTrip> candidates) {
		List<AlonsoMoraTrip> tripList = candidates.collect(Collectors.toList());
		List<AlonsoMoraRequest> requestList = new ArrayList<>(
				tripList.stream().flatMap(t -> t.getRequests().stream()).collect(Collectors.toSet()));
		List<AlonsoMoraVehicle> vehicleList = new ArrayList<>(
				tripList.stream().map(t -> t.getVehicle()).collect(Collectors.toSet()));

		if (requestList.size() == 0) {
			return new Solution(Status.OPTIMAL, Collections.emptySet());
		}

		try {
			IloCplex cplex = new IloCplex();
			cplex.setParam(IloCplex.Param.Simplex.Display, 0);
			cplex.setParam(IloCplex.Param.MIP.Display, 0);
			cplex.setParam(IloCplex.Param.Threads, numberOfThreads);

			// Create variables

			List<IloIntVar> tripVariables = new ArrayList<>(tripList.size());

			for (int i = 0; i < tripList.size(); i++) {
				tripVariables.add(cplex.boolVar("T" + i));
			}

			List<IloIntVar> requestVariables = new ArrayList<>(requestList.size());

			for (int k = 0; k < requestList.size(); k++) {
				requestVariables.add(cplex.boolVar("x" + k));
			}

			// Add constraints

			// ... (1) one trip per vehicle

			for (int j = 0; j < vehicleList.size(); j++) {
				AlonsoMoraVehicle vehicle = vehicleList.get(j);
				IloLinearIntExpr expression = cplex.linearIntExpr();

				for (int i = 0; i < tripList.size(); i++) {
					if (tripList.get(i).getVehicle() == vehicle) {
						expression.addTerm(1, tripVariables.get(i));
					}
				}

				cplex.addLe(expression, 1.0, "V" + j);
			}

			// ... (2) either one assignment or none per request

			for (int k = 0; k < requestList.size(); k++) {
				AlonsoMoraRequest request = requestList.get(k);
				IloLinearIntExpr expression = cplex.linearIntExpr();

				for (int i = 0; i < tripList.size(); i++) {
					if (tripList.get(i).getRequests().contains(request)) {
						expression.addTerm(1, tripVariables.get(i));
					}
				}

				expression.addTerm(1, requestVariables.get(k));
				cplex.addEq(expression, 1.0, "R" + k);
			}

			// Objective

			IloLinearNumExpr objective = cplex.linearNumExpr();

			for (int i = 0; i < tripVariables.size(); i++) {
				AlonsoMoraTrip trip = tripList.get(i);
				objective.addTerm(trip.getResult().getCost(), tripVariables.get(i));
			}

			for (int k = 0; k < requestVariables.size(); k++) {
				AlonsoMoraRequest request = requestList.get(k);
				double penalty = request.isAssigned() ? unassignmentPenalty : rejectionPenalty;
				objective.addTerm(penalty, requestVariables.get(k));
			}

			cplex.addMinimize(objective);

			// Start optimization
			cplex.solve();

			IloCplex.Status status = cplex.getStatus();
			Solution solution = new Solution(Status.FAILURE, Collections.emptyList());

			if (status == IloCplex.Status.Optimal || status == IloCplex.Status.Feasible) {
				List<AlonsoMoraTrip> selection = new LinkedList<>();
				boolean isInvalid = false;

				for (int i = 0; i < tripList.size(); i++) {
					if (cplex.getValue(tripVariables.get(i)) > 0.9) {
						selection.add(tripList.get(i));
					} else if (cplex.getValue(tripVariables.get(i)) > 0.1) {
						isInvalid = true;
					}
				}

				if (!isInvalid) {
					solution = new Solution(status == IloCplex.Status.Optimal ? Status.OPTIMAL : Status.FEASIBLE,
							selection);
				}
			}

			cplex.end();
			cplex.close();

			return solution;
		} catch (IloException e) {
			throw new RuntimeException(e);
		}
	}

	static public boolean checkAvailability() {
		try {
			IloCplex cplex = new IloCplex();
			cplex.getVersionNumber();
			cplex.close();

			return true;
		} catch (UnsatisfiedLinkError | NoClassDefFoundError | IloException e) {
			e.printStackTrace();
		}

		logger.error( //
				"CPLEX is not set up correctly. Make sure to pass the path to CPLEX via the"
						+ " command line, e.g. -Djava.library.path=/path/to/cplex/bin/x86-64_linux");

		return false;
	}
}
