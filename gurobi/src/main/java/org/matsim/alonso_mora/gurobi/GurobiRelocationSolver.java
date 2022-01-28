package org.matsim.alonso_mora.gurobi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

/**
 * Implements the relocation solver as propose in Alonso-Mora et al. (2017)
 * based on a minimum cost flow problem. The problem is solved using Gurobi.
 * 
 * @author sebhoerl
 */
public class GurobiRelocationSolver implements RelocationSolver {
	public static final String TYPE = "Gurobi";
	
	private final int numberOfThreads;

	public GurobiRelocationSolver(int numberOfThreads) {
		this.numberOfThreads = numberOfThreads;
	}

	@Override
	public Collection<Relocation> solve(List<Relocation> candidates) {
		List<Relocation> relocationList = new ArrayList<>(candidates);

		int numberOfVehicles = relocationList.stream().map(t -> t.vehicle).collect(Collectors.toSet()).size();
		int numberOfDestinations = relocationList.stream().map(t -> t.destination).collect(Collectors.toSet()).size();

		int numberOfVariables = relocationList.size();
		int numberOfAssignments = Math.min(numberOfVehicles, numberOfDestinations);

		if (relocationList.size() == 0) {
			return Collections.emptySet();
		}

		try {
			// Start problem

			GRBEnv environment = new GRBEnv(true);
			environment.set(GRB.IntParam.LogToConsole, 0);
			environment.set(GRB.IntParam.Threads, numberOfThreads);
			environment.start();

			GRBModel model = new GRBModel(environment);

			// Add variables

			List<GRBVar> variables = new ArrayList<>(numberOfVariables);

			for (int i = 0; i < numberOfVariables; i++) {
				variables.add(model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y" + i));
			}

			// Add constraint

			GRBLinExpr constraint = new GRBLinExpr();

			for (int i = 0; i < numberOfVariables; i++) {
				constraint.addTerm(1.0, variables.get(i));
			}

			model.addConstr(constraint, GRB.EQUAL, numberOfAssignments, "C");

			// Objective

			GRBLinExpr objective = new GRBLinExpr();

			for (int i = 0; i < numberOfVariables; i++) {
				Relocation relocation = relocationList.get(i);
				objective.addTerm(relocation.cost, variables.get(i));
			}

			model.setObjective(objective, GRB.MINIMIZE);

			// Optimize

			model.optimize();

			int status = model.get(GRB.IntAttr.Status);
			List<Relocation> result = new LinkedList<>();

			if (status == GRB.Status.OPTIMAL) {
				for (int i = 0; i < relocationList.size(); i++) {
					if (variables.get(i).get(GRB.DoubleAttr.X) > 0.0) {
						result.add(relocationList.get(i));
					}
				}
			}

			model.dispose();
			environment.dispose();

			return result;
		} catch (GRBException e) {
			throw new RuntimeException(e);
		}
	}
}
