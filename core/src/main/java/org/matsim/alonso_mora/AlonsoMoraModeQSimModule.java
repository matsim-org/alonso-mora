package org.matsim.alonso_mora;

import static org.matsim.contrib.dvrp.path.VrpPaths.FIRST_LINK_TT;

import java.io.File;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup.CbcMpsAssignmentParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.CbcMpsRelocationParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.CongestionMitigationParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.EuclideanEstimatorParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.GlpkMpsAssignmentParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.GlpkMpsRelocationParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.HybridEstimatorParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.MatrixEstimatorParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.RoutingEstimatorParameters;
import org.matsim.alonso_mora.algorithm.AlonsoMoraAlgorithm;
import org.matsim.alonso_mora.algorithm.AlonsoMoraAlgorithm.AlgorithmSettings;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequestFactory;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicleFactory;
import org.matsim.alonso_mora.algorithm.DefaultAlonsoMoraRequestFactory;
import org.matsim.alonso_mora.algorithm.DefaultAlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.DefaultItemsProvider;
import org.matsim.alonso_mora.algorithm.ItemsProvider;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.DefaultRejectionPenalty;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.RejectionPenalty;
import org.matsim.alonso_mora.algorithm.assignment.CbcMpsAssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.GlpkMpsAssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.GreedyTripFirstSolver;
import org.matsim.alonso_mora.algorithm.assignment.GreedyVehicleFirstSolver;
import org.matsim.alonso_mora.algorithm.function.AlonsoMoraFunction;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.Constraint;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.MinimumDelay;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.NoopConstraint;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.Objective;
import org.matsim.alonso_mora.algorithm.function.sequence.CombinedSequenceGenerator;
import org.matsim.alonso_mora.algorithm.function.sequence.EuclideanSequenceGenerator;
import org.matsim.alonso_mora.algorithm.function.sequence.ExtensiveSequenceGenerator;
import org.matsim.alonso_mora.algorithm.function.sequence.InsertiveSequenceGenerator;
import org.matsim.alonso_mora.algorithm.function.sequence.SequenceGeneratorFactory;
import org.matsim.alonso_mora.algorithm.relocation.BestResponseRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.CbcMpsRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.GlpkMpsRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.NoopRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver;
import org.matsim.alonso_mora.scheduling.AlonsoMoraScheduler;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler.NoopOperationalVoter;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler.OperationalVoter;
import org.matsim.alonso_mora.scheduling.ParallelLeastCostPathCalculator;
import org.matsim.alonso_mora.scheduling.StandardRebalancer;
import org.matsim.alonso_mora.travel_time.DrtDetourTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.EuclideanTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.HybridTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.LazyMatrixTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.MatrixTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.RoutingTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.TravelTimeEstimator;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.optimizer.QSimScopeForkJoinPoolHolder;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.passenger.DrtOfferAcceptor;
import org.matsim.contrib.drt.prebooking.unscheduler.RequestUnscheduler;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTaskEndTimeCalculator;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.scheduler.DrtScheduleInquiry;
import org.matsim.contrib.drt.scheduler.EmptyVehicleRelocator;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;
import org.matsim.contrib.drt.stops.StopTimeCalculator;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.load.DvrpLoadType;
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

import com.google.inject.Singleton;

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
			switch (amConfig.sequenceGeneratorType) {
			case Combined:
				return new CombinedSequenceGenerator.Factory(amConfig.insertionStartOccupancy);
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

		bindModal(RejectionPenalty.class)
				.toInstance(new DefaultRejectionPenalty(amConfig.unassignmentPenalty, amConfig.rejectionPenalty));

		bindModal(CbcMpsAssignmentSolver.class).toProvider(modalProvider(getter -> {
			if (!CbcMpsAssignmentSolver.checkAvailability()) {
				throw new IllegalStateException("Cbc solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			CbcMpsAssignmentParameters solverParameters = (CbcMpsAssignmentParameters) amConfig.assignmentSolver;

			return new CbcMpsAssignmentSolver(getter.getModal(RejectionPenalty.class), solverParameters.timeLimit,
					solverParameters.optimalityGap, problemPath, solutionPath, getConfig().global().getRandomSeed());
		})).in(Singleton.class);

		bindModal(GlpkMpsAssignmentSolver.class).toProvider(modalProvider(getter -> {
			if (!GlpkMpsAssignmentSolver.checkAvailability()) {
				throw new IllegalStateException("GLPK solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			GlpkMpsAssignmentParameters solverParameters = (GlpkMpsAssignmentParameters) amConfig.assignmentSolver;

			return new GlpkMpsAssignmentSolver(getter.getModal(RejectionPenalty.class), solverParameters.timeLimit,
					solverParameters.optimalityGap, problemPath, solutionPath);
		})).in(Singleton.class);

		switch (amConfig.assignmentSolver.getSolverType()) {
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

		bindModal(NoopRelocationSolver.class).toProvider(modalProvider(getter -> {
			return new NoopRelocationSolver();
		})).in(Singleton.class);

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

			CbcMpsRelocationParameters solverParameters = (CbcMpsRelocationParameters) amConfig.relocationSolver;

			return new CbcMpsRelocationSolver(solverParameters.runtimeThreshold, problemPath, solutionPath,
					getConfig().global().getRandomSeed());
		})).in(Singleton.class);

		bindModal(GlpkMpsRelocationSolver.class).toProvider(modalProvider(getter -> {
			if (!GlpkMpsRelocationSolver.checkAvailability()) {
				throw new IllegalStateException("GLPK solver is not available on this system!");
			}

			OutputDirectoryHierarchy outputHierarchy = getter.get(OutputDirectoryHierarchy.class);
			File problemPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.mps");
			File solutionPath = new File(outputHierarchy.getTempPath(), "alonso_mora_assignment.sol");

			GlpkMpsRelocationParameters solverParameters = (GlpkMpsRelocationParameters) amConfig.relocationSolver;

			return new GlpkMpsRelocationSolver(solverParameters.runtimeThreshold, problemPath, solutionPath);
		})).in(Singleton.class);

		if (amConfig.relocationSolver != null) {
			switch (amConfig.relocationSolver.getSolverType()) {
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
		} else {
			bindModal(RelocationSolver.class).to(modalKey(NoopRelocationSolver.class));
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
			EuclideanEstimatorParameters parameters = (EuclideanEstimatorParameters) amConfig.travelTimeEstimator;

			return new EuclideanTravelTimeEstimator(parameters.euclideanDistanceFactor,
					parameters.euclideanSpeed / 3.6);
		})).in(Singleton.class);

		bindModal(RoutingTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			RoutingEstimatorParameters parameters = (RoutingEstimatorParameters) amConfig.travelTimeEstimator;

			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);
			MobsimTimer mobsimTimer = getter.get(MobsimTimer.class);

			return new RoutingTravelTimeEstimator(mobsimTimer, router, travelTime, parameters.cacheLifetime);
		})).in(Singleton.class);

		bindModal(HybridTravelTimeEstimator.class).toProvider(modalProvider(getter -> {
			HybridEstimatorParameters parameters = (HybridEstimatorParameters) amConfig.travelTimeEstimator;

			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);
			TravelTime travelTime = getter.getModal(TravelTime.class);
			MobsimTimer mobsimTimer = getter.get(MobsimTimer.class);

			return new HybridTravelTimeEstimator(
					new RoutingTravelTimeEstimator(mobsimTimer, router, travelTime, parameters.cacheLifetime),
					new EuclideanTravelTimeEstimator(parameters.euclideanDistanceFactor,
							parameters.euclideanSpeed / 3.6));
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

		switch (amConfig.travelTimeEstimator.getEstimatorType()) {
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
			MatrixEstimatorParameters estimatorParameters = (MatrixEstimatorParameters) amConfig.travelTimeEstimator;
			bindModal(TravelTimeEstimator.class)
					.to(estimatorParameters.lazy ? modalKey(LazyMatrixTravelTimeEstimator.class)
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
			PassengerStopDurationProvider stopDurationProvider = getter.getModal(PassengerStopDurationProvider.class);

			CongestionMitigationParameters congestionParameters = amConfig.congestionMitigation;

			return new DefaultAlonsoMoraFunction(travelTimeEstimator, sequenceGeneratorFactory, stopDurationProvider,
					drtConfig.getStopDuration(), congestionParameters.allowPickupViolations,
					congestionParameters.allowPickupsWithDropoffViolations, amConfig.checkDeterminsticTravelTimes,
					objective, constraint, amConfig.violationFactor, amConfig.violationOffset,
					amConfig.preferNonViolation, getter.getModal(DvrpLoadType.class));
		}));

		bindModal(Objective.class).toProvider(() -> new MinimumDelay());
		bindModal(Constraint.class).toInstance(new NoopConstraint());

		bindModal(StayTaskEndTimeCalculator.class).toProvider(modalProvider(getter -> {
			return new DrtStayTaskEndTimeCalculator(getter.getModal(StopTimeCalculator.class));
		}));

		bindModal(AlonsoMoraScheduler.class).toProvider(modalProvider(getter -> {
			StayTaskEndTimeCalculator endTimeCalculator = getter.getModal(StayTaskEndTimeCalculator.class);
			DrtTaskFactory taskFactory = getter.getModal(DrtTaskFactory.class);
			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);

			TravelTime travelTime = getter.getModal(TravelTime.class);
			Network network = getter.getModal(Network.class);

			OperationalVoter operationalVoter = getter.getModal(OperationalVoter.class);
			PassengerStopDurationProvider stopDurationProvider = getter.getModal(PassengerStopDurationProvider.class);

			return new DefaultAlonsoMoraScheduler(taskFactory, stopDurationProvider, drtConfig.getStopDuration(),
					amConfig.checkDeterminsticTravelTimes, amConfig.rerouteDuringScheduling, travelTime, network,
					endTimeCalculator, router, operationalVoter);
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

			if ((amConfig.relocationInterval > 0 && amConfig.relocationSolver != null)
					&& standardRebalancer.isActive()) {
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
					getter.getModal(PassengerStopDurationProvider.class), //
					new AlgorithmSettings(amConfig), //
					getter.getModal(DrtOfferAcceptor.class), //
					drtConfig.getStopDuration(), //
					getter.getModal(DvrpLoadType.class));
		}));

		bindModal(AlonsoMoraVehicleFactory.class).toProvider(modalProvider(getter -> {
			ItemsProvider itemsProvider = getter.getModal(ItemsProvider.class);
			return vehicle -> new DefaultAlonsoMoraVehicle(
				vehicle, itemsProvider.getItems(vehicle.getCapacity()));
		}));

		bindModal(AlonsoMoraRequestFactory.class).toProvider(modalProvider(getter -> {
			return new DefaultAlonsoMoraRequestFactory(amConfig.maximumQueueTime, //
				getter.getModal(DvrpLoadType.class), //
				getter.getModal(ItemsProvider.class));
		}));

		bindModal(AlonsoMoraOptimizer.class).toProvider(modalProvider(getter -> {
			return new AlonsoMoraOptimizer(getter.getModal(AlonsoMoraAlgorithm.class),
					getter.getModal(AlonsoMoraRequestFactory.class), //
					getter.getModal(ScheduleTimingUpdater.class), //
					getter.getModal(Fleet.class), //
					amConfig.assignmentInterval, //
					getter.getModal(QSimScopeForkJoinPoolHolder.class).getPool(), //
					getter.getModal(LeastCostPathCalculator.class), //
					getter.getModal(TravelTime.class), //
					getter.getModal(InformationCollector.class) //
			);
		}));

		bindModal(AlonsoMoraConfigGroup.class).toInstance(amConfig);
		addModalComponent(DrtOptimizer.class, modalKey(AlonsoMoraOptimizer.class));

		bindModal(AlonsoMoraOfferAcceptor.class).toInstance(new AlonsoMoraOfferAcceptor());
		bindModal(DrtOfferAcceptor.class).to(modalKey(AlonsoMoraOfferAcceptor.class));

		bindModal(AlonsoMoraUnscheduler.class).toInstance(new AlonsoMoraUnscheduler());
		bindModal(RequestUnscheduler.class).to(modalKey(AlonsoMoraUnscheduler.class));

		bindModal(DefaultItemsProvider.class).toProvider(modalProvider(getter -> {
			return new DefaultItemsProvider(getter.getModal(DvrpLoadType.class));
		})).in(Singleton.class);

		bindModal(ItemsProvider.class).to(modalKey(DefaultItemsProvider.class));
	}
}
