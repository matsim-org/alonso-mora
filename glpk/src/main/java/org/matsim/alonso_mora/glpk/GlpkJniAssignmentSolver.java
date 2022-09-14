package org.matsim.alonso_mora.glpk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_iocp;
import org.gnu.glpk.glp_prob;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.Solution.Status;

/**
 * Solves the assignment problem as described by Alonso-Mora et al. using GLPK
 * for Java. GLPK for Java must be configured correctly for this to work.
 * 
 * @author sebhoerl
 */
public class GlpkJniAssignmentSolver implements AssignmentSolver {
	static public final String TYPE = "GlpkJni";

	private static final Logger logger = LogManager.getLogger(GlpkJniAssignmentSolver.class);

	private final double unassignmentPenalty;
	private final double rejectionPenalty;
	private final double timeLimit;
	private final double optimalityGap;

	public GlpkJniAssignmentSolver(double unassignmentPenalty, double rejectionPenalty, double timeLimit,
			double optimalityGap) {
		this.unassignmentPenalty = unassignmentPenalty;
		this.rejectionPenalty = rejectionPenalty;
		this.timeLimit = timeLimit;
		this.optimalityGap = optimalityGap;
	}

	@Override
	public Solution solve(Stream<AlonsoMoraTrip> candidates) {
		glp_prob problem = GLPK.glp_create_prob();
		GLPK.glp_set_prob_name(problem, "AlonsoMoraAssignment");

		List<AlonsoMoraTrip> tripList = candidates.collect(Collectors.toList());
		List<AlonsoMoraRequest> requestList = new ArrayList<>(
				tripList.stream().flatMap(t -> t.getRequests().stream()).distinct().collect(Collectors.toList()));
		List<AlonsoMoraVehicle> vehicleList = new ArrayList<>(
				tripList.stream().map(t -> t.getVehicle()).distinct().collect(Collectors.toList()));

		int numberOfRequests = requestList.size();
		int numberOfTrips = tripList.size();
		int numberOfVehicles = vehicleList.size();

		int numberOfConstraints = numberOfVehicles + numberOfRequests;
		int numberOfVariables = numberOfTrips + numberOfRequests;

		if (numberOfRequests == 0) {
			return new Solution(Status.OPTIMAL, Collections.emptySet());
		}

		// Add variables

		GLPK.glp_add_cols(problem, numberOfVariables);

		for (int i = 0; i < numberOfVariables; i++) {
			GLPK.glp_set_col_kind(problem, i + 1, GLPKConstants.GLP_IV); // Integer Variable
			GLPK.glp_set_col_bnds(problem, i + 1, GLPKConstants.GLP_DB, 0, 1); // Between 0 and 1
		}

		for (int i = 0; i < numberOfTrips; i++) {
			GLPK.glp_set_col_name(problem, i + 1, "T" + i);
		}

		for (int i = 0; i < numberOfRequests; i++) {
			GLPK.glp_set_col_name(problem, i + numberOfTrips + 1, "x" + i);
		}

		// Add constraints

		GLPK.glp_add_rows(problem, numberOfConstraints);

		// ... (1) one trip per vehicle

		List<List<Integer>> allVehicleIndices = new ArrayList<>(numberOfVehicles);

		for (int i = 0; i < numberOfVehicles; i++) {
			allVehicleIndices.add(new LinkedList<>());
		}

		for (int i = 0; i < numberOfTrips; i++) {
			int vehicleIndex = vehicleList.indexOf(tripList.get(i).getVehicle());
			allVehicleIndices.get(vehicleIndex).add(i);
		}

		for (int i = 0; i < numberOfVehicles; i++) {
			List<Integer> vehicleIndices = allVehicleIndices.get(i);

			SWIGTYPE_p_int variables = GLPK.new_intArray(vehicleIndices.size() + 1);
			SWIGTYPE_p_double values = GLPK.new_doubleArray(vehicleIndices.size() + 1);

			for (int j = 0; j < vehicleIndices.size(); j++) {
				GLPK.intArray_setitem(variables, j + 1, vehicleIndices.get(j) + 1);
				GLPK.doubleArray_setitem(values, j + 1, 1.0);
			}

			GLPK.glp_set_row_bnds(problem, i + 1, GLPKConstants.GLP_UP, 0, 1);
			GLPK.glp_set_mat_row(problem, i + 1, vehicleIndices.size(), variables, values);
		}

		// ... (2) each request needs a vehicle or is unassigned

		for (int k = 0; k < numberOfRequests; k++) {
			AlonsoMoraRequest request = requestList.get(k);
			Set<Integer> requestIndices = new HashSet<>();

			for (int i = 0; i < tripList.size(); i++) {
				AlonsoMoraTrip trip = tripList.get(i);

				if (trip.getRequests().contains(request)) {
					requestIndices.add(i);
				}
			}

			List<Integer> requestIndicesList = new ArrayList<>(requestIndices);

			SWIGTYPE_p_int variables = GLPK.new_intArray(requestIndices.size() + 2);
			SWIGTYPE_p_double values = GLPK.new_doubleArray(requestIndices.size() + 2);

			for (int j = 0; j < requestIndices.size(); j++) {
				GLPK.intArray_setitem(variables, j + 1, requestIndicesList.get(j) + 1);
				GLPK.doubleArray_setitem(values, j + 1, 1.0);
			}

			// Request selection variable is added at the end
			GLPK.intArray_setitem(variables, requestIndices.size() + 1, numberOfTrips + k + 1);
			GLPK.doubleArray_setitem(values, requestIndices.size() + 1, 1.0);

			GLPK.glp_set_row_bnds(problem, k + numberOfVehicles + 1, GLPKConstants.GLP_FX, 1, 1);
			GLPK.glp_set_mat_row(problem, k + numberOfVehicles + 1, requestIndices.size() + 1, variables, values);
		}

		// Add objective

		GLPK.glp_set_obj_name(problem, "C");
		GLPK.glp_set_obj_dir(problem, GLPKConstants.GLP_MIN);

		for (int i = 0; i < numberOfTrips; i++) {
			GLPK.glp_set_obj_coef(problem, i + 1, tripList.get(i).getResult().getCost());
		}

		for (int i = 0; i < numberOfRequests; i++) {
			double penalty = requestList.get(i).isAssigned() ? unassignmentPenalty : rejectionPenalty;
			GLPK.glp_set_obj_coef(problem, i + numberOfTrips + 1, penalty);
		}

		// Solve problem

		glp_iocp parameters = new glp_iocp();
		GLPK.glp_init_iocp(parameters);
		parameters.setTm_lim((int) (timeLimit * 1e3));
		parameters.setMip_gap(optimalityGap);

		parameters.setPresolve(GLPK.GLP_ON);
		GLPK.glp_term_out(GLPK.GLP_OFF);

		GLPK.glp_intopt(problem, parameters);
		Collection<AlonsoMoraTrip> selection = new LinkedList<>();

		boolean isOptimal = false;
		boolean isFeasible = false;

		int status = GLPK.glp_mip_status(problem);

		if (status == GLPK.GLP_FEAS || status == GLPK.GLP_OPT) {
			boolean isNonInteger = false;

			for (int i = 0; i < numberOfTrips; i++) {
				if (GLPK.glp_mip_col_val(problem, i + 1) > 0.7) {
					selection.add(tripList.get(i));
				} else if (GLPK.glp_mip_col_val(problem, i + 1) > 0.3) {
					isNonInteger = true;
				}
			}

			if (isNonInteger) {
				logger.warn("Non-integer solution found by GLPK");
			}

			isFeasible = true;

			if (status != GLPK.GLP_OPT) {
				logger.warn("Using non-optimal feasible GLPK solution");
			} else {
				isOptimal = true;
			}
		} else {
			logger.warn("GLPK problem could not be solved. Status: " + status);
		}

		GLPK.glp_delete_prob(problem);

		if (isFeasible) {
			return new Solution(isOptimal ? Status.OPTIMAL : Status.FEASIBLE, selection);
		} else {
			return new Solution(Status.FAILURE, selection);
		}
	}

	static public boolean checkAvailability() {
		try {
			GLPK.glp_version();
			return true;
		} catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
			e.printStackTrace();
		}

		logger.error( //
				"GLPK for Java is not set up correctly. Make sure to pass the path to GLPK JNI via the"
						+ " command line, e.g. -Djava.library.path=/usr/lib/x86_64-linux-gnu/jni . This example"
						+ " path should work out-of-the-box for recent Ubuntu distributions with the package "
						+ " libglpk-java installed.");

		return false;
	}
}
