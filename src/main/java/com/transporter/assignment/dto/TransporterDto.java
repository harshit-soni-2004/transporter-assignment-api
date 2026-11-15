package com.transporter.assignment.dto;

import java.util.List;

public record TransporterDto(Long id, String name, List<LaneQuoteDto> laneQuotes) {}
