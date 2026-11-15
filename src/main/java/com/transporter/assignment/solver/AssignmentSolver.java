package com.transporter.assignment.solver;

import com.transporter.assignment.dto.AssignmentDto;

import java.util.*;

/**
 * Exact bitmask solver for the Transporter Assignment on Lanes problem.
 *
 * Approach:
 *   Enumerate all 2^n subsets of transporters (n = number of transporters).
 *   For each subset S with |S| <= maxTransporters:
 *     For each lane, find the cheapest quoter in S.
 *     If any lane has no quoter in S → infeasible, skip.
 *     Otherwise compute total cost.
 *   Keep the best feasible subset by: (1) cost ascending, (2) transporters used descending.
 *
 * Complexity: O(2^n * L * n) where L = number of lanes, n = number of transporters.
 * Guard: n > 20 is rejected (2^20 = ~1M subsets; beyond that an ILP solver is needed).
 */
public class AssignmentSolver {

    public static final int MAX_TRANSPORTERS_EXACT = 20;

    public SolverResult solve(
            List<Long> laneIds,
            List<Long> transporterIds,
            long[][] costMatrix,
            int maxTransporters) {

        int n = transporterIds.size();
        int L = laneIds.size();

        if (maxTransporters < 1) {
            throw new IllegalArgumentException("maxTransporters must be at least 1");
        }

        if (n > MAX_TRANSPORTERS_EXACT) {
            throw new SolverException(
                    "Exact solver supports at most " + MAX_TRANSPORTERS_EXACT +
                    " transporters (n=" + n + "). Use an ILP approach for larger inputs.");
        }

        // Clamp maxTransporters to available transporters (n) since requirement is "at most K"
        int effectiveK = Math.min(maxTransporters, n);

        int minNeeded = computeMinTransportersNeeded(costMatrix, n, L);

        if (minNeeded > effectiveK) {
            throw new InfeasibleException(
                    "No feasible assignment exists with " + maxTransporters +
                    " transporter(s). Minimum required: " + minNeeded + ".",
                    minNeeded);
        }

        long bestCost = Long.MAX_VALUE;
        int bestUsed = -1;
        int[] bestAssignment = null;

        for (int mask = 1; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) > effectiveK) continue;

            long totalCost = 0;
            int[] assignment = new int[L];
            boolean feasible = true;

            for (int l = 0; l < L; l++) {
                long minCost = Long.MAX_VALUE;
                int chosenT = -1;
                for (int t = 0; t < n; t++) {
                    if ((mask & (1 << t)) == 0) continue;
                    if (costMatrix[t][l] != Long.MAX_VALUE && costMatrix[t][l] < minCost) {
                        minCost = costMatrix[t][l];
                        chosenT = t;
                    }
                }
                if (chosenT == -1) { feasible = false; break; }
                assignment[l] = chosenT;
                totalCost += minCost;
            }

            if (!feasible) continue;

            Set<Integer> usedSet = new HashSet<>();
            for (int val : assignment) usedSet.add(val);
            int used = usedSet.size();

            if (totalCost < bestCost || (totalCost == bestCost && used > bestUsed)) {
                bestCost = totalCost;
                bestUsed = used;
                bestAssignment = assignment.clone();
            }
        }

        List<AssignmentDto> assignments = new ArrayList<>();
        Set<Long> selectedSet = new TreeSet<>();
        for (int l = 0; l < L; l++) {
            long tid = transporterIds.get(bestAssignment[l]);
            assignments.add(new AssignmentDto(laneIds.get(l), tid));
            selectedSet.add(tid);
        }

        return new SolverResult(bestCost, assignments, new ArrayList<>(selectedSet));
    }

    private int computeMinTransportersNeeded(long[][] costMatrix, int n, int L) {
        for (int k = 1; k <= n; k++) {
            if (canCoverWithK(costMatrix, n, L, k)) return k;
        }
        return n + 1;
    }

    private boolean canCoverWithK(long[][] costMatrix, int n, int L, int k) {
        for (int mask = 1; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) != k) continue;
            boolean covers = true;
            for (int l = 0; l < L; l++) {
                boolean covered = false;
                for (int t = 0; t < n; t++) {
                    if ((mask & (1 << t)) != 0 && costMatrix[t][l] != Long.MAX_VALUE) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) { covers = false; break; }
            }
            if (covers) return true;
        }
        return false;
    }

    public record SolverResult(
            long totalCost,
            List<AssignmentDto> assignments,
            List<Long> selectedTransporters
    ) {}

    public static class SolverException extends RuntimeException {
        public SolverException(String msg) { super(msg); }
    }

    public static class InfeasibleException extends RuntimeException {
        private final int minNeeded;

        public InfeasibleException(String msg, int minNeeded) {
            super(msg);
            this.minNeeded = minNeeded;
        }

        public int getMinNeeded() { return minNeeded; }
    }
}
