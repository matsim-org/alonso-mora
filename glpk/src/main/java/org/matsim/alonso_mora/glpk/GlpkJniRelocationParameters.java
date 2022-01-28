package org.matsim.alonso_mora.glpk;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup.RelocationSolverParameters;
import org.matsim.core.config.Config;

import com.google.common.base.Verify;

public class GlpkJniRelocationParameters extends RelocationSolverParameters {
	public GlpkJniRelocationParameters() {
		super(GlpkJniRelocationSolver.TYPE);
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		Verify.verify(GlpkJniAssignmentSolver.checkAvailability());
	}
}
