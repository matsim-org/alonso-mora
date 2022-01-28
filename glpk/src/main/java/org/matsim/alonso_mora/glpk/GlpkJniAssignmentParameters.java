package org.matsim.alonso_mora.glpk;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup.AssignmentSolverParameters;
import org.matsim.core.config.Config;

import com.google.common.base.Verify;

public class GlpkJniAssignmentParameters extends AssignmentSolverParameters {
	public GlpkJniAssignmentParameters() {
		super(GlpkJniAssignmentSolver.TYPE);
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		Verify.verify(GlpkJniAssignmentSolver.checkAvailability());
	}
}
