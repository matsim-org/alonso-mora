package org.matsim.alonso_mora;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.matsim.alonso_mora.algorithm.assignment.CbcMpsAssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.GlpkMpsAssignmentSolver;
import org.matsim.alonso_mora.algorithm.assignment.GreedyTripFirstSolver;
import org.matsim.alonso_mora.algorithm.assignment.GreedyVehicleFirstSolver;
import org.matsim.alonso_mora.algorithm.relocation.BestResponseRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.CbcMpsRelocationSolver;
import org.matsim.alonso_mora.algorithm.relocation.GlpkMpsRelocationSolver;
import org.matsim.alonso_mora.travel_time.DrtDetourTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.EuclideanTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.HybridTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.MatrixTravelTimeEstimator;
import org.matsim.alonso_mora.travel_time.RoutingTravelTimeEstimator;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.util.ReflectiveConfigGroupWithConfigurableParameterSets;
import org.matsim.core.config.Config;
import org.matsim.core.config.ReflectiveConfigGroup;

import com.google.common.base.Verify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Config group for the dispatching extension of DRT including the algorithm by
 * Alonso-Mora et al.
 */
public class AlonsoMoraConfigGroup extends ReflectiveConfigGroupWithConfigurableParameterSets {
	public final static String GROUP_NAME = "drtAlonsoMora";

	public AlonsoMoraConfigGroup() {
		super(GROUP_NAME);

		prepareAvailableComponents();
		prepareDefaultComponents();
	}

	/* Integration */
	@Parameter
	@Comment("The DRT mode that will use the Alonso-Mora algorithm")
	@NotBlank
	public String mode = "drt";

	/* General */

	@Parameter
	@Comment("Frequency of logging current status of the dispatcher in [s]")
	@PositiveOrZero
	public double loggingInterval = 600;

	@Parameter
	@Comment("Maximum time the request stays in the dispatching queue after its earliest departure time (submission without prebooking) in [s]. Note that this is capped by the latest pickup time. A value of zero means that requests need to be matched in the dispatching step that comes right after submission / earliest departure time.")
	@PositiveOrZero
	public double maximumQueueTime = 0.0;

	@Parameter
	@Comment("Under ideal and correctly configured freeflow conditions, the algorithm will predict exactly what the vehicles will do in simulation. If this flag is enabled, the algorithm will perform self-checks to verify that this is the case. Use to verify your freeflow condiditons.")
	public boolean checkDeterminsticTravelTimes = false;

	/* Scheduling */

	@Parameter
	@Comment("During scheduling of the pickups and dropoffs we may find situations in which the vehicle is already on the way to its next destination on the current drive task, so not rerouting is necessary. However, it may be wanted if traffic conditions change frequently. This flag will enable rerouting for already pre-routed segments of the schedule.")
	public boolean rerouteDuringScheduling = false;

	/* Sequence generator */

	public enum SequenceGeneratorType {
		Extensive, Insertive, Combined, EuclideanBestResponse
	}

	@Parameter
	@Comment("Defines which sequence generator to use: Extensive (trying to find all arrangements of pickups and dropoff for a route), Insertive (inserting new pickups and dropoffs in the existing order along a vehicle's route), Combined (Extensive below insertionStartOccupancy, Insertive after), EuclideanBestResponse (as a very fast test generator based on stepwise adding the closest pickup and dropoff by Euclidean distnace).")
	public SequenceGeneratorType sequenceGeneratorType = SequenceGeneratorType.Combined;

	@Parameter
	@Comment("Defines the occupany at which the Combined sequence generator will switch from Extensive to Insertive mode.")
	@PositiveOrZero
	public int insertionStartOccupancy = 5;

	@Parameter
	@Comment("Limits the number of request-vehicle combinations that are explored when building the trip graph (III.C in paper). If set to 0, no limit is imposed.")
	@PositiveOrZero
	public int candidateVehiclesPerRequest = 30;

	/* Congestion mitigation */

	static public class CongestionMitigationParameters extends ReflectiveConfigGroupWithConfigurableParameterSets {
		static public final String SET_NAME = "congestionMitigation";

		public CongestionMitigationParameters() {
			super(SET_NAME);
		}

		@Parameter
		@Comment("In some dispatching steps no new request have arrived, so no reassignment is necessary. However, if congestion is involved one might want to perform a reassignment to react to changed traffic conditions.")
		public boolean allowBareReassignment = false;

		@Parameter
		@Comment("Keep current assignments of a vehicle in the shareability graph also they might otherwise be filtered out due to changed traffic conditions.")
		public boolean preserveVehicleAssignments = true;

		@Parameter
		@Comment("Allows that a request that is already assigned to the current vehicle can violate the pickup constraint if it is caused by traffic.")
		public boolean allowPickupViolations = true;

		@Parameter
		@Comment("Allows that new pickups can be integrated into a vehicle although some requests already have dropoff violations due to congestion.")
		public boolean allowPickupsWithDropoffViolations = true;
	}

	/* Violations */

	@Parameter
	@Comment("Violations (for pickup and dropoff) are initially expressed in seconds. This factor is added to the violations to arrive at the final value.")
	public double violationFactor = 60.0;

	@Parameter
	@Comment("Constant value that is added to each solution that has any violations.")
	public double violationOffset = 10000.0;

	@Parameter
	@Comment("Always prefer solutions without violations, even if a solution with violations and lower objective has been found.")
	public boolean preferNonViolation = false;

	/* Assignment */

	@Parameter
	@Comment("The frequency with which assignment is performed")
	@PositiveOrZero
	public double assignmentInterval = 30;

	@Parameter
	@Comment("Penalty in the ILP problem that is added for rejecting requests (before they have been assigned)")
	@PositiveOrZero
	public double rejectionPenalty = 24.0 * 3600.0;

	@Parameter
	@Comment("Penalty in the ILP problem that is added when not assigning and already assigned requests")
	@PositiveOrZero
	public double unassignmentPenalty = 24.0 * 3600.0 * 1000;

	/* Relocation */

	@Parameter
	@Comment("The frequency with which relocation is performed")
	@PositiveOrZero
	public double relocationInterval = 30;

	@Parameter
	@Comment("Defines whether vehicles that are already relocating can be used for relocation in the next relocation step")
	public boolean useBindingRelocations = false;

	@Parameter
	@Comment("If enabled, vehicles are stopped that are currently relocating but are not assigned a new relocation destination. If false they will finish their previous relocation if not assigned anoter one.")
	public boolean useStepwiseRelocation = false;

	/* Graph limits */

	@Parameter
	@Comment("Limits the total number of edges in the trip-vehicle graph per vehicle (0 = no limit)")
	@PositiveOrZero
	public int tripGraphLimitPerVehicle = 0;

	@Parameter
	@Comment("Limits the total number of edges in the trip-vehicle graph for each occupancy level per vehicle (0 = no limit)")
	@PositiveOrZero
	public int tripGraphLimitPerSequenceLength = 0;

	/* Block handling */

	public static class AssignmentSolverParameters extends ReflectiveConfigGroupWithConfigurableParameterSets {
		static public final String SET_PREFIX = "assignmentSolver:";

		public final String solverType;

		public AssignmentSolverParameters(String solverType) {
			super(SET_PREFIX + solverType);
			this.solverType = solverType;
		}

		public String getSolverType() {
			return solverType;
		}

		@Parameter
		@Comment("Defines the runtime threshold of the assignment algorithm [s]")
		public double timeLimit = 15;

		@Parameter
		@Comment("Defines the optimality gap for the algorithm")
		public double optimalityGap = 0.1;
	}

	public static class RelocationSolverParameters extends ReflectiveConfigGroupWithConfigurableParameterSets {
		static public final String SET_PREFIX = "relocationSolver:";

		public final String solverType;

		public RelocationSolverParameters(String solverType) {
			super(SET_PREFIX + solverType);
			this.solverType = solverType;
		}

		public String getSolverType() {
			return solverType;
		}

		@Parameter
		@Comment("Defines the runtime threshold of the assignment algorithm [ms]")
		public int runtimeThreshold = 3600 * 1000;
	}

	public static class GlpkMpsRelocationParameters extends RelocationSolverParameters {
		public GlpkMpsRelocationParameters() {
			super(GlpkMpsRelocationSolver.TYPE);
		}
	}

	public static class CbcMpsRelocationParameters extends RelocationSolverParameters {
		public CbcMpsRelocationParameters() {
			super(CbcMpsRelocationSolver.TYPE);
		}
	}

	public static class TravelTimeEstimatorParameters extends ReflectiveConfigGroup {
		static public final String SET_PREFIX = "travelTimeEstimator:";

		public final String estimatorType;

		public TravelTimeEstimatorParameters(String estimatorType) {
			super(SET_PREFIX + estimatorType);
			this.estimatorType = estimatorType;
		}

		public String getEstimatorType() {
			return estimatorType;
		}
	}

	public AssignmentSolverParameters assignmentSolver;
	public RelocationSolverParameters relocationSolver;
	public TravelTimeEstimatorParameters travelTimeEstimator;
	public CongestionMitigationParameters congestionMitigation;

	public void addAssignmentSolverDefinition(String solverType, Supplier<AssignmentSolverParameters> creator) {
		addDefinition(AssignmentSolverParameters.SET_PREFIX + solverType, creator, () -> assignmentSolver,
				params -> assignmentSolver = (AssignmentSolverParameters) params);
	}

	public void addRelocationSolverDefinition(String solverType, Supplier<RelocationSolverParameters> creator) {
		addDefinition(RelocationSolverParameters.SET_PREFIX + solverType, creator, () -> relocationSolver,
				params -> relocationSolver = (RelocationSolverParameters) params);
	}

	public void addTravelTimeEstimatorDefinition(String estimatorType,
			Supplier<TravelTimeEstimatorParameters> creator) {
		addDefinition(TravelTimeEstimatorParameters.SET_PREFIX + estimatorType, creator, () -> travelTimeEstimator,
				params -> travelTimeEstimator = (TravelTimeEstimatorParameters) params);
	}

	public final Map<String, Supplier<AssignmentSolverParameters>> availableAssignmentSolvers = new HashMap<>();
	public final Map<String, Supplier<RelocationSolverParameters>> availableRelocationSolvers = new HashMap<>();
	public final Map<String, Supplier<TravelTimeEstimatorParameters>> availableTravelTimeEstimators = new HashMap<>();

	public void prepareAvailableComponents() {
		addAssignmentSolverDefinition(GreedyTripFirstSolver.TYPE, GreedyTripFirstAssignmentParameters::new);
		addAssignmentSolverDefinition(GreedyVehicleFirstSolver.TYPE, GreedyVehicleFirstAssignmentParameters::new);
		addAssignmentSolverDefinition(CbcMpsAssignmentSolver.TYPE, CbcMpsAssignmentParameters::new);
		addAssignmentSolverDefinition(GlpkMpsAssignmentSolver.TYPE, GlpkMpsAssignmentParameters::new);

		addRelocationSolverDefinition(BestResponseRelocationSolver.TYPE, BestResponseRelocationParameters::new);
		addRelocationSolverDefinition(CbcMpsRelocationSolver.TYPE, CbcMpsRelocationParameters::new);
		addRelocationSolverDefinition(GlpkMpsRelocationSolver.TYPE, GlpkMpsRelocationParameters::new);

		addTravelTimeEstimatorDefinition(DrtDetourTravelTimeEstimator.TYPE, DrtDetourEstimatorParameters::new);
		addTravelTimeEstimatorDefinition(EuclideanTravelTimeEstimator.TYPE, EuclideanEstimatorParameters::new);
		addTravelTimeEstimatorDefinition(RoutingTravelTimeEstimator.TYPE, RoutingEstimatorParameters::new);
		addTravelTimeEstimatorDefinition(HybridTravelTimeEstimator.TYPE, HybridEstimatorParameters::new);
		addTravelTimeEstimatorDefinition(MatrixTravelTimeEstimator.TYPE, MatrixEstimatorParameters::new);

		for (var entry : availableAssignmentSolvers.entrySet()) {
			addDefinition(AssignmentSolverParameters.SET_PREFIX + entry.getKey(), entry.getValue(),
					() -> assignmentSolver, params -> assignmentSolver = (AssignmentSolverParameters) params);
		}

		for (var entry : availableRelocationSolvers.entrySet()) {
			addDefinition(RelocationSolverParameters.SET_PREFIX + entry.getKey(), entry.getValue(),
					() -> relocationSolver, params -> relocationSolver = (RelocationSolverParameters) params);
		}

		for (var entry : availableTravelTimeEstimators.entrySet()) {
			addDefinition(RelocationSolverParameters.SET_PREFIX + entry.getKey(), entry.getValue(),
					() -> travelTimeEstimator,
					params -> this.travelTimeEstimator = (TravelTimeEstimatorParameters) params);
		}

		addDefinition(CongestionMitigationParameters.SET_NAME, CongestionMitigationParameters::new,
				() -> congestionMitigation, params -> congestionMitigation = (CongestionMitigationParameters) params);
	}

	public void prepareDefaultComponents() {
		addParameterSet(new GreedyTripFirstAssignmentParameters());
		addParameterSet(new BestResponseRelocationParameters());
		addParameterSet(new EuclideanEstimatorParameters());
		addParameterSet(new CongestionMitigationParameters());
	}

	public void clearAssignmentSolver() {
		for (String name : availableAssignmentSolvers.keySet()) {
			this.clearParameterSetsForType(AssignmentSolverParameters.SET_PREFIX + name);
		}

		this.assignmentSolver = null;
	}

	public void clearRelocationSolver() {
		for (String name : availableRelocationSolvers.keySet()) {
			this.clearParameterSetsForType(RelocationSolverParameters.SET_PREFIX + name);
		}

		this.relocationSolver = null;
	}

	public void clearTravelTimeEstimator() {
		for (String name : availableTravelTimeEstimators.keySet()) {
			this.clearParameterSetsForType(TravelTimeEstimatorParameters.SET_PREFIX + name);
		}

		this.travelTimeEstimator = null;
	}

	/* Assignment parameters */

	public static class GreedyTripFirstAssignmentParameters extends AssignmentSolverParameters {
		public GreedyTripFirstAssignmentParameters() {
			super(GreedyTripFirstSolver.TYPE);
		}
	}

	public static class GreedyVehicleFirstAssignmentParameters extends AssignmentSolverParameters {
		public GreedyVehicleFirstAssignmentParameters() {
			super(GreedyVehicleFirstSolver.TYPE);
		}
	}

	public static class GlpkMpsAssignmentParameters extends AssignmentSolverParameters {
		public GlpkMpsAssignmentParameters() {
			super(GlpkMpsAssignmentSolver.TYPE);
		}
	}

	public static class CbcMpsAssignmentParameters extends AssignmentSolverParameters {
		public CbcMpsAssignmentParameters() {
			super(CbcMpsAssignmentSolver.TYPE);
		}
	}

	/* Relocation parameters */

	public static class BestResponseRelocationParameters extends RelocationSolverParameters {
		public BestResponseRelocationParameters() {
			super(BestResponseRelocationSolver.TYPE);
		}
	}

	/* Travel time estimator parameters */

	public static class EuclideanEstimatorParameters extends TravelTimeEstimatorParameters {
		public EuclideanEstimatorParameters() {
			super(EuclideanTravelTimeEstimator.TYPE);
		}

		protected EuclideanEstimatorParameters(String estimatorType) {
			super(estimatorType);
		}

		@Parameter
		@Comment("Factor added to the Euclidean distance to estimate the travel time.")
		public double euclideanDistanceFactor = 1.3;

		@Parameter
		@Comment("Speed along the scaled crofly distance in [km/h]")
		public double euclideanSpeed = 40.0;
	}

	public static class RoutingEstimatorParameters extends TravelTimeEstimatorParameters {
		public RoutingEstimatorParameters() {
			super(RoutingTravelTimeEstimator.TYPE);
		}

		@Parameter
		@Comment("Delay until which a specific OD pair needs to be rerouted again")
		public double cacheLifetime = 1200.0;
	}

	public static class HybridEstimatorParameters extends EuclideanEstimatorParameters {
		public HybridEstimatorParameters() {
			super(HybridTravelTimeEstimator.TYPE);
		}

		@Parameter
		@Comment("Delay until which a specific OD pair needs to be rerouted again")
		public double cacheLifetime = 1200.0;
	}

	public static class DrtDetourEstimatorParameters extends TravelTimeEstimatorParameters {
		public DrtDetourEstimatorParameters() {
			super(DrtDetourTravelTimeEstimator.TYPE);
		}
	}

	public static class MatrixEstimatorParameters extends TravelTimeEstimatorParameters {
		public MatrixEstimatorParameters() {
			super(MatrixTravelTimeEstimator.TYPE);
		}

		@Parameter
		@Comment("Defines whether the travel time matrix is constructed step by step when routes get requested or all at once in the beginning")
		public boolean lazy = false;
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		Verify.verify(relocationInterval % assignmentInterval == 0,
				"Relocation interval must be multiple of the assignment interval");
		Verify.verify(loggingInterval % assignmentInterval == 0,
				"Logging interval must be multiple of the assignment interval");

		Verify.verifyNotNull(assignmentSolver);
		Verify.verifyNotNull(travelTimeEstimator);
		Verify.verifyNotNull(congestionMitigation);

		boolean foundDrt = false;

		for (DrtConfigGroup drtModeConfig : MultiModeDrtConfigGroup.get(config).getModalElements()) {
			if (drtModeConfig.getMode().equals(mode)) {
				foundDrt = true;

				if (drtModeConfig.getRebalancingParams().isPresent()) {
					Verify.verify(relocationInterval == 0 || relocationSolver == null,
							"If DRT rebalancing is enabled, relocationInterval should be zero (disabling Alonso-Mora relocation)");
				}
			}
		}

		Verify.verify(foundDrt, "Mode {} was defined for Alonso-Mora, but does not exist in DRT", mode);
	}
}
