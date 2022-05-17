package org.matsim.alonso_mora;

import com.google.inject.Singleton;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.*;
import org.matsim.alonso_mora.algorithm.*;
import org.matsim.alonso_mora.algorithm.AlonsoMoraAlgorithm.AlgorithmSettings;
import org.matsim.alonso_mora.algorithm.assignment.*;
import org.matsim.alonso_mora.algorithm.function.AlonsoMoraFunction;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.Constraint;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.MinimumDelay;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.NoopConstraint;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.Objective;
import org.matsim.alonso_mora.algorithm.function.sequence.*;
import org.matsim.alonso_mora.algorithm.relocation.BestResponseRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.CbcMpsRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.GlpkMpsRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;
import org.matsim.alonso_mora.scheduling.AlonsoMoraScheduler;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler.NoopOperationalVoter;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler.OperationalVoter;
import org.matsim.alonso_mora.scheduling.ParallelLeastCostPathCalculator;
import org.matsim.alonso_mora.scheduling.StandardRebalancer;
import org.matsim.alonso_mora.travel_time.*;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.QSimScopeForkJoinPoolHolder;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTaskEndTimeCalculator;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.path.VrpPaths;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater.StayTaskEndTimeCalculator;
import org.matsim.contrib.zone.skims.TravelTimeMatrix;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelTime;

import java.io.File;

import static org.matsim.contrib.dvrp.path.VrpPaths.FIRST_LINK_TT;

/**
 * Registers all components for the Alonso-Mora dispatcher in the MATSim QSim
 * scope.
 */
public class AlonsoMoraModeQSimModule extends AbstractDvrpModeQSimModule {
	private final DrtConfigGroup drtConfig;
	private final AlonsoMoraConfigGroup amConfig;

	public AlonsoMoraModeQSimModule(DrtConfigGroup drtConfig, AlonsoMoraConfigGroup config) {
		super(drtConfig.getMode());
		this.drtConfig = drtConfig;
		this.amConfig = config;
	}

	@Override
	protected void configureQSim() {
		bindModal(SequenceGeneratorFactory.class).toProvider(modalProvider(getter -> {
			switch (amConfig.getSequenceGeneratorType()) {
			case Combined:
				return new CombinedSequenceGenerator.Factory(amConfig.getInsertionStartOccupancy());
			case EuclideanBestResponse:
				return new EuclideanSequenceGenerator.Factory();
			case Extensive:
				return new ExtensiveSequenceGenerator.Factory();
			case Insertive:
				return new InsertiveSequenceGenerator.Factory();
			default:
				throw new IllegalStateException();
			}
		}));

		bindModal(GreedyTripFirstSolver.class).toProvider(modalProvider(getter -> {
			return new GreedyTripFirstSolver();
		})).in(Singleton.class);

		bindModal(GreedyVehicleFirstSolver.class).toProvider(modalProvider(getter -> {
			return new GreedyVehicleFirstSolver();
		})).in(Singleton.class);

		bindModal(CbcMpsAssignmentSolver.class).toProvider(modalProvider(getter -> {
			if (!CbcMpsAssignmentSolver.checkAvailability()) {
				throw new IllegalStateException("Cbc solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			CbcMpsAssignmentParameters solverParameters = (CbcMpsAssignmentParameters) amConfig
					.getAssignmentSolverParameters();

			return new CbcMpsAssignmentSolver(amConfig.getUnassignmentPenalty(), amConfig.getRejectionPenalty(),
					solverParameters.getTimeLimit(), solverParameters.getOptimalityGap(), problemPath,
					solutionPath);
		})).in(Singleton.class);

		bindModal(GlpkMpsAssignmentSolver.class).toProvider(modalProvider(getter -> {
			if (!GlpkMpsAssignmentSolver.checkAvailability()) {
				throw new IllegalStateException("GLPK solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			GlpkMpsAssignmentParameters solverParameters = (GlpkMpsAssignmentParameters) amConfig
					.getAssignmentSolverParameters();

			return new GlpkMpsAssignmentSolver(amConfig.getUnassignmentPenalty(), amConfig.getRejectionPenalty(),
					solverParameters.getTimeLimit(), solverParameters.getOptimalityGap(), problemPath,
					solutionPath);
		})).in(Singleton.class);

		switch (amConfig.getAssignmentSolverParameters().getSolverType()) {
		case GreedyTripFirstSolver.TYPE:
			bindModal(AssignmentSolver.class).to(modalKey(GreedyTripFirstSolver.class));
			break;
		case GreedyVehicleFirstSolver.TYPE:
			bindModal(AssignmentSolver.class).to(modalKey(GreedyVehicleFirstSolver.class));
			break;
		case CbcMpsAssignmentSolver.TYPE:
			bindModal(AssignmentSolver.class).to(modalKey(CbcMpsAssignmentSolver.class));
			break;
		case GlpkMpsAssignmentSolver.TYPE:
			bindModal(AssignmentSolver.class).to(modalKey(GlpkMpsAssignmentSolver.class));
			break;
		}

		bindModal(BestResponseRelocationSolver.class).toProvider(modalProvider(getter -> {
			return new BestResponseRelocationSolver();
		})).in(Singleton.class);

		bindModal(CbcMpsRelocationSolver.class).toProvider(modalProvider(getter -> {
			if (!CbcMpsAssignmentSolver.checkAvailability()) {
				throw new IllegalStateException("Cbc solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			CbcMpsRelocationParameters solverParameters = (CbcMpsRelocationParameters) amConfig
					.getRelocationSolverParameters();

			return new CbcMpsRelocationSolver(solverParameters.getRuntimeThreshold(), problemPath, solutionPath);
		})).in(Singleton.class);

		bindModal(GlpkMpsRelocationSolver.class).toProvider(modalProvider(getter -> {
			if (!GlpkMpsRelocationSolver.checkAvailability()) {
				throw new IllegalStateException("GLPK solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			GlpkMpsRelocationParameters solverParameters = (GlpkMpsRelocationParameters) amConfig
					.getRelocationSolverParameters();

			return new GlpkMpsRelocationSolver(solverParameters.getRuntimeThreshold(), problemPath, solutionPath);
		})).in(Singleton.class);

		switch (amConfig.getRelocationSolverParameters().getSolverType()) {
		case BestResponseRelocationSolver.TYPE:
			bindModal(RelocationSolver.class).to(modalKey(BestResponseRelocationSolver.class));
			break;
		case CbcMpsRelocationSolver.TYPE:
			bindModal(RelocationSolver.class).to(modalKey(CbcMpsRelocationSolver.class));
			break;
		case GlpkMpsRelocationSolver.TYPE:
			bindModal(RelocationSolver.class).to(modalKey(GlpkMpsRelocationSolver.class));
			break;
		}

		bindModal(LeastCostPathCalculatorFactory.class).to(LeastCostPathCalculatorFactory.class);

		addModalComponent(ParallelLeastCostPathCalculator.class, modalProvider(getter -> {
			LeastCostPathCalculatorFactory factory = getter.getModal(LeastCostPathCalculatorFactory.class);
			Network network = getter.getModal(Network.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);

			return new ParallelLeastCostPathCalculator(drtConfig.getNumberOfThreads(), factory, network,
					new OnlyTimeDependentTravelDisutility(travelTime), travelTime);
		}));

		bindModal(LeastCostPathCalculator.class).to(modalKey(ParallelLeastCostPathCalculator.class));

		bindModal(DrtDetourTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			// Copy & paste from DetourTimeEstimator.createMatrixBasedEstimator
			TravelTimeMatrix matrix = getter.getModal(TravelTimeMatrix.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);
			double speedFactor = 1.0;
			
			return new DrtDetourTravelTimeEstimator((from, to, departureTime) -> {
				if (from == to) {
					return 0;
				}
				double duration = FIRST_LINK_TT;
				duration += matrix.getTravelTime(from.getToNode(), to.getFromNode(), departureTime + duration);
				duration += VrpPaths.getLastLinkTT(travelTime, to, departureTime + duration);
				return duration / speedFactor;
			});
		})).in(Singleton.class);

		bindModal(EuclideanTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			EuclideanEstimatorParameters parameters = (EuclideanEstimatorParameters) amConfig
					.getTravelTimeEstimatorParameters();

			return new EuclideanTravelTimeEstimator(parameters.getEuclideanDistanceFactor(),
					parameters.getEuclideanSpeed() / 3.6);
		})).in(Singleton.class);

		bindModal(RoutingTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			RoutingEstimatorParameters parameters = (RoutingEstimatorParameters) amConfig
					.getTravelTimeEstimatorParameters();

			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);
			MobsimTimer mobsimTimer = getter.get(MobsimTimer.class);

			return new RoutingTravelTimeEstimator(mobsimTimer, router, travelTime, parameters.getCacheLifetime());
		})).in(Singleton.class);

		bindModal(HybridTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			HybridEstimatorParameters parameters = (HybridEstimatorParameters) amConfig
					.getTravelTimeEstimatorParameters();

			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);
			MobsimTimer mobsimTimer = getter.get(MobsimTimer.class);

			return new HybridTravelTimeEstimator(
					new RoutingTravelTimeEstimator(mobsimTimer, router, travelTime, parameters.getCacheLifetime()),
					new EuclideanTravelTimeEstimator(parameters.getEuclideanDistanceFactor(),
							parameters.getEuclideanSpeed() / 3.6));
		})).in(Singleton.class);

		bindModal(MatrixTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			Network network = getter.getModal(Network.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);

			// TODO: Parametrize time
			return MatrixTravelTimeEstimator.create(network, travelTime, 8.5 * 3600.0);
		})).in(Singleton.class);

		bindModal(LazyMatrixTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			Network network = getter.getModal(Network.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);

			// TODO: Parametrize time
			return LazyMatrixTravelTimeEstimator.create(network, travelTime, new DijkstraFactory(), 8.5 * 3600.0);
		})).in(Singleton.class);

		switch (amConfig.getTravelTimeEstimatorParameters().getEstimatorType()) {
		case DrtDetourTravelTimeEstimator.TYPE:
			bindModal(TravelTimeEstimator.class).to(modalKey(DrtDetourTravelTimeEstimator.class));
			break;
		case EuclideanTravelTimeEstimator.TYPE:
			bindModal(TravelTimeEstimator.class).to(modalKey(EuclideanTravelTimeEstimator.class));
			break;
		case HybridTravelTimeEstimator.TYPE:
			bindModal(TravelTimeEstimator.class).to(modalKey(HybridTravelTimeEstimator.class));
			break;
		case MatrixTravelTimeEstimator.TYPE:
			MatrixEstimatorParameters estimatorParameters = (MatrixEstimatorParameters) amConfig
					.getTravelTimeEstimatorParameters();
			bindModal(TravelTimeEstimator.class)
					.to(estimatorParameters.isLazy() ? modalKey(LazyMatrixTravelTimeEstimator.class)
							: modalKey(MatrixTravelTimeEstimator.class));
			break;
		case RoutingTravelTimeEstimator.TYPE:
			bindModal(TravelTimeEstimator.class).to(modalKey(RoutingTravelTimeEstimator.class));
			break;
		}

		bindModal(AlonsoMoraFunction.class).toProvider(modalProvider(getter -> {
			TravelTimeEstimator travelTimeEstimator = getter.getModal(TravelTimeEstimator.class);
			SequenceGeneratorFactory sequenceGeneratorFactory = getter.getModal(SequenceGeneratorFactory.class);
			Objective objective = getter.getModal(Objective.class);
			Constraint constraint = getter.getModal(Constraint.class);

			CongestionMitigationParameters congestionParameters = amConfig.getCongestionMitigationParameters();

			return new DefaultAlonsoMoraFunction(travelTimeEstimator, sequenceGeneratorFactory,
					drtConfig.getStopDuration(), congestionParameters.getAllowPickupViolations(),
					congestionParameters.getAllowPickupsWithDropoffViolations(),
					amConfig.getCheckDeterminsticTravelTimes(), objective, constraint, amConfig.getViolationFactor(),
					amConfig.getViolationOffset(), amConfig.getPreferNonViolation());
		}));

		bindModal(Objective.class).toProvider(() -> new MinimumDelay());
		bindModal(Constraint.class).toInstance(new NoopConstraint());

		bindModal(StayTaskEndTimeCalculator.class).toProvider(modalProvider(getter -> {
			return new DrtStayTaskEndTimeCalculator((dvrpVehicle, collection, collection1) -> drtConfig.getStopDuration());
		}));

		bindModal(AlonsoMoraScheduler.class).toProvider(modalProvider(getter -> {
			StayTaskEndTimeCalculator endTimeCalculator = getter.getModal(StayTaskEndTimeCalculator.class);
			DrtTaskFactory taskFactory = getter.getModal(DrtTaskFactory.class);
			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);

			TravelTime travelTime = getter.getModal(TravelTime.class);
			Network network = getter.getModal(Network.class);

			OperationalVoter operationalVoter = getter.getModal(OperationalVoter.class);

			return new DefaultAlonsoMoraScheduler(taskFactory, drtConfig.getStopDuration(),
					amConfig.getCheckDeterminsticTravelTimes(), amConfig.getRerouteDuringScheduling(), travelTime,
					network, endTimeCalculator, router, operationalVoter);
		}));

		bindModal(OperationalVoter.class).toInstance(new NoopOperationalVoter());

		addModalComponent(StandardRebalancer.class, modalProvider(getter -> {
			Double rebalancingInterval = null;

			if (drtConfig.getRebalancingParams().isPresent()) {
				rebalancingInterval = (double) drtConfig.getRebalancingParams().get().getInterval();
			}

			return new StandardRebalancer( //
					getter.getModal(RebalancingStrategy.class), //
					getter.get(MobsimTimer.class), //
					getter.getModal(DrtScheduleInquiry.class), //
					getter.getModal(Fleet.class), //
					getter.getModal(EmptyVehicleRelocator.class), //
					rebalancingInterval //
			);
		}));

		bindModal(AlonsoMoraAlgorithm.class).toProvider(modalProvider(getter -> {
			StandardRebalancer standardRebalancer = getter.getModal(StandardRebalancer.class);

			if (amConfig.getRelocationInterval() > 0 && standardRebalancer.isActive()) {
				throw new IllegalStateException(
						"If a DRT rebalancing strategy is defined, you have to set useInternalRebalancing=false for the Alonso Mora dispatcher.");
			}

			return new AlonsoMoraAlgorithm( //
					getter.getModal(Fleet.class), //
					getter.getModal(AssignmentSolver.class), //
					getter.getModal(RelocationSolver.class), //
					getter.getModal(AlonsoMoraFunction.class), //
					getter.getModal(AlonsoMoraScheduler.class), //
					getter.get(EventsManager.class), //
					drtConfig.getMode(), //
					getter.getModal(AlonsoMoraVehicleFactory.class), //
					getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool(), //
					getter.getModal(TravelTimeEstimator.class), //
					drtConfig.getStopDuration(), //
					new AlgorithmSettings(amConfig));
		}));

		bindModal(AlonsoMoraVehicleFactory.class).toInstance(vehicle -> new DefaultAlonsoMoraVehicle(vehicle));

		bindModal(AlonsoMoraRequestFactory.class).toProvider(modalProvider(getter -> {
			return new DefaultAlonsoMoraRequestFactory(amConfig.getMaximumQueueTime());
		}));

		bindModal(AlonsoMoraOptimizer.class).toProvider(modalProvider(getter -> {
			return new AlonsoMoraOptimizer(getter.getModal(AlonsoMoraAlgorithm.class),
					getter.getModal(AlonsoMoraRequestFactory.class), //
					getter.getModal(ScheduleTimingUpdater.class), //
					getter.getModal(Fleet.class), //
					amConfig.getAssignmentInterval(), //
					amConfig.getMaximumGroupRequestSize(), //
					getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool(), //
					getter.getModal(LeastCostPathCalculator.class), //
					getter.getModal(TravelTime.class), //
					drtConfig.getAdvanceRequestPlanningHorizon(), //
					getter.getModal(InformationCollector.class) //
			);
		}));

		bindModal(AlonsoMoraConfigGroup.class).toInstance(amConfig);
		addModalComponent(DrtOptimizer.class, modalKey(AlonsoMoraOptimizer.class));
	}
}
