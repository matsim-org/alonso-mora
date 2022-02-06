package org.matsim.alonso_mora.algorithm.graphs;

import java.util.stream.Stream;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.function.AlonsoMoraFunction;

/**
 * Represents a trip-vehicle graph
 * 
 * @author sebhoerl
 */
public interface VehicleGraph {

	void addRequest(AlonsoMoraRequest request, double now);

	void addRequest(AlonsoMoraRequest request, double now, AlonsoMoraFunction.Result result);

	Stream<AlonsoMoraTrip> stream();

	int getSize();

	void preserveVehicleAssignment(double now);

}