package com.transporter.assignment.controller;

import com.transporter.assignment.dto.*;
import com.transporter.assignment.service.TransporterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transporters")
@Tag(name = "Transporter Assignment", description = "APIs for submitting lane data and computing optimal transporter assignments")
@RequiredArgsConstructor
public class TransporterController {

    private final TransporterService transporterService;

    @PostMapping("/input")
    @Operation(
            summary = "Submit lanes and quotes",
            description = "Replaces any existing dataset. Validates uniqueness and coverage."
    )
    public ResponseEntity<InputResponse> submitInput(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Lanes and transporter quotes input payload",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "SampleInput",
                                    summary = "5 lanes with transporter quotes",
                                    value = """
                                            {
                                              "lanes": [
                                                {"id": 1, "origin": "Mumbai", "destination": "Delhi"},
                                                {"id": 2, "origin": "Delhi", "destination": "Bangalore"},
                                                {"id": 3, "origin": "Chennai", "destination": "Kolkata"},
                                                {"id": 4, "origin": "Pune", "destination": "Hyderabad"},
                                                {"id": 5, "origin": "Ahmedabad", "destination": "Jaipur"}
                                              ],
                                              "transporters": [
                                                {
                                                  "id": 1,
                                                  "name": "Transporter T1",
                                                  "laneQuotes": [
                                                    {"laneId": 1, "quote": 20835},
                                                    {"laneId": 2, "quote": 10512},
                                                    {"laneId": 3, "quote": 22105},
                                                    {"laneId": 4, "quote": 42481},
                                                    {"laneId": 5, "quote": 19862}
                                                  ]
                                                },
                                                {
                                                  "id": 2,
                                                  "name": "Transporter T2",
                                                  "laneQuotes": [
                                                    {"laneId": 1, "quote": 48844},
                                                    {"laneId": 2, "quote": 31326},
                                                    {"laneId": 3, "quote": 18640},
                                                    {"laneId": 4, "quote": 45828},
                                                    {"laneId": 5, "quote": 18297}
                                                  ]
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody InputRequest request) {
        return ResponseEntity.ok(transporterService.saveInput(request));
    }

    @PostMapping("/assignment")
    @Operation(
            summary = "Compute optimal assignment",
            description = "Runs exact bitmask search over transporter subsets. Returns the minimum-cost assignment."
    )
    public ResponseEntity<AssignmentResponse> computeAssignment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Optimization request specifying max transporters",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "SampleAssignment",
                                    summary = "Limit to at most 3 transporters",
                                    value = "{\"maxTransporters\": 3}"
                            )
                    )
            )
            @Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.ok(transporterService.computeAssignment(request));
    }
}
