package org.matsim.alonso_mora.gurobi;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup.AssignmentSolverParameters;
import org.matsim.core.config.Config;

import com.google.common.base.Verify;

public class GurobiAssignmentParameters extends AssignmentSolverParameters {
	public GurobiAssignmentParameters() {
		super(GurobiAssignmentSolver.TYPE);
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		Verify.verify(GurobiAssignmentSolver.checkAvailability());
	}
}
