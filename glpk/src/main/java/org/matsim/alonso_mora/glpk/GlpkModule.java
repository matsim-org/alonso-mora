package org.matsim.alonso_mora.glpk;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.MultiModeAlonsoMoraConfigGroup;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.core.config.Config;

import com.google.inject.Singleton;

public class GlpkModule extends AbstractDvrpModeQSimModule {
	private final AlonsoMoraConfigGroup amConfig;

	public GlpkModule(DrtConfigGroup drtConfig, AlonsoMoraConfigGroup amConfig) {
		super(drtConfig.getMode());
		this.amConfig = amConfig;
	}

	static public void configure(Config config) {
		MultiModeAlonsoMoraConfigGroup mmConfig = (MultiModeAlonsoMoraConfigGroup) MultiModeAlonsoMoraConfigGroup
				.get(config);
		mmConfig.getModes().values().forEach(GlpkModule::configure);
	}

	static public void configure(AlonsoMoraConfigGroup amConfig) {
		amConfig.addAssignmentSolverDefinition(GlpkJniAssignmentSolver.TYPE, () -> new GlpkJniAssignmentParameters());
		amConfig.addRelocationSolverDefinition(GlpkJniRelocationSolver.TYPE, () -> new GlpkJniRelocationParameters());
	}

	@Override
	protected void configureQSim() {
		bindModal(GlpkJniAssignmentSolver.class).toProvider(modalProvider(getter -> {
			if (!GlpkJniAssignmentSolver.checkAvailability()) {
				throw new IllegalStateException("GLPK JNI solver is not available on this system!");
			}

			GlpkJniAssignmentParameters solverParameters = (GlpkJniAssignmentParameters) amConfig.assignmentSolver;

			return new GlpkJniAssignmentSolver(amConfig.unassignmentPenalty, amConfig.rejectionPenalty,
					solverParameters.timeLimit, solverParameters.optimalityGap);
		})).in(Singleton.class);

		if (amConfig.assignmentSolver.getSolverType().equals(GlpkJniAssignmentSolver.TYPE)) {
			bindModal(AssignmentSolver.class).to(modalKey(GlpkJniAssignmentSolver.class));
		}

		bindModal(GlpkJniRelocationSolver.class).toProvider(modalProvider(getter -> {
			if (!GlpkJniRelocationSolver.checkAvailability()) {
				throw new IllegalStateException("GLPK JNI solver is not available on this system!");
			}

			return new GlpkJniRelocationSolver();
		})).in(Singleton.class);

		if (amConfig.assignmentSolver.getSolverType().equals(GlpkJniRelocationSolver.TYPE)) {
			bindModal(RelocationSolver.class).to(modalKey(GlpkJniRelocationSolver.class));
		}
	}

}
