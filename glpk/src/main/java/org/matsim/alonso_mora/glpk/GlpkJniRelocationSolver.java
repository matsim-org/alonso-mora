package org.matsim.alonso_mora.glpk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_iocp;
import org.gnu.glpk.glp_prob;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;

/**
 * Implements the relocation solver as propose in Alonso-Mora et al. (2017)
 * based on a minimum cost flow problem. The problem is solved using GLPK based
 * on GLPK for Java (JNI).
 * 
 * @author sebhoerl
 */
public class GlpkJniRelocationSolver implements RelocationSolver {
	static public final String TYPE = "GlpkJni";

	private static final Logger logger = LogManager.getLogger(GlpkJniRelocationSolver.class);

	@Override
	public Collection<Relocation> solve(List<Relocation> candidates) {
		List<Relocation> tripList = new ArrayList<>(candidates);

		int numberOfVehicles = tripList.stream().map(t -> t.vehicle).collect(Collectors.toSet()).size();
		int numberOfDestinations = tripList.stream().map(t -> t.destination).collect(Collectors.toSet()).size();

		int numberOfVariables = tripList.size();
		int numberOfAssignments = Math.min(numberOfVehicles, numberOfDestinations);

		if (numberOfVariables == 0) {
			return Collections.emptySet();
		}

		// Start problem

		glp_prob problem = GLPK.glp_create_prob();
		GLPK.glp_set_prob_name(problem, "Rebalancing");

		// Add variables

		GLPK.glp_add_cols(problem, numberOfVariables);

		for (int i = 0; i < numberOfVariables; i++) {
			GLPK.glp_set_col_kind(problem, i + 1, GLPKConstants.GLP_BV);
			GLPK.glp_set_col_bnds(problem, i + 1, GLPKConstants.GLP_DB, 0.0, 1.0);
			GLPK.glp_set_col_name(problem, i + 1, "y" + i);
		}

		// Add constraint

		GLPK.glp_add_rows(problem, 1);

		SWIGTYPE_p_int variables = GLPK.new_intArray(numberOfVariables + 1);
		SWIGTYPE_p_double values = GLPK.new_doubleArray(numberOfVariables + 1);

		for (int i = 0; i < numberOfVariables; i++) {
			GLPK.intArray_setitem(variables, i + 1, i + 1);
			GLPK.doubleArray_setitem(values, i + 1, 1.0);
		}

		GLPK.glp_set_row_bnds(problem, 1, GLPKConstants.GLP_FX, numberOfAssignments, numberOfAssignments);
		GLPK.glp_set_mat_row(problem, 1, numberOfVariables, variables, values);

		// Add objective

		GLPK.glp_set_obj_name(problem, "S");
		GLPK.glp_set_obj_dir(problem, GLPKConstants.GLP_MIN);

		for (int i = 0; i < numberOfVariables; i++) {
			GLPK.glp_set_obj_coef(problem, i + 1, tripList.get(i).cost);
		}

		// Solve problem

		glp_iocp parameters = new glp_iocp();
		GLPK.glp_init_iocp(parameters);
		parameters.setPresolve(GLPK.GLP_ON);
		GLPK.glp_term_out(GLPK.GLP_OFF);

		GLPK.glp_intopt(problem, parameters);
		int status = GLPK.glp_mip_status(problem);

		List<Relocation> result = new LinkedList<>();

		if (status == GLPK.GLP_FEAS || status == GLPK.GLP_OPT) {
			boolean isNonInteger = false;

			if (status == GLPK.GLP_OPT) {
				for (int i = 0; i < numberOfVariables; i++) {
					if (GLPK.glp_mip_col_val(problem, i + 1) > 0.7) {
						result.add(tripList.get(i));
					} else if (GLPK.glp_mip_col_val(problem, i + 1) > 0.3) {
						isNonInteger = true;
					}
				}
			}

			if (isNonInteger) {
				logger.warn("Non-integer solution found by GLPK");
			}
		} else {
			logger.warn("GLPK problem could not be solved. Status: " + status);
		}

		GLPK.glp_delete_prob(problem);
		return result;
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
