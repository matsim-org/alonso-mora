package org.matsim.alonso_mora.gurobi;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.MultiModeAlonsoMoraConfigGroup;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.GlobalConfigGroup;

import com.google.inject.Singleton;

public class GurobiModule extends AbstractDvrpModeQSimModule {
	private final AlonsoMoraConfigGroup amConfig;

	public GurobiModule(DrtConfigGroup drtConfig, AlonsoMoraConfigGroup amConfig) {
		super(drtConfig.getMode());
		this.amConfig = amConfig;
	}

	static public void configure(Config config) {
		MultiModeAlonsoMoraConfigGroup mmConfig = (MultiModeAlonsoMoraConfigGroup) MultiModeAlonsoMoraConfigGroup
				.get(config);
		mmConfig.getModes().values().forEach(GurobiModule::configure);
	}

	static public void configure(AlonsoMoraConfigGroup amConfig) {
		amConfig.addAssignmentSolverDefinition(GurobiAssignmentSolver.TYPE, () -> new GurobiAssignmentParameters());
		amConfig.addRelocationSolverDefinition(GurobiRelocationSolver.TYPE, () -> new GurobiRelocationParameters());
	}

	@Override
	protected void configureQSim() {
		bindModal(GurobiAssignmentSolver.class).toProvider(modalProvider(getter -> {
			GlobalConfigGroup globalConfig = getter.get(GlobalConfigGroup.class);

			GurobiAssignmentParameters solverParameters = (GurobiAssignmentParameters) amConfig
					.getAssignmentSolverParameters();

			return new GurobiAssignmentSolver(amConfig.getUnassignmentPenalty(), amConfig.getRejectionPenalty(),
					globalConfig.getNumberOfThreads(), solverParameters.getRuntimeThreshold() * 1e-3);
		})).in(Singleton.class);

		if (amConfig.getAssignmentSolverParameters().getSolverType().equals(GurobiAssignmentSolver.TYPE)) {
			bindModal(AssignmentSolver.class).to(modalKey(GurobiAssignmentSolver.class));
		}

		bindModal(GurobiRelocationSolver.class).toProvider(modalProvider(getter -> {
			GlobalConfigGroup globalConfig = getter.get(GlobalConfigGroup.class);

			return new GurobiRelocationSolver(globalConfig.getNumberOfThreads());
		})).in(Singleton.class);

		if (amConfig.getRelocationSolverParameters().getSolverType().equals(GurobiRelocationSolver.TYPE)) {
			bindModal(RelocationSolver.class).to(modalKey(GurobiRelocationSolver.class));
		}
	}

}
