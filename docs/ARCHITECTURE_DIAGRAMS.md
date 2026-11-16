# System & Software Architecture Diagrams
**Project:** Transporter Assignment on Lanes API  
**Author:** Harshit Soni  

You can use these diagrams in your Loom walkthrough, GitHub README, or design discussions.

---

## 1. High-Level Cloud & Infrastructure Architecture

This diagram shows how requests flow from the client across the public internet through Render's edge down to Neon Serverless PostgreSQL.

```mermaid
flowchart TD
    subgraph ClientLayer["Client & Consumer Layer"]
        A["Swagger UI / Web Browser"]
        B["Automated Test / curl / Postman"]
    end

    subgraph RenderCloud["Render Cloud Platform (US East - Ohio)"]
        Edge["Cloudflare Edge / TLS Termination"]
        
        subgraph DockerContainer["Docker Container (Eclipse Temurin JRE 17)"]
            Tomcat["Embedded Apache Tomcat (Port 10000)"]
            App["Spring Boot 3.3.0 Application\n(Profile: prod)"]
            JVM["JVM Tuning:\n-XX:MaxRAMPercentage=70\n-XX:+UseSerialGC"]
        end
    end

    subgraph NeonCloud["Neon Serverless PostgreSQL (AWS us-east-1)"]
        NeonProxy["Neon Connection Pooler / TLS Proxy\n(sslmode=require)"]
        PostgresDB[("Lakebase Postgres 18\nDatabase: neondb\nSchema: lane, transporter, lane_quotes")]
    end

    A -->|"HTTPS / JSON"| Edge
    B -->|"HTTPS / JSON"| Edge
    Edge --> Tomcat
    Tomcat --> App
    App -.-> JVM
    App -->|"JDBC over TLS (HikariCP)"| NeonProxy
    NeonProxy --> PostgresDB

    classDef client fill:#e1f5fe,stroke:#0288d1,stroke-width:2px;
    classDef render fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px;
    classDef neon fill:#e8f5e9,stroke:#388e3c,stroke-width:2px;
    class A,B client;
    class Edge,Tomcat,App,JVM render;
    class NeonProxy,PostgresDB neon;
```

---

## 2. Software Layer Architecture (Separation of Concerns)

This diagram highlights the clean 4-tier modular decoupling in your Java codebase.

```mermaid
flowchart TD
    subgraph Presentation["1. Presentation / HTTP Layer"]
        Ctrl["TransporterController\n- Routes /input and /assignment\n- Bean Validation (@Valid)\n- OpenAPI / Swagger Annotations"]
        HealthCtrl["HealthController\n- Routes /health"]
        ExHandler["GlobalExceptionHandler\n- Maps 400 Bad Request\n- Maps 409 Conflict\n- Maps 422 Unprocessable Entity"]
    end

    subgraph Business["2. Business & Orchestration Layer"]
        Svc["TransporterService\n- @Transactional demarcation\n- Business invariant checks\n- Builds costMatrix[t][l] from entities\n- Assembles final AssignmentResponse"]
    end

    subgraph Optimization["3. Pure Algorithm Layer (Isolated POJO)"]
        Solver["AssignmentSolver\n- Zero Spring/JPA dependencies\n- computeMinTransportersNeeded()\n- 2^n Bitmask Enumeration with POPCNT\n- Tie-breaking logic (load distribution)"]
    end

    subgraph Persistence["4. Persistence Layer"]
        LaneRepo["LaneRepository"]
        TransRepo["TransporterRepository\n(findAllWithQuotes - JOIN FETCH)"]
        QuoteRepo["LaneQuoteRepository"]
    end

    subgraph Database["Database"]
        DB[("PostgreSQL (prod) / H2 (dev/test)")]
    end

    Ctrl --> Svc
    HealthCtrl --> Svc
    Ctrl -.-> ExHandler
    Svc -->|"laneIds, costMatrix[][], K"| Solver
    Solver -->|"SolverResult"| Svc
    Svc --> LaneRepo
    Svc --> TransRepo
    Svc --> QuoteRepo
    LaneRepo --> DB
    TransRepo --> DB
    QuoteRepo --> DB
```

---

## 3. End-to-End Sequence Diagram: `/assignment` Request

This diagram illustrates how an assignment request is validated, loaded from the database, optimized by the solver, and returned.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client (Swagger / curl)
    participant C as TransporterController
    participant V as Hibernate Validator
    participant S as TransporterService
    participant R as TransporterRepository
    participant Opt as AssignmentSolver
    participant DB as Neon PostgreSQL

    Client->>C: POST /api/v1/transporters/assignment {"maxTransporters": 3}
    C->>V: Validate @Min(1)
    V-->>C: Validated OK
    
    C->>S: computeAssignment(request)
    S->>R: findAllWithQuotes()
    R->>DB: SELECT t FROM Transporter t LEFT JOIN FETCH t.laneQuotes
    DB-->>R: Transporter & Quote entities
    R-->>S: List<Transporter>
    
    Note over S: Builds sorted costMatrix[transporter][lane]
    
    S->>Opt: solve(laneIds, transporterIds, costMatrix, k=3)
    
    alt Infeasible (minNeeded > k)
        Opt-->>S: throw InfeasibleException(minNeeded)
        S-->>C: Bubble up
        C-->>Client: 422 Unprocessable Entity {"message": "Minimum required: 3"}
    else Feasible Solution Found
        Note over Opt: Bitmask loop (1 to 2^n)<br/>Prune if popcount > k<br/>Per-lane min cost & tie-breaker
        Opt-->>S: SolverResult(totalCost=7500, assignments, selectedTransporters)
        S-->>C: AssignmentResponse DTO
        C-->>Client: 200 OK {"status": "success", "totalCost": 7500, ...}
    end
```

---

## 4. Entity-Relationship (ER) Schema

This diagram visualizes the database tables and relational constraints in PostgreSQL.

```mermaid
erDiagram
    LANES ||--o{ LANE_QUOTES : "quoted_in"
    TRANSPORTERS ||--o{ LANE_QUOTES : "submits"

    LANES {
        bigint id PK "Lane ID"
        varchar origin "Origin city"
        varchar destination "Destination city"
    }

    TRANSPORTERS {
        bigint id PK "Transporter ID"
        varchar name "Transporter name"
    }

    LANE_QUOTES {
        bigint id PK "Auto-generated ID"
        bigint transporter_id FK "References TRANSPORTERS(id)"
        bigint lane_id FK "References LANES(id)"
        bigint quote "Positive price quote (> 0)"
    }
```
