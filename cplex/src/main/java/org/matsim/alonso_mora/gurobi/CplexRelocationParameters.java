package org.matsim.alonso_mora.gurobi;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup.RelocationSolverParameters;
import org.matsim.core.config.Config;

import com.google.common.base.Verify;

public class CplexRelocationParameters extends RelocationSolverParameters {
	public CplexRelocationParameters() {
		super(CplexRelocationSolver.TYPE);
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		Verify.verify(CplexAssignmentSolver.checkAvailability());
	}
}
