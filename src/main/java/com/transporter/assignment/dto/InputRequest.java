package com.transporter.assignment.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InputRequest(
        @NotEmpty(message = "lanes must not be empty")
        List<LaneDto> lanes,

        @NotEmpty(message = "transporters must not be empty")
        List<TransporterDto> transporters
) {}
