package org.matsim.alonso_mora.shifts;

import org.matsim.alonso_mora.AlonsoMoraConfigGroup;
import org.matsim.alonso_mora.AlonsoMoraOptimizer;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicleFactory;
import org.matsim.alonso_mora.algorithm.function.DefaultAlonsoMoraFunction.Constraint;
import org.matsim.alonso_mora.scheduling.AlonsoMoraScheduler;
import org.matsim.alonso_mora.scheduling.DefaultAlonsoMoraScheduler.OperationalVoter;
import org.matsim.alonso_mora.travel_time.TravelTimeEstimator;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.extension.operations.shifts.config.ShiftsParams;
import org.matsim.contrib.drt.extension.operations.shifts.dispatcher.DrtShiftDispatcher;
import org.matsim.contrib.drt.extension.operations.shifts.optimizer.ShiftDrtOptimizer;
import org.matsim.contrib.drt.extension.operations.shifts.schedule.ShiftDrtStayTaskEndTimeCalculator;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtStayTaskEndTimeCalculator;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.drt.stops.PassengerStopDurationProvider;
import org.matsim.contrib.drt.stops.StopTimeCalculator;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater.StayTaskEndTimeCalculator;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;

public class ShiftAlonsoMoraModule extends AbstractDvrpModeQSimModule {
	private final DrtConfigGroup drtConfig;
	private final ShiftsParams shiftConfig;
	private final AlonsoMoraConfigGroup amConfig;

	public ShiftAlonsoMoraModule(DrtConfigGroup drtConfig, ShiftsParams shiftConfig, AlonsoMoraConfigGroup amConfig) {
		super(drtConfig.getMode());
		this.drtConfig = drtConfig;
		this.shiftConfig = shiftConfig;
		this.amConfig = amConfig;
	}

	@Override
	protected void configureQSim() {
		bindModal(AlonsoMoraVehicleFactory.class).toProvider(modalProvider(getter -> {
			return v -> new ShiftAlonsoMoraVehicle(v);
		}));

		// TODO: This can become a general binding in DRT
		bindModal(StayTaskEndTimeCalculator.class).toProvider(modalProvider(getter -> {
			return new ShiftDrtStayTaskEndTimeCalculator(shiftConfig,
					new DrtStayTaskEndTimeCalculator(getter.getModal(StopTimeCalculator.class)));
		}));

		bindModal(OperationalVoter.class).toProvider(modalProvider(getter -> {
			return new ShiftOperationalVoter();
		}));

		bindModal(Constraint.class).toProvider(modalProvider(getter -> {
			TravelTimeEstimator travelTimeEstimator = getter.getModal(TravelTimeEstimator.class);
			return new AlonsoMoraShiftConstraint(travelTimeEstimator);
		}));

		// Wrap the Shift opimizer around the AlonsoMoraOptimizer instead of
		// DefaultDrtOptimizer
		addModalComponent(DrtOptimizer.class,
				modalProvider((getter) -> new ShiftDrtOptimizer(getter.getModal(AlonsoMoraOptimizer.class),
						getter.getModal(DrtShiftDispatcher.class), getter.getModal(ScheduleTimingUpdater.class))));

		bindModal(AlonsoMoraScheduler.class).toProvider(modalProvider(getter -> {
			StayTaskEndTimeCalculator endTimeCalculator = getter.getModal(StayTaskEndTimeCalculator.class);
			DrtTaskFactory taskFactory = getter.getModal(DrtTaskFactory.class);
			LeastCostPathCalculator router = getter.getModal(LeastCostPathCalculator.class);

			TravelTime travelTime = getter.getModal(TravelTime.class);
			Network network = getter.getModal(Network.class);

			OperationalVoter operationalVoter = getter.getModal(OperationalVoter.class);
			PassengerStopDurationProvider stopDurationProvider = getter.getModal(PassengerStopDurationProvider.class);
			
			return new ShiftAlonsoMoraScheduler(taskFactory, stopDurationProvider, drtConfig.getStopDuration(),
					amConfig.checkDeterminsticTravelTimes, amConfig.rerouteDuringScheduling, travelTime, network,
					endTimeCalculator, router, operationalVoter);
		}));
	}
}
