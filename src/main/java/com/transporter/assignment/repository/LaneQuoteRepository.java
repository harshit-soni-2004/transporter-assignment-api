package com.transporter.assignment.repository;

import com.transporter.assignment.entity.LaneQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LaneQuoteRepository extends JpaRepository<LaneQuote, Long> {}
