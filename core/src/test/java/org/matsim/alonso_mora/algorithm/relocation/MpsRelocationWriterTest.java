package org.matsim.alonso_mora.algorithm.relocation;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.relocation.RelocationSolver.Relocation;
import org.matsim.api.core.v01.network.Link;
import org.mockito.Mockito;

public class MpsRelocationWriterTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void testWriter() throws IOException {
		List<Relocation> relocations = new LinkedList<>();

		AlonsoMoraVehicle vehicle = Mockito.mock(AlonsoMoraVehicle.class);

		relocations.add(new Relocation(vehicle, Mockito.mock(Link.class), 100.0));
		relocations.add(new Relocation(vehicle, Mockito.mock(Link.class), 50.0));

		MpsRelocationWriter writer = new MpsRelocationWriter(relocations);
		writer.write(temporaryFolder.newFile("problem"));
	}
}
