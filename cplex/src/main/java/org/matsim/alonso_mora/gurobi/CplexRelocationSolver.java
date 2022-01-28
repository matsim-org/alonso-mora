package org.matsim.alonso_mora.gurobi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.cplex.IloCplex;

/**
 * Implements the relocation solver as propose in Alonso-Mora et al. (2017)
 * based on a minimum cost flow problem. The problem is solved using CPLEX.
 * 
 * @author sebhoerl
 */
public class CplexRelocationSolver implements RelocationSolver {
	public static final String TYPE = "CPLEX";

	private final int numberOfThreads;

	public CplexRelocationSolver(int numberOfThreads) {
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

			IloCplex cplex = new IloCplex();
			cplex.setParam(IloCplex.Param.Simplex.Display, 0);
			cplex.setParam(IloCplex.Param.MIP.Display, 0);
			cplex.setParam(IloCplex.Param.Threads, numberOfThreads);

			// Add variables

			List<IloIntVar> variables = new ArrayList<>(numberOfVariables);

			for (int i = 0; i < numberOfVariables; i++) {
				variables.add(cplex.boolVar("y" + i));
			}

			// Add constraint

			IloLinearIntExpr constraint = cplex.linearIntExpr();

			for (int i = 0; i < numberOfVariables; i++) {
				constraint.addTerm(1, variables.get(i));
			}

			cplex.addEq(constraint, numberOfAssignments, "C");

			// Objective

			IloLinearNumExpr objective = cplex.linearNumExpr();

			for (int i = 0; i < numberOfVariables; i++) {
				Relocation relocation = relocationList.get(i);
				objective.addTerm(relocation.cost, variables.get(i));
			}

			cplex.addMinimize(objective);

			// Optimize

			cplex.solve();

			IloCplex.Status status = cplex.getStatus();
			List<Relocation> result = new LinkedList<>();

			if (status == IloCplex.Status.Optimal) {
				for (int i = 0; i < relocationList.size(); i++) {
					if (cplex.getValue(variables.get(i)) > 0.0) {
						result.add(relocationList.get(i));
					}
				}
			}

			cplex.end();
			cplex.close();

			return result;
		} catch (IloException e) {
			throw new RuntimeException(e);
		}
	}
}
