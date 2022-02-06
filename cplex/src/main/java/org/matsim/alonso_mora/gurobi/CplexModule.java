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

public class CplexModule extends AbstractDvrpModeQSimModule {
	private final AlonsoMoraConfigGroup amConfig;

	public CplexModule(DrtConfigGroup drtConfig, AlonsoMoraConfigGroup amConfig) {
		super(drtConfig.getMode());
		this.amConfig = amConfig;
	}

	static public void configure(Config config) {
		MultiModeAlonsoMoraConfigGroup mmConfig = (MultiModeAlonsoMoraConfigGroup) MultiModeAlonsoMoraConfigGroup
				.get(config);
		mmConfig.getModes().values().forEach(CplexModule::configure);
	}

	static public void configure(AlonsoMoraConfigGroup amConfig) {
		amConfig.addAssignmentSolverDefinition(CplexAssignmentSolver.TYPE, () -> new CplexAssignmentParameters());
		amConfig.addRelocationSolverDefinition(CplexRelocationSolver.TYPE, () -> new CplexRelocationParameters());
	}

	@Override
	protected void configureQSim() {
		bindModal(CplexAssignmentSolver.class).toProvider(modalProvider(getter -> {
			GlobalConfigGroup globalConfig = getter.get(GlobalConfigGroup.class);

			CplexAssignmentParameters solverParameters = (CplexAssignmentParameters) amConfig
					.getAssignmentSolverParameters();

			return new CplexAssignmentSolver(amConfig.getUnassignmentPenalty(), amConfig.getRejectionPenalty(),
					globalConfig.getNumberOfThreads(), solverParameters.getRuntimeThreshold());
		})).in(Singleton.class);

		if (amConfig.getAssignmentSolverParameters().getSolverType().equals(CplexAssignmentSolver.TYPE)) {
			bindModal(AssignmentSolver.class).to(modalKey(CplexAssignmentSolver.class));
		}

		bindModal(CplexRelocationSolver.class).toProvider(modalProvider(getter -> {
			GlobalConfigGroup globalConfig = getter.get(GlobalConfigGroup.class);

			return new CplexRelocationSolver(globalConfig.getNumberOfThreads());
		})).in(Singleton.class);

		if (amConfig.getRelocationSolverParameters().getSolverType().equals(CplexRelocationSolver.TYPE)) {
			bindModal(RelocationSolver.class).to(modalKey(CplexRelocationSolver.class));
		}
	}

}
