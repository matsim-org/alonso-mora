package org.matsim.alonso_mora.algorithm.relocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.assignment.GlpkMpsAssignmentSolver;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver.Relocation;
import org.matsim.api.core.v01.network.Link;
import org.mockito.Mockito;

public class GlpkMpsRelocationSolverTest {
	@BeforeAll
	static public void checkSolver() {
		assertTrue(GlpkMpsAssignmentSolver.checkAvailability(), "Checking for availability of GLPK solver");
	}

	@Test
	public void testTwoVehicesOneDestination(@TempDir File temporaryFolder) throws IOException {
		File problemFile = new File(temporaryFolder, "problem");
		File solutionFile = new File(temporaryFolder, "solution");

		GlpkMpsRelocationSolver solver = new GlpkMpsRelocationSolver(1000, //
				problemFile, //
				solutionFile);

		AlonsoMoraVehicle vehicle = Mockito.mock(AlonsoMoraVehicle.class);

		List<Relocation> relocations = new LinkedList<>();
		relocations.add(new Relocation(vehicle, Mockito.mock(Link.class), 100.0));
		relocations.add(new Relocation(vehicle, Mockito.mock(Link.class), 50.0));

		Collection<Relocation> solution = solver.solve(relocations);

		assertEquals(1, solution.size());
		assertEquals(relocations.get(1), solution.iterator().next());
	}

	@Test
	public void testOneVehicesTwoDestinations(@TempDir File temporaryFolder) throws IOException {
		File problemFile = new File(temporaryFolder, "problem");
		File solutionFile = new File(temporaryFolder, "solution");

		GlpkMpsRelocationSolver solver = new GlpkMpsRelocationSolver(1000, //
				problemFile, //
				solutionFile);

		Link link = Mockito.mock(Link.class);

		List<Relocation> relocations = new LinkedList<>();
		relocations.add(new Relocation(Mockito.mock(AlonsoMoraVehicle.class), link, 20.0));
		relocations.add(new Relocation(Mockito.mock(AlonsoMoraVehicle.class), link, 50.0));

		Collection<Relocation> solution = solver.solve(relocations);

		assertEquals(1, solution.size());
		assertEquals(relocations.get(0), solution.iterator().next());
	}

	@Test
	public void testComplex(@TempDir File temporaryFolder) throws IOException {
		File problemFile = new File(temporaryFolder, "problem");
		File solutionFile = new File(temporaryFolder, "solution");

		GlpkMpsRelocationSolver solver = new GlpkMpsRelocationSolver(1000, //
				problemFile, //
				solutionFile);

		Link linkA = Mockito.mock(Link.class);
		Link linkB = Mockito.mock(Link.class);

		AlonsoMoraVehicle vehicle1 = Mockito.mock(AlonsoMoraVehicle.class);
		AlonsoMoraVehicle vehicle2 = Mockito.mock(AlonsoMoraVehicle.class);
		AlonsoMoraVehicle vehicle3 = Mockito.mock(AlonsoMoraVehicle.class);

		List<Relocation> relocations = new LinkedList<>();
		relocations.add(new Relocation(vehicle1, linkA, 20.0));
		relocations.add(new Relocation(vehicle2, linkA, 25.0));
		relocations.add(new Relocation(vehicle3, linkA, 20.0));
		relocations.add(new Relocation(vehicle1, linkB, 40.0));
		relocations.add(new Relocation(vehicle3, linkB, 10.0));

		Collection<Relocation> solution = solver.solve(relocations);

		assertEquals(2, solution.size());

		List<Relocation> expected = new ArrayList<>(Arrays.asList(relocations.get(0), relocations.get(4)));
		expected.removeAll(relocations);
		assertEquals(0, expected.size());
	}

	@Test
	public void testEmpty(@TempDir File temporaryFolder) throws IOException {
		File problemFile = new File(temporaryFolder, "problem");
		File solutionFile = new File(temporaryFolder, "solution");

		GlpkMpsRelocationSolver solver = new GlpkMpsRelocationSolver(1000, //
				problemFile, //
				solutionFile);

		Collection<Relocation> solution = solver.solve(Collections.emptyList());

		assertEquals(0, solution.size());
	}
}
