package org.matsim.alonso_mora.algorithm.assignment;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.alonso_mora.algorithm.AlonsoMoraTrip;
import org.matsim.alonso_mora.algorithm.assignment.AssignmentSolver.Solution.Status;

/**
 * Solves the assignment problem as described by Alonso-Mora et al. using the
 * Cbc solver via file transmission. Cbc must be avaialble on the system to use
 * this solver.
 * 
 * @author sebhoerl
 */
public class CbcMpsAssignmentSolver implements AssignmentSolver {
	static public final String TYPE = "CbcMps";

	private final static Logger logger = LogManager.getLogger(CbcMpsAssignmentSolver.class);

	private final RejectionPenalty rejectionPenalty;

	private final File problemPath;
	private final File solutionPath;

	private final double timeLimit;
	private final double optimalityGap;
	private final long randomSeed;

	public CbcMpsAssignmentSolver(RejectionPenalty rejectionPenalty, double timeLimit, double optimalityGap,
			File problemPath, File solutionPath, long randomSeed) {
		this.rejectionPenalty = rejectionPenalty;

		this.problemPath = problemPath;
		this.solutionPath = solutionPath;

		this.timeLimit = timeLimit;
		this.optimalityGap = optimalityGap;

		this.randomSeed = randomSeed;
	}

	@Override
	public Solution solve(Stream<AlonsoMoraTrip> candidates) {
		try {
			List<AlonsoMoraTrip> tripList = candidates.collect(Collectors.toList());
			new MpsAssignmentWriter(tripList, rejectionPenalty).write(problemPath);

			new ProcessBuilder("cbc", problemPath.toString(), "-randomSeed", String.valueOf(randomSeed),
					"-randomCbcSeed", String.valueOf(randomSeed), "-ratio", String.valueOf(optimalityGap), "-seconds",
					String.valueOf(timeLimit), "-threads", "1", "-solve", "-solution", solutionPath.toString()).start()
					.waitFor();

			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(solutionPath)));

			String line = null;
			boolean isFirstLine = true;

			boolean isOptimal = true;
			List<AlonsoMoraTrip> solution = new LinkedList<>();

			while ((line = reader.readLine()) != null) {
				if (isFirstLine) {
					if (!line.startsWith("Optimal")) {
						isOptimal = false;
						break;
					}

					isFirstLine = false;
				} else if (line.contains("T")) {
					String[] parts = line.trim().split("\\s+");

					if (parts[2].equals("1")) {
						int tripIndex = Integer.parseInt(parts[1].replace("T", ""));
						solution.add(tripList.get(tripIndex));
					}
				}
			}

			reader.close();

			if (!isOptimal) {
				logger.warn("Cbc MPS solution is not optimal");
			}

			return new Solution(isOptimal ? Status.OPTIMAL : Status.FEASIBLE, solution);
		} catch (FileNotFoundException e) {
			logger.warn("Cbc MPS solver did not finish successfully");
			return new Solution(Status.FAILURE, Collections.emptySet());
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	static public boolean checkAvailability() {
		try {
			Process process = new ProcessBuilder("cbc", "unknown_file").start();

			BufferedInputStream inputStream = new BufferedInputStream(process.getInputStream());
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

			String line = null;
			boolean messageFound = false;

			while ((line = reader.readLine()) != null) {
				if (line.contains("Welcome to the CBC MILP Solver")) {
					messageFound = true;
				}
			}

			if (messageFound) {
				return true;
			}
		} catch (IOException e) {
		}

		logger.error( //
				"Cbc MILP solver was not found in the system."
						+ " Make sure you are able to call the 'cbc' executable from the command line.");

		return false;
	}
}
