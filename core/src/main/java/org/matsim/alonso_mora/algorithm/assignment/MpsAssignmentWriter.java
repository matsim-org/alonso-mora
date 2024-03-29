package org.matsim.alonso_mora.algorithm.assignment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.matsim.alonso_mora.algorithm.AlonsoMoraRequest;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.AlonsoMoraVehicle;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.RejectionPenalty;

/**
 * Writes the assignment problem from Alonso-Mora et al. in MPS format which can
 * be solved using external tools.
 * 
 * @author sebhoerl
 */
public class MpsAssignmentWriter {
	private final List<AlonsoMoraTrip> tripList;

	private final RejectionPenalty rejectionPenalty;

	public MpsAssignmentWriter(List<AlonsoMoraTrip> tripList, RejectionPenalty rejectionPenalty) {
		this.tripList = tripList;
		this.rejectionPenalty = rejectionPenalty;
	}

	public void write(File path) throws IOException {
		List<AlonsoMoraRequest> requestList = new ArrayList<>(
				tripList.stream().flatMap(t -> t.getRequests().stream()).distinct().collect(Collectors.toList()));
		List<AlonsoMoraVehicle> vehicleList = new ArrayList<>(
				tripList.stream().map(t -> t.getVehicle()).distinct().collect(Collectors.toList()));

		int numberOfRequests = requestList.size();
		int numberOfTrips = tripList.size();
		int numberOfVehicles = vehicleList.size();

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path)));

		writer.write("NAME AlonsoMoraAssignment\n");
		writer.write("ROWS\n");

		int rowIndex = 0;

		writer.write(String.format(Locale.US, " N R%07d\n", rowIndex));
		rowIndex++;

		// <= 1 rows for the vehicles
		for (int i = 0; i < numberOfVehicles; i++) {
			writer.write(String.format(Locale.US, " L R%07d\n", rowIndex));
			rowIndex++;
		}

		// == 1 rows for the requests
		for (int i = 0; i < numberOfRequests; i++) {
			writer.write(String.format(Locale.US, " E R%07d\n", rowIndex));
			rowIndex++;
		}

		writer.write("COLUMNS\n");
		writer.write(" M0000001 'MARKER' 'INTORG'\n");

		// Trip influences
		for (int i = 0; i < numberOfTrips; i++) {
			AlonsoMoraTrip trip = tripList.get(i);
			writer.write(String.format(Locale.US, " T%d R%07d %f\n", i, 0, trip.getResult().getCost()));

			int vehicleIndex = vehicleList.indexOf(trip.getVehicle());
			writer.write(String.format(Locale.US, " T%d R%07d 1\n", i, vehicleIndex + 1));

			for (AlonsoMoraRequest request : trip.getRequests()) {
				int requestIndex = requestList.indexOf(request);
				writer.write(String.format(Locale.US, " T%d R%07d 1\n", i, requestIndex + numberOfVehicles + 1));
			}
		}

		// Request influences
		for (int i = 0; i < numberOfRequests; i++) {
			double penalty = rejectionPenalty.getPenalty(requestList.get(i));
			writer.write(String.format(Locale.US, " x%d R%07d %f R%07d 1\n", i, 0, penalty, i + numberOfVehicles + 1));
		}

		writer.write(" M0000002 'MARKER' 'INTEND'\n");
		writer.write("RHS\n");

		for (int i = 0; i < numberOfVehicles + numberOfRequests; i++) {
			writer.write(String.format(Locale.US, " RHS1 R%07d 1\n", i + 1));
		}

		writer.write("BOUNDS\n");

		// Trip variables bounds
		for (int i = 0; i < numberOfTrips; i++) {
			writer.write(String.format(Locale.US, " UP BND1 T%d 1\n", i));
		}

		// Trip variables bounds
		for (int i = 0; i < numberOfRequests; i++) {
			writer.write(String.format(Locale.US, " UP BND1 x%d 1\n", i));
		}

		writer.write("ENDATA\n");

		writer.close();
	}
}
