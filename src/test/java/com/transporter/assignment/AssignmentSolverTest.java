package com.transporter.assignment;

import com.transporter.assignment.dto.AssignmentDto;
import com.transporter.assignment.solver.AssignmentSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentSolverTest {

    private static final long INF = Long.MAX_VALUE;
    private AssignmentSolver solver;

    @BeforeEach
    void setUp() {
        solver = new AssignmentSolver();
    }

    /**
     * TC1 Cost Matrix: 5 lanes (L1-L5), 7 transporters (T1-T7)
     * Values from PDF Test Case 1 / CSV rows 1-5:
     * T1: L1=20835, L2=10512, L3=22105, L4=42481, L5=19862
     * T2: L1=48844, L2=31326, L3=18640, L4=45828, L5=18297
     * T3: L1=39020, L2=20648, L3=31438, L4=36447, L5=12789
     * T4: L1=14400, L2=44514, L3=14316, L4=10678, L5=13032
     * T5: L1=11601, L2=19760, L3=40870, L4=20635, L5=26421
     * T6: L1=35095, L2=12494, L3=17808, L4=36210, L5=39444
     * T7: L1=26070, L2=41098, L3=20932, L4=16897, L5=27938
     */
    private long[][] buildTc1Matrix() {
        return new long[][]{
                {20835, 10512, 22105, 42481, 19862}, // T1 (id: 1)
                {48844, 31326, 18640, 45828, 18297}, // T2 (id: 2)
                {39020, 20648, 31438, 36447, 12789}, // T3 (id: 3)
                {14400, 44514, 14316, 10678, 13032}, // T4 (id: 4)
                {11601, 19760, 40870, 20635, 26421}, // T5 (id: 5)
                {35095, 12494, 17808, 36210, 39444}, // T6 (id: 6)
                {26070, 41098, 20932, 16897, 27938}  // T7 (id: 7)
        };
    }

    private List<Long> tc1LaneIds() {
        return List.of(1L, 2L, 3L, 4L, 5L);
    }

    private List<Long> tc1TransporterIds() {
        return List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    /**
     * TC2: 5 lanes, 4 transporters (X=1, Y=2, Z=3, W=4)
     * Quotes:
     * X: L1=1000, L2=1500
     * Y: L3=2000, L4=1800
     * Z: L5=1200
     * W: L1=1100, L4=1750
     */
    private long[][] buildTc2Matrix() {
        return new long[][]{
                {1000, 1500, INF, INF, INF},  // X (id: 1)
                {INF, INF, 2000, 1800, INF},   // Y (id: 2)
                {INF, INF, INF, INF, 1200},    // Z (id: 3)
                {1100, INF, INF, 1750, INF}    // W (id: 4)
        };
    }

    private List<Long> tc2LaneIds() {
        return List.of(1L, 2L, 3L, 4L, 5L);
    }

    private List<Long> tc2TransporterIds() {
        return List.of(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("PDF Test Case 1: 5 lanes, 7 transporters, K=3 -> cost 60139, T1, T4, T5")
    void tc1_k3_expectedCost60139() {
        var result = solver.solve(tc1LaneIds(), tc1TransporterIds(), buildTc1Matrix(), 3);

        assertEquals(60139L, result.totalCost(), "Total cost must be 60139");

        Map<Long, Long> assignmentMap = new HashMap<>();
        for (AssignmentDto a : result.assignments()) {
            assignmentMap.put(a.laneId(), a.transporterId());
        }

        // Verify specific lane assignments: L1->T5, L2->T1, L3->T4, L4->T4, L5->T4
        assertEquals(5L, assignmentMap.get(1L), "Lane 1 should be assigned to T5 (quote: 11601)");
        assertEquals(1L, assignmentMap.get(2L), "Lane 2 should be assigned to T1 (quote: 10512)");
        assertEquals(4L, assignmentMap.get(3L), "Lane 3 should be assigned to T4 (quote: 14316)");
        assertEquals(4L, assignmentMap.get(4L), "Lane 4 should be assigned to T4 (quote: 10678)");
        assertEquals(4L, assignmentMap.get(5L), "Lane 5 should be assigned to T4 (quote: 13032)");

        // Selected transporters must be exactly [1, 4, 5] in ascending order
        assertEquals(List.of(1L, 4L, 5L), result.selectedTransporters());
        assertEquals(3, result.selectedTransporters().size());
    }

    @Test
    @DisplayName("PDF Test Case 2: K=3 -> cost 7500 using X, Y, Z")
    void tc2_k3_expectedCost7500() {
        var result = solver.solve(tc2LaneIds(), tc2TransporterIds(), buildTc2Matrix(), 3);

        assertEquals(7500L, result.totalCost());
        Map<Long, Long> map = new HashMap<>();
        for (AssignmentDto a : result.assignments()) {
            map.put(a.laneId(), a.transporterId());
        }

        assertEquals(1L, map.get(1L)); // X
        assertEquals(1L, map.get(2L)); // X
        assertEquals(2L, map.get(3L)); // Y
        assertEquals(2L, map.get(4L)); // Y
        assertEquals(3L, map.get(5L)); // Z

        assertEquals(List.of(1L, 2L, 3L), result.selectedTransporters());
    }

    @Test
    @DisplayName("PDF Test Case 2: K=4 -> cost 7450 (transporter W takes lane 4 for 1750)")
    void tc2_k4_expectedCost7450() {
        var result = solver.solve(tc2LaneIds(), tc2TransporterIds(), buildTc2Matrix(), 4);

        assertEquals(7450L, result.totalCost());
        Map<Long, Long> map = new HashMap<>();
        for (AssignmentDto a : result.assignments()) {
            map.put(a.laneId(), a.transporterId());
        }

        assertEquals(1L, map.get(1L)); // X takes L1 (1000)
        assertEquals(1L, map.get(2L)); // X takes L2 (1500)
        assertEquals(2L, map.get(3L)); // Y takes L3 (2000)
        assertEquals(4L, map.get(4L)); // W takes L4 (1750)
        assertEquals(3L, map.get(5L)); // Z takes L5 (1200)

        assertEquals(List.of(1L, 2L, 3L, 4L), result.selectedTransporters());
    }

    @Test
    @DisplayName("PDF Test Case 2: K=10 (exceeds 4 transporters) -> clamps to 4, cost 7450")
    void tc2_k10_clampsTo4_expectedCost7450() {
        var result = solver.solve(tc2LaneIds(), tc2TransporterIds(), buildTc2Matrix(), 10);

        assertEquals(7450L, result.totalCost());
        assertEquals(List.of(1L, 2L, 3L, 4L), result.selectedTransporters());
    }

    @Test
    @DisplayName("PDF Test Case 2: K=2 -> infeasible (422), needs at least 3")
    void tc2_k2_infeasible() {
        var ex = assertThrows(AssignmentSolver.InfeasibleException.class,
                () -> solver.solve(tc2LaneIds(), tc2TransporterIds(), buildTc2Matrix(), 2));

        assertEquals(3, ex.getMinNeeded(), "Should report that at least 3 transporters are needed");
        assertTrue(ex.getMessage().contains("Minimum required: 3"), "Error message must report min needed");
    }

    @Test
    @DisplayName("PDF 10-lane example: K=3 -> infeasible, needs at least 5")
    void tenLane_k3_infeasible() {
        // Lanes 1-10, Transporters T1-T7 from the PDF introductory example
        List<Long> laneIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        List<Long> transporterIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);

        long[][] cost = {
                {5000, 6000, INF, INF, INF, INF, INF, INF, INF, INF},   // T1: L1, L2
                {INF, INF, 7000, 4000, INF, INF, INF, INF, INF, INF},   // T2: L3, L4
                {INF, INF, INF, INF, 3000, 2000, INF, INF, INF, INF},   // T3: L5, L6
                {INF, INF, INF, INF, INF, INF, 8000, 5000, INF, INF},   // T4: L7, L8
                {INF, INF, INF, INF, INF, INF, INF, INF, 4500, 3500},   // T5: L9, L10
                {5500, 6200, INF, INF, INF, INF, INF, INF, INF, INF},   // T6: L1, L2
                {INF, INF, 7500, 4500, INF, INF, INF, INF, INF, INF}    // T7: L3, L4
        };

        var ex = assertThrows(AssignmentSolver.InfeasibleException.class,
                () -> solver.solve(laneIds, transporterIds, cost, 3));

        assertEquals(5, ex.getMinNeeded(), "Minimum transporters needed to cover all 10 lanes must be 5");
        assertTrue(ex.getMessage().contains("Minimum required: 5"));
    }

    @Test
    @DisplayName("Load transporter_assignment_data.csv, convert to input format, and solve")
    void csvData_loadAndSolve() throws Exception {
        // Read CSV from classpath or project root
        InputStream is = getClass().getResourceAsStream("/transporter_assignment_data.csv");
        if (is == null) {
            Path p = Path.of("transporter_assignment_data.csv");
            if (!Files.exists(p)) {
                p = Path.of("../transporter_assignment_data.csv");
            }
            is = Files.newInputStream(p);
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        }

        assertFalse(lines.isEmpty(), "CSV lines should not be empty");

        // Parse header: Trade Lane,T1,T2,T3,T4,T5,T6,T7
        String[] header = lines.get(0).split(",");
        int numTransporters = header.length - 1;
        List<Long> transporterIds = new ArrayList<>();
        for (int i = 1; i <= numTransporters; i++) {
            transporterIds.add((long) i);
        }

        int numLanes = lines.size() - 1;
        List<Long> laneIds = new ArrayList<>();
        for (int i = 1; i <= numLanes; i++) {
            laneIds.add((long) i);
        }

        long[][] costMatrix = new long[numTransporters][numLanes];
        for (int l = 0; l < numLanes; l++) {
            String[] parts = lines.get(l + 1).split(",");
            for (int t = 0; t < numTransporters; t++) {
                costMatrix[t][l] = Long.parseLong(parts[t + 1].trim());
            }
        }

        // Solve with K = 3
        var result = solver.solve(laneIds, transporterIds, costMatrix, 3);

        assertNotNull(result);
        assertEquals(numLanes, result.assignments().size(), "All 9 lanes must be assigned");
        assertTrue(result.selectedTransporters().size() <= 3, "At most 3 transporters selected");
        assertTrue(result.selectedTransporters().size() >= 1, "At least 1 transporter selected");

        // Verify total cost matches sum of assigned quotes
        Map<Long, Long> map = new HashMap<>();
        for (AssignmentDto a : result.assignments()) {
            map.put(a.laneId(), a.transporterId());
        }

        long computedCost = 0;
        for (int l = 0; l < numLanes; l++) {
            Long assignedTransporterId = map.get((long) (l + 1));
            assertNotNull(assignedTransporterId);
            int tIdx = transporterIds.indexOf(assignedTransporterId);
            long quote = costMatrix[tIdx][l];
            computedCost += quote;
        }

        assertEquals(result.totalCost(), computedCost, "Total cost should equal sum of assigned lane quotes");
    }

    @Test
    @DisplayName("Guard: > 20 transporters throws SolverException")
    void moreThan20Transporters_throwsSolverException() {
        List<Long> lanes = List.of(1L, 2L);
        List<Long> transporters = new ArrayList<>();
        for (long i = 1; i <= 21; i++) {
            transporters.add(i);
        }
        long[][] cost = new long[21][2];

        var ex = assertThrows(AssignmentSolver.SolverException.class,
                () -> solver.solve(lanes, transporters, cost, 3));

        assertTrue(ex.getMessage().contains("Exact solver supports at most 20 transporters"));
        assertTrue(ex.getMessage().contains("ILP approach"));
    }

    @Test
    @DisplayName("Tiebreaker: on equal cost, prefer solution using more transporters")
    void tiebreaker_prefersMoreTransporters() {
        // 2 lanes, 3 transporters
        // T1: L1=10, L2=10 (1 transporter covers both, cost 20)
        // T2: L1=10, L2=INF
        // T3: L1=INF, L2=10
        // (T2 + T3 covers both, cost 10 + 10 = 20, but uses 2 transporters)
        // With K=2, cost is 20 for {T1} and 20 for {T2, T3}.
        // The tiebreaker must pick {T2, T3} because it uses 2 transporters instead of 1.
        List<Long> lanes = List.of(1L, 2L);
        List<Long> transporters = List.of(1L, 2L, 3L);
        long[][] cost = {
                {10, 10}, // T1 (id 1)
                {10, INF}, // T2 (id 2)
                {INF, 10}  // T3 (id 3)
        };

        var result = solver.solve(lanes, transporters, cost, 2);

        assertEquals(20L, result.totalCost());
        assertEquals(2, result.selectedTransporters().size(), "Should select 2 transporters to maximize usage on tie");
        assertTrue(result.selectedTransporters().containsAll(List.of(2L, 3L)));
    }
}

