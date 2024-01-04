package org.matsim.alonso_mora.algorithm.function.sequence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.mockito.Mockito;

public class ExtensiveSequenceGeneratorTest {
	private DrtRequest createDrtRequestMock() {
		DrtRequest request = Mockito.mock(DrtRequest.class);
		return request;
	}

	private AlonsoMoraRequest createAlonsoMoraRequestMock() {
		AlonsoMoraRequest request = Mockito.mock(AlonsoMoraRequest.class);
		Mockito.when(request.getDrtRequest()).thenReturn(createDrtRequestMock());
		return request;
	}

	@Test
	public void testOneRequests() {
		List<AlonsoMoraRequest> requests = new LinkedList<>();
		requests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(Collections.emptySet(), requests);

		int partial = 0;
		int complete = 0;

		while (generator.hasNext()) {
			partial++;

			if (generator.isComplete()) {
				complete++;
			}

			generator.advance();
		}

		assertEquals(2, partial);
		assertEquals(1, complete);
	}

	@Test
	public void testTwoRequests() {
		List<AlonsoMoraRequest> requests = new LinkedList<>();
		requests.add(createAlonsoMoraRequestMock());
		requests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(Collections.emptySet(), requests);

		int partial = 0;
		int complete = 0;

		while (generator.hasNext()) {
			partial++;

			if (generator.isComplete()) {
				complete++;
			}

			generator.advance();
		}

		assertEquals(18, partial);
		assertEquals(6, complete);
	}

	@Test
	public void testOneOnboard() {
		List<AlonsoMoraRequest> onboardRequests = new LinkedList<>();
		onboardRequests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(onboardRequests, Collections.emptySet());

		int partial = 0;
		int complete = 0;

		while (generator.hasNext()) {
			partial++;

			if (generator.isComplete()) {
				complete++;
			}

			generator.advance();
		}

		assertEquals(1, partial);
		assertEquals(1, complete);
	}

	@Test
	public void testTwoOnboard() {
		List<AlonsoMoraRequest> onboardRequests = new LinkedList<>();
		onboardRequests.add(createAlonsoMoraRequestMock());
		onboardRequests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(onboardRequests, Collections.emptySet());

		int partial = 0;
		int complete = 0;

		while (generator.hasNext()) {
			partial++;

			if (generator.isComplete()) {
				complete++;
			}

			generator.advance();
		}

		assertEquals(4, partial);
		assertEquals(2, complete);
	}

	@Test
	public void testTwoRequestsWithOneOnboard() {
		List<AlonsoMoraRequest> onboardRequests = new LinkedList<>();
		AlonsoMoraRequest onboardRequest = createAlonsoMoraRequestMock();
		onboardRequests.add(onboardRequest);

		List<AlonsoMoraRequest> requests = new LinkedList<>();

		requests.add(createAlonsoMoraRequestMock());
		requests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(onboardRequests, requests);

		int partial = 0;
		int complete = 0;

		while (generator.hasNext()) {
			partial++;

			if (generator.isComplete()) {
				complete++;
			}

			generator.advance();
		}

		assertEquals(89, partial);
		assertEquals(30, complete);
	}

	@Test
	public void testFourRequests() {
		List<AlonsoMoraRequest> requests = new LinkedList<>();

		requests.add(createAlonsoMoraRequestMock());
		requests.add(createAlonsoMoraRequestMock());
		requests.add(createAlonsoMoraRequestMock());
		requests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(Collections.emptySet(), requests);

		int partial = 0;
		int complete = 0;

		while (generator.hasNext()) {
			partial++;

			if (generator.isComplete()) {
				complete++;
			}

			generator.advance();
		}

		assertEquals(7364, partial);
		assertEquals(2520, complete);
	}

	@Test
	public void testTwoRequestsFirstReject() {
		List<AlonsoMoraRequest> requests = new LinkedList<>();
		requests.add(createAlonsoMoraRequestMock());
		requests.add(createAlonsoMoraRequestMock());

		ExtensiveSequenceGenerator generator = new ExtensiveSequenceGenerator(requests, Collections.emptySet());

		assertTrue(generator.hasNext());
		assertEquals(1, generator.get().size());

		generator.abort();

		assertTrue(generator.hasNext());
		assertEquals(1, generator.get().size());

		generator.advance();

		assertTrue(generator.hasNext());
		assertEquals(2, generator.get().size());

		generator.advance();

		assertFalse(generator.hasNext());
	}
}
