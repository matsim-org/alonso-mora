package org.matsim.alonso_mora.examples.analysis;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.alonso_mora.examples.analysis.AssignmentAnalysisHandler.AssignmentItem;
import org.matsim.alonso_mora.examples.analysis.RequestAnalysisHandler.RequestItem;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;

/**
 * This listener enables or disables the new event handlers for requests,
 * assignments and relocations depending on whether analysis is desired in a
 * certain iteration. It also writes out the collected information.
 * 
 * @author Sebastian Hörl, IRT SystemX
 */
class AlonsoMoraAnalysisListener implements IterationStartsListener, IterationEndsListener {
	private final EventsManager eventsManager;
	private final OutputDirectoryHierarchy outputHierarchy;
	private final Collection<String> modes;

	private final Map<String, RequestAnalysisHandler> requestHandlers = new HashMap<>();
	private final Map<String, AssignmentAnalysisHandler> assignmentHandlers = new HashMap<>();

	private final Network network;

	public AlonsoMoraAnalysisListener(Collection<String> modes, EventsManager eventsManager,
			OutputDirectoryHierarchy outputHierarchy, Network network) {
		this.eventsManager = eventsManager;
		this.outputHierarchy = outputHierarchy;
		this.modes = modes;
		this.network = network;
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		for (String mode : modes) {
			requestHandlers.put(mode, new RequestAnalysisHandler(mode, network));
			assignmentHandlers.put(mode, new AssignmentAnalysisHandler(mode));
		}

		requestHandlers.values().forEach(eventsManager::addHandler);
		assignmentHandlers.values().forEach(eventsManager::addHandler);
	}

	private void writeOutput(int iteration) {
		try {
			// Write request analysis

			String requestsPath = outputHierarchy.getIterationFilename(iteration, "request_analysis.csv");
			BufferedWriter requestsWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(requestsPath)));

			requestsWriter.write(String.join(";", new String[] { //
					"mode", //
					"request_id", //
					"person_id", //
					"requestTime", //
					"rejectionTime", //
					"firstAssignmentTime", //
					"firstExpectedPickupTime", //
					"firstExpectedDropoffTime", //
					"lastAssignmentTime", //
					"lastExpectedPickupTime", //
					"lastExpectedDropoffTime", //
					"pickupTime", //
					"dropoffTime", //
					"pickupX", //
					"pickupY", //
					"dropoffX", //
					"dropoffY", //
					"latestPickupTime", //
					"latestDropoffTime", //
			}) + "\n");

			for (String mode : modes) {
				List<RequestItem> items = requestHandlers.get(mode).consolidate();

				for (RequestItem item : items) {
					requestsWriter.write(String.join(";", new String[] { //
							mode, //
							String.valueOf(item.requestId), //
							String.valueOf(item.personId), //
							String.valueOf(item.requestTime), //
							String.valueOf(item.rejectionTime), //
							String.valueOf(item.firstAssignmentTime), //
							String.valueOf(item.firstExpectedPickupTime), //
							String.valueOf(item.firstExpectedDropoffTime), //
							String.valueOf(item.lastAssignmentTime), //
							String.valueOf(item.lastExpectedPickupTime), //
							String.valueOf(item.lastExpectedDropoffTime), //
							String.valueOf(item.pickupTime), //
							String.valueOf(item.dropoffTime), //
							String.valueOf(item.pickupX), //
							String.valueOf(item.pickupY), //
							String.valueOf(item.dropoffX), //
							String.valueOf(item.dropoffY), //
							String.valueOf(item.latestPickupTime), //
							String.valueOf(item.latestDropoffTime), //
					}) + "\n");
				}
			}

			requestsWriter.close();

			// Write assignment items

			String assignmentsPath = outputHierarchy.getIterationFilename(iteration, "assignments_analysis.csv");
			BufferedWriter assignmentsWriter = new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(assignmentsPath)));

			assignmentsWriter.write(String.join(";", new String[] { //
					"mode", //
					"time", //
					"personId", //
					"vehicleId", //
					"isInitial", //
			}) + "\n");

			for (String mode : modes) {
				List<AssignmentItem> items = assignmentHandlers.get(mode).consolidate();

				for (AssignmentItem item : items) {
					assignmentsWriter.write(String.join(";", new String[] { //
							mode, //
							String.valueOf(item.time), //
							String.valueOf(item.personId), //
							String.valueOf(item.vehicleId), //
							String.valueOf(item.isInitial), //
					}) + "\n");
				}
			}

			assignmentsWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		writeOutput(event.getIteration());

		requestHandlers.values().forEach(eventsManager::removeHandler);
		assignmentHandlers.values().forEach(eventsManager::removeHandler);

		requestHandlers.clear();
		assignmentHandlers.clear();
	}
}
