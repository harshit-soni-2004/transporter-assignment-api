package com.transporter.assignment.dto;

import java.util.List;

public record AssignmentResponse(
        String status,
        long totalCost,
        List<AssignmentDto> assignments,
        List<Long> selectedTransporters
) {}
