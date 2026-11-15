package com.transporter.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransporterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 5 lanes, 7 transporters matching PDF Test Case 1
    private static final String TC1_INPUT = """
            {
              "lanes": [
                {"id": 1, "origin": "Mumbai", "destination": "Delhi"},
                {"id": 2, "origin": "Delhi", "destination": "Bangalore"},
                {"id": 3, "origin": "Chennai", "destination": "Kolkata"},
                {"id": 4, "origin": "Pune", "destination": "Hyderabad"},
                {"id": 5, "origin": "Ahmedabad", "destination": "Jaipur"}
              ],
              "transporters": [
                {"id": 1, "name": "T1", "laneQuotes": [{"laneId":1,"quote":20835},{"laneId":2,"quote":10512},{"laneId":3,"quote":22105},{"laneId":4,"quote":42481},{"laneId":5,"quote":19862}]},
                {"id": 2, "name": "T2", "laneQuotes": [{"laneId":1,"quote":48844},{"laneId":2,"quote":31326},{"laneId":3,"quote":18640},{"laneId":4,"quote":45828},{"laneId":5,"quote":18297}]},
                {"id": 3, "name": "T3", "laneQuotes": [{"laneId":1,"quote":39020},{"laneId":2,"quote":20648},{"laneId":3,"quote":31438},{"laneId":4,"quote":36447},{"laneId":5,"quote":12789}]},
                {"id": 4, "name": "T4", "laneQuotes": [{"laneId":1,"quote":14400},{"laneId":2,"quote":44514},{"laneId":3,"quote":14316},{"laneId":4,"quote":10678},{"laneId":5,"quote":13032}]},
                {"id": 5, "name": "T5", "laneQuotes": [{"laneId":1,"quote":11601},{"laneId":2,"quote":19760},{"laneId":3,"quote":40870},{"laneId":4,"quote":20635},{"laneId":5,"quote":26421}]},
                {"id": 6, "name": "T6", "laneQuotes": [{"laneId":1,"quote":35095},{"laneId":2,"quote":12494},{"laneId":3,"quote":17808},{"laneId":4,"quote":36210},{"laneId":5,"quote":39444}]},
                {"id": 7, "name": "T7", "laneQuotes": [{"laneId":1,"quote":26070},{"laneId":2,"quote":41098},{"laneId":3,"quote":20932},{"laneId":4,"quote":16897},{"laneId":5,"quote":27938}]}
              ]
            }
            """;

    // TC2 input: 5 lanes, 4 transporters (X, Y, Z, W)
    private static final String TC2_INPUT = """
            {
              "lanes": [
                {"id": 1, "origin": "Chandigarh", "destination": "Shimla"},
                {"id": 2, "origin": "Agra", "destination": "Kanpur"},
                {"id": 3, "origin": "Varanasi", "destination": "Gorakhpur"},
                {"id": 4, "origin": "Amritsar", "destination": "Ludhiana"},
                {"id": 5, "origin": "Coimbatore", "destination": "Madurai"}
              ],
              "transporters": [
                {"id": 1, "name": "Transporter X", "laneQuotes": [{"laneId": 1, "quote": 1000}, {"laneId": 2, "quote": 1500}]},
                {"id": 2, "name": "Transporter Y", "laneQuotes": [{"laneId": 3, "quote": 2000}, {"laneId": 4, "quote": 1800}]},
                {"id": 3, "name": "Transporter Z", "laneQuotes": [{"laneId": 5, "quote": 1200}]},
                {"id": 4, "name": "Transporter W", "laneQuotes": [{"laneId": 1, "quote": 1100}, {"laneId": 4, "quote": 1750}]}
              ]
            }
            """;

    @Test
    @DisplayName("Health endpoint returns UP")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Calling /assignment before submitting data returns 409 Conflict")
    void assignmentBeforeInput_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("No input data has been submitted yet")));
    }

    @Test
    @DisplayName("Submit TC1 input and compute assignment with K=3 -> 200 OK, cost 60139")
    void submitInput_thenAssign_tc1_k3() throws Exception {
        // Submit Input
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC1_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Input data saved successfully."));

        // Compute Assignment K=3
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.totalCost").value(60139))
                .andExpect(jsonPath("$.assignments", hasSize(5)))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 4, 5)));
    }

    @Test
    @DisplayName("Submit TC2 input and compute assignment with K=2 -> 422 Unprocessable Entity")
    void submitTc2_k2_returns422Infeasible() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC2_INPUT))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Minimum required: 3")));
    }

    @Test
    @DisplayName("Validation: maxTransporters < 1 returns 400 Bad Request")
    void assignment_invalidMaxTransporters_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Validation: duplicate lane IDs returns 400 Bad Request")
    void submitInput_duplicateLaneId_returns400() throws Exception {
        String duplicateLanesInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"},
                    {"id": 1, "origin": "C", "destination": "D"}
                  ],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 100}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateLanesInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Duplicate lane IDs")));
    }

    @Test
    @DisplayName("Validation: quote with nonexistent laneId returns 400 Bad Request")
    void submitInput_quoteForNonexistentLane_returns400() throws Exception {
        String invalidQuoteInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"}
                  ],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 999, "quote": 100}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidQuoteInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("does not exist in the submitted lanes")));
    }

    @Test
    @DisplayName("Validation: negative quote returns 400 Bad Request")
    void submitInput_negativeQuote_returns400() throws Exception {
        String negativeQuoteInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"}
                  ],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": -50}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeQuoteInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("must be greater than 0")));
    }

    @Test
    @DisplayName("Validation: lane with zero quotes returns 400 Bad Request")
    void submitInput_laneWithoutQuotes_returns400() throws Exception {
        String laneWithoutQuoteInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"},
                    {"id": 2, "origin": "C", "destination": "D"}
                  ],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 100}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(laneWithoutQuoteInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("has no quotes from any transporter")));
    }

    @Test
    @DisplayName("Validation: duplicate transporter IDs returns 400 Bad Request")
    void submitInput_duplicateTransporterId_returns400() throws Exception {
        String duplicateTransportersInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"}
                  ],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 100}]},
                    {"id": 1, "name": "T1-Duplicate", "laneQuotes": [{"laneId": 1, "quote": 120}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateTransportersInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Duplicate transporter IDs")));
    }

    @Test
    @DisplayName("Validation: zero quote returns 400 Bad Request")
    void submitInput_zeroQuote_returns400() throws Exception {
        String zeroQuoteInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"}
                  ],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 0}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(zeroQuoteInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("must be greater than 0")));
    }

    @Test
    @DisplayName("Validation: empty lanes list returns 400 Bad Request")
    void submitInput_emptyLanesList_returns400() throws Exception {
        String emptyLanesInput = """
                {
                  "lanes": [],
                  "transporters": [
                    {"id": 1, "name": "T1", "laneQuotes": [{"laneId": 1, "quote": 100}]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyLanesInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Validation: empty transporters list returns 400 Bad Request")
    void submitInput_emptyTransportersList_returns400() throws Exception {
        String emptyTransportersInput = """
                {
                  "lanes": [
                    {"id": 1, "origin": "A", "destination": "B"}
                  ],
                  "transporters": []
                }
                """;

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyTransportersInput))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Clamp: maxTransporters larger than total transporters clamps to total and returns 200 with cost 7450")
    void assignment_maxTransportersLargerThanTotal_clampsAndReturns200() throws Exception {
        // Submit TC2 with 4 transporters
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC2_INPUT))
                .andExpect(status().isOk());

        // Ask for maxTransporters = 10 (exceeds 4 transporters, should clamp to 4)
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.totalCost").value(7450));
    }

    @Test
    @DisplayName("Malformed JSON in /input returns 400 Bad Request with error format")
    void submitInput_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed: json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Malformed JSON")));
    }

    @Test
    @DisplayName("Malformed JSON in /assignment returns 400 Bad Request with error format")
    void assignment_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": \"not_a_number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(containsString("Malformed JSON")));
    }

    @Test
    @DisplayName("Dataset replacement: submitting TC1 twice in a row succeeds without conflict")
    void submitInput_twiceInARow_replacesDatasetSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC1_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC1_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(60139));
    }

    @Test
    @DisplayName("Dataset replacement: submitting TC1 then TC2 correctly updates state")
    void submitInput_tc1ThenTc2_datasetReplacement() throws Exception {
        // First submit TC1
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC1_INPUT))
                .andExpect(status().isOk());

        // Then submit TC2 (replacing TC1)
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TC2_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Assignment on replaced TC2 dataset with K=3 -> cost 7500
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxTransporters\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCost").value(7500))
                .andExpect(jsonPath("$.selectedTransporters", contains(1, 2, 3)));
    }
}

