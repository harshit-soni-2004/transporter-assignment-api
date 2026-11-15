package com.transporter.assignment.service;

import com.transporter.assignment.dto.*;
import com.transporter.assignment.entity.*;
import com.transporter.assignment.exception.*;
import com.transporter.assignment.repository.*;
import com.transporter.assignment.solver.AssignmentSolver;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransporterService {

    private final LaneRepository laneRepository;
    private final TransporterRepository transporterRepository;
    private final LaneQuoteRepository laneQuoteRepository;
    private final AssignmentSolver assignmentSolver;
    private final EntityManager entityManager;

    @Transactional
    public InputResponse saveInput(InputRequest request) {
        if (request == null || request.lanes() == null || request.lanes().isEmpty()) {
            throw new AppException("lanes list must not be empty.", HttpStatus.BAD_REQUEST.value());
        }
        if (request.transporters() == null || request.transporters().isEmpty()) {
            throw new AppException("transporters list must not be empty.", HttpStatus.BAD_REQUEST.value());
        }

        // 1. Validate no duplicate lane IDs
        List<Long> laneIds = request.lanes().stream().map(LaneDto::id).collect(Collectors.toList());
        Set<Long> uniqueLaneIds = new HashSet<>(laneIds);
        if (uniqueLaneIds.size() != laneIds.size()) {
            throw new AppException("Duplicate lane IDs found in input.", HttpStatus.BAD_REQUEST.value());
        }

        // 2. Validate no duplicate transporter IDs
        List<Long> transporterIds = request.transporters().stream()
                .map(TransporterDto::id).collect(Collectors.toList());
        Set<Long> uniqueTransporterIds = new HashSet<>(transporterIds);
        if (uniqueTransporterIds.size() != transporterIds.size()) {
            throw new AppException("Duplicate transporter IDs found in input.", HttpStatus.BAD_REQUEST.value());
        }

        // 3. Collect valid lane IDs; validate every laneQuote's laneId exists
        for (TransporterDto tDto : request.transporters()) {
            if (tDto.laneQuotes() != null) {
                for (LaneQuoteDto lqDto : tDto.laneQuotes()) {
                    if (!uniqueLaneIds.contains(lqDto.laneId())) {
                        throw new AppException(
                                "Lane ID " + lqDto.laneId() + " referenced in quotes for transporter " +
                                tDto.id() + " does not exist in the submitted lanes.",
                                HttpStatus.BAD_REQUEST.value());
                    }
                    // 4. Validate every quote > 0
                    if (lqDto.quote() <= 0) {
                        throw new AppException(
                                "Quote for lane " + lqDto.laneId() + " by transporter " + tDto.id() +
                                " must be greater than 0.",
                                HttpStatus.BAD_REQUEST.value());
                    }
                }
            }
        }

        // 5. Validate every lane has at least one quote
        Map<Long, Boolean> laneHasQuote = new HashMap<>();
        for (Long lid : laneIds) laneHasQuote.put(lid, false);
        for (TransporterDto tDto : request.transporters()) {
            if (tDto.laneQuotes() != null) {
                for (LaneQuoteDto lqDto : tDto.laneQuotes()) {
                    laneHasQuote.put(lqDto.laneId(), true);
                }
            }
        }
        for (Map.Entry<Long, Boolean> entry : laneHasQuote.entrySet()) {
            if (!entry.getValue()) {
                throw new AppException(
                        "Lane " + entry.getKey() + " has no quotes from any transporter.",
                        HttpStatus.BAD_REQUEST.value());
            }
        }

        // 6. Delete all previous data and clear persistence context to support dataset replacement
        laneQuoteRepository.deleteAllInBatch();
        transporterRepository.deleteAllInBatch();
        laneRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();

        // Save lanes first to get persisted Lane entities
        Map<Long, Lane> savedLanes = new HashMap<>();
        for (LaneDto lDto : request.lanes()) {
            Lane lane = Lane.builder()
                    .id(lDto.id())
                    .origin(lDto.origin())
                    .destination(lDto.destination())
                    .build();
            entityManager.persist(lane);
            savedLanes.put(lDto.id(), lane);
        }

        // Build and persist transporters with their LaneQuote children
        for (TransporterDto tDto : request.transporters()) {
            Transporter transporter = Transporter.builder()
                    .id(tDto.id())
                    .name(tDto.name())
                    .build();

            if (tDto.laneQuotes() != null) {
                for (LaneQuoteDto lqDto : tDto.laneQuotes()) {
                    LaneQuote laneQuote = LaneQuote.builder()
                            .lane(savedLanes.get(lqDto.laneId()))
                            .transporter(transporter)
                            .quote(lqDto.quote())
                            .build();
                    transporter.getLaneQuotes().add(laneQuote);
                }
            }

            entityManager.persist(transporter);
        }
        entityManager.flush();

        log.debug("Input saved: {} lanes, {} transporters", laneIds.size(), transporterIds.size());

        // 7. Return success
        return new InputResponse("success", "Input data saved successfully.");
    }

    @Transactional(readOnly = true)
    public AssignmentResponse computeAssignment(AssignmentRequest request) {
        // 1. Load lanes; if empty → throw NoInputDataException
        List<Lane> lanes = laneRepository.findAll();
        if (lanes.isEmpty()) {
            throw new NoInputDataException();
        }

        // 2. Load transporters with quotes (avoid N+1)
        List<Transporter> transporters = transporterRepository.findAllWithQuotes();

        // 3. Sort lane IDs and transporter IDs
        List<Long> laneIds = lanes.stream()
                .map(Lane::getId)
                .sorted()
                .collect(Collectors.toList());
        List<Long> transporterIds = transporters.stream()
                .map(Transporter::getId)
                .sorted()
                .collect(Collectors.toList());

        int n = transporterIds.size();
        int L = laneIds.size();

        // Validate maxTransporters >= 1 (returns 400 if invalid)
        if (request.maxTransporters() < 1) {
            throw new AppException(
                    "maxTransporters must be at least 1",
                    HttpStatus.BAD_REQUEST.value());
        }

        // Clamp maxTransporters to available transporters (n) since requirement is "at most K"
        int effectiveMaxTransporters = Math.min(request.maxTransporters(), n);

        // Build index maps
        Map<Long, Integer> laneIndex = new HashMap<>();
        for (int i = 0; i < L; i++) laneIndex.put(laneIds.get(i), i);

        Map<Long, Integer> transporterIndex = new HashMap<>();
        for (int i = 0; i < n; i++) transporterIndex.put(transporterIds.get(i), i);

        // 4. Build costMatrix[t][l]
        long[][] costMatrix = new long[n][L];
        for (long[] row : costMatrix) Arrays.fill(row, Long.MAX_VALUE);

        for (Transporter transporter : transporters) {
            int tIdx = transporterIndex.get(transporter.getId());
            for (LaneQuote lq : transporter.getLaneQuotes()) {
                int lIdx = laneIndex.get(lq.getLane().getId());
                costMatrix[tIdx][lIdx] = lq.getQuote();
            }
        }

        // 5. Call solver
        AssignmentSolver.SolverResult result = assignmentSolver.solve(
                laneIds, transporterIds, costMatrix, effectiveMaxTransporters);

        log.debug("Assignment computed: totalCost={}, transporters used={}",
                result.totalCost(), result.selectedTransporters().size());

        // 6. Return response
        return new AssignmentResponse(
                "success",
                result.totalCost(),
                result.assignments(),
                result.selectedTransporters()
        );
    }
}
