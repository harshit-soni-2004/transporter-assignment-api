package com.transporter.assignment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lanes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lane {

    @Id
    private Long id;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @OneToMany(mappedBy = "lane", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LaneQuote> quotes = new ArrayList<>();
}
