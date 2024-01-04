package org.matsim.alonso_mora.algorithm.relocation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author sebhoerl
 */
public class NoopRelocationSolver implements RelocationSolver {
	static public final String TYPE = "Noop";

	@Override
	public Collection<Relocation> solve(List<Relocation> candidates) {
		return Collections.emptySet();
	}
}
