package org.matsim.alonso_mora.algorithm.relocation;

import java.util.Collection;
import java.util.List;

import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.api.core.v01.network.Link;

/**
 * General interface for the relocation solver of the algorithm by Alonso-Mora
 * et al.
 * 
 * @author sebhoerl
 */
public interface RelocationSolver {
	Collection<Relocation> solve(List<Relocation> candidates);

	static public class Relocation {
		public final AlonsoMoraVehicle vehicle;
		public final Link destination;
		public final double cost;

		public Relocation(AlonsoMoraVehicle vehicle, Link destination, double cost) {
			this.vehicle = vehicle;
			this.destination = destination;
			this.cost = cost;
		}
	}
}
