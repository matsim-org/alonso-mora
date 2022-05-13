package org.matsim.alonso_mora.examples;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.MatrixEstimatorParameters;
import org.matsim.alonso_mora.AlonsoMoraConfigGroup.SequenceGeneratorType;
import org.matsim.alonso_mora.AlonsoMoraModeModule;
import org.matsim.alonso_mora.AlonsoMoraModeQSimModule;
import org.matsim.alonso_mora.examples.analysis.AlonsoMoraAnalysisModule;
import org.matsim.alonso_mora.examples.vehicle_locations.VehicleLocations;
import org.matsim.alonso_mora.examples.vehicle_locations.VehicleLocationsReader;
import org.matsim.alonso_mora.gurobi.GurobiAssignmentParameters;
import org.matsim.alonso_mora.gurobi.GurobiModule;
import org.matsim.alonso_mora.gurobi.GurobiRelocationParameters;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.insertion.ExtensiveInsertionSearchParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.FleetSpecification;
import org.matsim.contrib.dvrp.fleet.FleetSpecificationImpl;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.QSimConfigGroup.StarttimeInterpretation;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.opengis.referencing.FactoryException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Verify;

public class RunNewYorkPaper {
	static public void main(String[] args) throws CommandLine.ConfigurationException, FactoryException,
			JsonGenerationException, JsonMappingException, IOException {
		CommandLine cmd = new CommandLine.Builder(args).requireOptions( //
				"demand-path", "network-path", "output-path", //
				"use-alonso-mora" //
		).allowOptions( //
				"fleet-size", "vehicle-capacity", //
				"maximum-waiting-time", "maximum-queue-time", //
				"sampling-rate", "stop-duration", //
				"threads", "end-time", "service-end-time", //
				"vehicle-location-path", "graph-limit", //
				"rtv-sequence-limit", "rtv-vehicle-limit" //
		).build();

		String demandPath = cmd.getOptionStrict("demand-path");
		String networkPath = cmd.getOptionStrict("network-path");
		String outputPath = cmd.getOptionStrict("output-path");

		int fleetSize = cmd.getOption("fleet-size").map(Integer::parseInt).orElse(1000);
		int vehicleCapacity = cmd.getOption("vehicle-capacity").map(Integer::parseInt).orElse(4);

		double maximumWaitingTime = cmd.getOption("maximum-waiting-time").map(Double::parseDouble).orElse(300.0);
		double maximumQueueTime = cmd.getOption("maximum-queue-time").map(Double::parseDouble).orElse(0.0);

		double samplingRate = cmd.getOption("sampling-rate").map(Double::parseDouble).orElse(1.0);
		double stopDuration = cmd.getOption("stop-duration").map(Double::parseDouble).orElse(60.0);
		int graphLimit = cmd.getOption("graph-limit").map(Integer::parseInt).orElse(30);
		int rtvSequenceLimit = cmd.getOption("rtv-sequence-limit").map(Integer::parseInt).orElse(Integer.MAX_VALUE);
		int rtvVehicleLimit = cmd.getOption("rtv-vehicle-limit").map(Integer::parseInt).orElse(Integer.MAX_VALUE);

		boolean useAlonsoMora = Boolean.parseBoolean(cmd.getOptionStrict("use-alonso-mora"));

		int threads = cmd.getOption("threads").map(Integer::parseInt)
				.orElse(Runtime.getRuntime().availableProcessors());

		Config config = ConfigUtils.createConfig();
		config.network().setInputFile(networkPath);
		config.plans().setInputFile(demandPath);

		double endTime = cmd.getOption("end-time").map(Double::parseDouble).orElse(24.0 * 3600.0 * 7.0);

		Scenario scenario = ScenarioUtils.createScenario(config);
		scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class,
				new DrtRouteFactory());
		ScenarioUtils.loadScenario(scenario);

		{ // Demand downsampling
			Random random = new Random(0);
			Set<Id<Person>> removeIds = new HashSet<>();

			for (Person person : scenario.getPopulation().getPersons().values()) {
				if (random.nextDouble() > samplingRate) {
					removeIds.add(person.getId());
				}
			}

			removeIds.forEach(scenario.getPopulation()::removePerson);
		}

		// Find start links either from previous state output or by sampling trip
		// origins
		List<Id<Link>> vehicleLinks = new ArrayList<>(fleetSize);

		if (cmd.hasOption("vehicle-location-path")) {
			VehicleLocations locations = new VehicleLocationsReader()
					.read(new File(cmd.getOptionStrict("vehicle-location-path")));
			locations.locations.forEach(l -> vehicleLinks.add(Id.createLinkId(l.linkId)));
		} else {
			IdMap<Link, Integer> counts = new IdMap<>(Link.class);

			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (Leg leg : TripStructureUtils.getLegs(person.getSelectedPlan())) {
					if (leg.getMode().equals("drt")) {
						counts.compute(leg.getRoute().getStartLinkId(), (id, value) -> value == null ? 1 : value + 1);
					}
				}
			}

			List<Id<Link>> linkIds = new LinkedList<>();
			List<Double> linkCDF = new LinkedList<>();

			for (var entry : counts.entrySet()) {
				linkIds.add(entry.getKey());
				linkCDF.add((double) entry.getValue());
			}

			for (int i = 1; i < linkCDF.size(); i++) {
				linkCDF.set(i, linkCDF.get(i - 1) + linkCDF.get(i));
			}

			for (int i = 0; i < linkCDF.size(); i++) {
				linkCDF.set(i, linkCDF.get(i) / linkCDF.get(linkCDF.size() - 1));
			}

			Random random = new Random(0);

			for (int i = 0; i < fleetSize; i++) {
				double s = random.nextDouble();

				for (int k = 0; k < linkCDF.size(); k++) {
					if (s <= linkCDF.get(k)) {
						vehicleLinks.add(linkIds.get(k));
						break;
					}
				}
			}
		}

		Verify.verify(vehicleLinks.size() == fleetSize);
		FleetSpecification fleet = new FleetSpecificationImpl();

		{ // Create fleet
			double serviceEndTime = cmd.getOption("service-end-time").map(Double::parseDouble).orElse(endTime);

			for (int i = 0; i < fleetSize; i++) {
				fleet.addVehicleSpecification(ImmutableDvrpVehicleSpecification.newBuilder() //
						.id(Id.create("drt_" + i, DvrpVehicle.class)) //
						.capacity(vehicleCapacity) //
						.serviceBeginTime(0.0) //
						.serviceEndTime(serviceEndTime) //
						.startLinkId(vehicleLinks.get(i)) //
						.build());
			}
		}

		// Set up config
		config.controler().setOutputDirectory(outputPath);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setLastIteration(0);

		config.qsim().setNumberOfThreads(Math.min(12, threads));
		config.global().setNumberOfThreads(threads);

		config.qsim().setFlowCapFactor(1e9);
		config.qsim().setStorageCapFactor(1e9);

		config.qsim().setStartTime(0.0);
		config.qsim().setSimStarttimeInterpretation(StarttimeInterpretation.onlyUseStarttime);

		config.qsim().setEndTime(endTime);

		ModeParams modeParams = new ModeParams("drt");
		config.planCalcScore().addModeParams(modeParams);

		ActivityParams genericParams = new ActivityParams("generic");
		genericParams.setScoringThisActivityAtAll(false);
		config.planCalcScore().addActivityParams(genericParams);

		ActivityParams interactionParams = new ActivityParams("drt interaction");
		interactionParams.setScoringThisActivityAtAll(false);
		config.planCalcScore().addActivityParams(interactionParams);

		StrategySettings keepSettings = new StrategySettings();
		keepSettings.setStrategyName("BestScore");
		keepSettings.setWeight(1.0);
		config.strategy().addStrategySettings(keepSettings);

		DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
		config.addModule(dvrpConfig);

		MultiModeDrtConfigGroup drtConfig = new MultiModeDrtConfigGroup();
		config.addModule(drtConfig);

		DrtConfigGroup modeConfig = new DrtConfigGroup() //
				.setMode(TransportMode.drt) //
				.setMaxTravelTimeAlpha(1.0) //
				.setMaxTravelTimeBeta(stopDuration + 2.0 * maximumWaitingTime) //
				.setMaxWaitTime(maximumWaitingTime + stopDuration) //
				.setStopDuration(stopDuration) //
				.setRejectRequestIfMaxWaitOrTravelTimeViolated(true) //
				.setUseModeFilteredSubnetwork(false) //
				.setIdleVehiclesReturnToDepots(false) //
				.setOperationalScheme(DrtConfigGroup.OperationalScheme.door2door) //
				.setPlotDetailedCustomerStats(true) //
				.setMaxWalkDistance(1000.) //
				.setNumberOfThreads(threads);

		modeConfig.addParameterSet(new ExtensiveInsertionSearchParams());
		drtConfig.addParameterSet(modeConfig);

		cmd.applyConfiguration(config);

		// Set up controller
		Controler controller = new Controler(scenario);

		controller.addOverridingModule(new DvrpModule());
		controller.addOverridingModule(new MultiModeDrtModule());
		controller.configureQSimComponents(DvrpQSimComponents.activateAllModes(drtConfig));

		controller.addOverridingModule(new AbstractDvrpModeModule("drt") {
			@Override
			public void install() {
				bindModal(FleetSpecification.class).toInstance(fleet);
			}
		});

		// Alonso-Mora

		if (useAlonsoMora) {
			config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles(true);

			AlonsoMoraConfigGroup amConfig = new AlonsoMoraConfigGroup();

			amConfig.setMaximumQueueTime(maximumQueueTime);
			amConfig.setAssignmentInterval(30);
			amConfig.setRelocationInterval(30);

			amConfig.getCongestionMitigationParameters().setAllowBareReassignment(false);
			amConfig.getCongestionMitigationParameters().setAllowPickupViolations(false);
			amConfig.getCongestionMitigationParameters().setAllowPickupsWithDropoffViolations(false);
			amConfig.getCongestionMitigationParameters().setPreserveVehicleAssignments(false);

			amConfig.setRerouteDuringScheduling(false);
			amConfig.setCheckDeterminsticTravelTimes(true);

			amConfig.setSequenceGeneratorType(SequenceGeneratorType.Combined);
			amConfig.setCandidateVehiclesPerRequest(graphLimit);
			amConfig.setTripGraphLimitPerSequenceLength(rtvSequenceLimit);
			amConfig.setTripGraphLimitPerVehicle(rtvVehicleLimit);

			GurobiAssignmentParameters assignmentParameters = new GurobiAssignmentParameters();
			amConfig.addParameterSet(assignmentParameters);

			GurobiRelocationParameters relocationParameters = new GurobiRelocationParameters();
			amConfig.addParameterSet(relocationParameters);

			MatrixEstimatorParameters estimator = new MatrixEstimatorParameters();
			amConfig.addParameterSet(estimator);

			controller.addOverridingModule(new AlonsoMoraModeModule(modeConfig));
			controller.addOverridingQSimModule(new GurobiModule(modeConfig, amConfig));
			controller.addOverridingQSimModule(new AlonsoMoraModeQSimModule(modeConfig, amConfig));
			controller.addOverridingModule(new AlonsoMoraAnalysisModule());
		}

		controller.run();
	}
}
