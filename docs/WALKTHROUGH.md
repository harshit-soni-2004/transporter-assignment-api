# Transporter Assignment — Technical Walkthrough

> **Audience:** This document is intended for a video walkthrough of the codebase. It
> explains every architectural decision, every request flow step, the solver algorithm in depth,
> error handling, persistence design, and known inconsistencies in the original problem PDF.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Request Flow: POST /api/v1/transporters/input](#2-request-flow-post-apiv1transportersinput)
3. [Request Flow: POST /api/v1/transporters/assignment](#3-request-flow-post-apiv1transportersassignment)
4. [The Solver: Step-by-Step with Worked Example](#4-the-solver-step-by-step-with-worked-example)
5. [Error Handling](#5-error-handling)
6. [Persistence Design](#6-persistence-design)
7. [PDF Inconsistencies](#7-pdf-inconsistencies)

---

## 1. Architecture Overview

The application is structured into four distinct, cleanly separated layers. Each layer has a single
responsibility and depends only on the layer below it.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENT  (curl / Swagger UI)                  │
└───────────────────────────────┬─────────────────────────────────────┘
                                │  HTTP JSON
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│          HTTP LAYER  —  TransporterController                       │
│                                                                     │
│  • Routes POST /api/v1/transporters/input  → submitInput()          │
│  • Routes POST /api/v1/transporters/assignment → getAssignment()    │
│  • Runs @Valid Bean Validation on every incoming request body        │
│  • Returns 200 OK with typed response records                       │
│  • Knows nothing about business rules or the database               │
└───────────────────────────────┬─────────────────────────────────────┘
                                │  Java method call
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│      BUSINESS / ORCHESTRATION LAYER  —  TransporterService          │
│                                                                     │
│  • Validates business invariants (unique IDs, positive quotes,      │
│    all lanes covered)                                               │
│  • Coordinates the @Transactional save / clear cycle               │
│  • Builds the costMatrix from JPA entities                          │
│  • Delegates the hard optimisation problem to the Solver            │
│  • Wraps the SolverResult into the API response DTO                 │
└──────────┬──────────────────────────┬───────────────────────────────┘
           │  pure Java call          │  Spring Data repository calls
           ▼                          ▼
┌──────────────────────┐  ┌──────────────────────────────────────────┐
│  ALGORITHM LAYER     │  │  PERSISTENCE LAYER                       │
│  AssignmentSolver    │  │                                          │
│                      │  │  LaneRepository                          │
│  • Pure Java class   │  │  TransporterRepository                   │
│  • No Spring, no JPA │  │  LaneQuoteRepository                     │
│  • Enumeration over  │  │       │                                  │
│    all 2^n subsets   │  │       │  JPA / Hibernate                 │
│  • Returns           │  │       ▼                                  │
│    SolverResult      │  │  ┌──────────────────────────────────┐    │
│                      │  │  │  H2 (dev/test)  /  Postgres (prod)│   │
└──────────────────────┘  │  └──────────────────────────────────┘    │
                          └──────────────────────────────────────────┘
```

### Why this separation?

| Layer | Reason for isolation |
|---|---|
| **Controller** | Keeps HTTP concerns (status codes, media types, OpenAPI annotations) out of business logic. Easy to swap REST for gRPC later. |
| **Service** | Business rules and transaction management in one place. If the algorithm changes, the service does not need to change. |
| **Solver** | Zero framework coupling means the solver is unit-testable with a plain `new AssignmentSolver()`. It can be swapped for an ILP solver without touching any Spring code. |
| **Persistence** | Standard Spring Data abstractions isolate the database vendor. Switching from H2 to Postgres is an environment-variable change, not a code change. |

---

## 2. Request Flow: POST /api/v1/transporters/input

This endpoint accepts the full dataset — lanes and transporter quotes — and replaces whatever was
stored before.

### Step-by-step

```
POST /api/v1/transporters/input
  Body: { "lanes": [...], "transporters": [...] }
```

**Step 1 — HTTP POST received by `TransporterController.submitInput()`**

Spring's `DispatcherServlet` routes the request. The method signature is:

```java
@PostMapping("/input")
public ResponseEntity<InputResponse> submitInput(@Valid @RequestBody InputRequest request)
```

**Step 2 — `@Valid` triggers Bean Validation on `InputRequest`**

Before the method body executes, Hibernate Validator runs:
- `@NotEmpty` on `lanes` → 400 if the list is null or empty
- `@NotEmpty` on `transporters` → 400 if the list is null or empty

If any constraint fails, `MethodArgumentNotValidException` is thrown and caught by
`GlobalExceptionHandler`, returning a descriptive 400 response before the service is ever called.

**Step 3 — Controller delegates to `TransporterService.saveInput(request)`**

The controller is a thin shell. It hands the validated DTO straight to the service and waits for
an `InputResponse`.

**Step 4 — Service validates business invariants**

The service applies four business-rule checks, each of which throws an `AppException(400)` on
failure:

| Check | What it verifies |
|---|---|
| Unique lane IDs | No two `LaneDto` entries have the same `id` |
| Unique transporter IDs | No two `TransporterDto` entries have the same `id` |
| `laneId` references exist | Every `LaneQuoteDto.laneId` appears in the submitted `lanes` list |
| Positive quotes | Every `quote` value is `> 0` |
| Full coverage | Every submitted lane has at least one quote |

**Step 5 — `@Transactional` block clears old data and saves new entities**

Inside a single database transaction:

```
1. laneQuoteRepository.deleteAll()   ← child rows first (FK constraint)
2. laneRepository.deleteAll()
3. transporterRepository.deleteAll()
4. laneRepository.saveAll(lanes)     ← persist Lane entities
5. for each TransporterDto:
     build Transporter + List<LaneQuote>
     (each LaneQuote.lane = already-saved Lane reference)
   transporterRepository.saveAll(transporters)  ← cascades to LaneQuote
```

Cascading `CascadeType.ALL` on `Transporter.laneQuotes` means saving a `Transporter` automatically
inserts its `LaneQuote` children.

**Step 6 — 200 `InputResponse` returned**

```json
{ "status": "success", "message": "Input data saved successfully." }
```

---

## 3. Request Flow: POST /api/v1/transporters/assignment

This endpoint reads the previously stored data and returns the optimal transporter assignment.

### Step-by-step

```
POST /api/v1/transporters/assignment
  Body: { "maxTransporters": 3 }
```

**Step 1 — HTTP POST received by `TransporterController.getAssignment()`**

```java
@PostMapping("/assignment")
public ResponseEntity<AssignmentResponse> getAssignment(@Valid @RequestBody AssignmentRequest request)
```

**Step 2 — `@Valid` validates `maxTransporters >= 1`**

`AssignmentRequest` is a record with:
```java
record AssignmentRequest(@Min(1) int maxTransporters)
```
If `maxTransporters` is 0 or negative, Bean Validation fires before the service is called → 400.

**Step 3 — Controller delegates to `TransporterService.computeAssignment(request)`**

**Step 4 — Service loads lanes and transporters (with quotes) from DB**

```java
List<Lane> lanes = laneRepository.findAll();
if (lanes.isEmpty()) throw new NoInputDataException();   // → 409

List<Transporter> transporters = transporterRepository.findAllWithQuotes();
// uses JPQL with LEFT JOIN FETCH to avoid N+1 queries
```

**Step 5 — Build the `costMatrix`**

The service constructs a 2-D `long[][]` array:

```
costMatrix[transporterIndex][laneIndex] = quote  (if the transporter quoted this lane)
                                        = Long.MAX_VALUE  (if no quote)
```

Transporter and lane IDs are sorted for determinism. The index positions in this matrix are what
the solver uses; the solver never sees entity objects or Spring beans.

**Step 6 — Call `AssignmentSolver.solve(...)`**

```java
SolverResult result = solver.solve(laneIds, transporterIds, costMatrix, request.maxTransporters());
```

The solver returns a `SolverResult` record:
```java
record SolverResult(long totalCost, List<AssignmentDto> assignments, List<Long> selectedTransporters)
```

**Step 7 — Wrap result into `AssignmentResponse` → 200**

```java
return new AssignmentResponse(
    "success",
    result.totalCost(),
    result.assignments(),
    result.selectedTransporters()
);
```

Example response for TC2, K=3:
```json
{
  "status": "success",
  "totalCost": 7500,
  "assignments": [
    { "laneId": 1, "transporterId": 1 },
    { "laneId": 2, "transporterId": 1 },
    { "laneId": 3, "transporterId": 2 },
    { "laneId": 4, "transporterId": 2 },
    { "laneId": 5, "transporterId": 3 }
  ],
  "selectedTransporters": [1, 2, 3]
}
```

---

## 4. The Solver: Step-by-Step with Worked Example

### Algorithm overview

`AssignmentSolver` uses **exact bitmask enumeration**. This is the correct approach when the number
of transporters `n` is small (≤ 20).

**Core idea:** A *subset* of transporters is represented as an integer bitmask, where bit `t` is
set if transporter `t` is included in the subset. There are `2^n` possible subsets.

For each subset `mask` where `popcount(mask) ≤ maxTransporters`:

1. For every lane, find the cheapest transporter in this subset that quoted it.
2. If any lane has no quoter in this subset → **infeasible**, skip.
3. Otherwise accumulate `totalCost` and record the assignment.

Track the **best feasible** subset:
- Primary key: **lowest `totalCost`**
- Tiebreaker: **more transporters used** (prefer distributing work)

**Complexity:** `O(2^n × L × n)` where `L` = number of lanes, `n` = number of transporters.

**Guard:** If `n > 20`, the solver throws `SolverException` (HTTP 400). At `n = 20` there are
~1 million subsets, still fast. At `n = 30` it would be ~1 billion — an ILP solver is needed.

**Pre-check:** Before the main loop, the solver computes `minNeeded` — the smallest `k` for which
any `k`-sized subset covers all lanes. If `minNeeded > maxTransporters`, it throws
`InfeasibleException` (HTTP 422) immediately, without scanning all `2^n` subsets.

---

### Worked Example — TC2: 4 transporters, 5 lanes, K = 3

#### Setup

| Transporter | id | Bit index |
|---|---|---|
| X | 1 | 0 (bit 0 = `0001`) |
| Y | 2 | 1 (bit 1 = `0010`) |
| Z | 3 | 2 (bit 2 = `0100`) |
| W | 4 | 3 (bit 3 = `1000`) |

#### Quote matrix (INF = `Long.MAX_VALUE`, not quoted)

```
              L1     L2     L3     L4     L5
X  (index 0): 1000   1500   INF    INF    INF
Y  (index 1): INF    INF    2000   1800   INF
Z  (index 2): INF    INF    INF    INF    1200
W  (index 3): 1100   INF    INF    1750   INF
```

#### Enumerate subsets (mask in binary, bit 0 = X, bit 3 = W)

The loop runs `mask` from `1` to `15` (`2^4 - 1`). We show the significant ones.

---

**mask = 0001 (binary) → `{X}` — 1 transporter ≤ K=3 ✓**

| Lane | Cheapest in {X} | Cost |
|------|----------------|------|
| L1   | X → 1000       | 1000 |
| L2   | X → 1500       | 1500 |
| L3   | _(nobody)_     | —    |

L3 has no quoter in {X} → **INFEASIBLE**, skip.

---

**mask = 0011 (binary) → `{X, Y}` — 2 transporters ≤ K=3 ✓**

| Lane | Cheapest in {X, Y}  | Cost |
|------|---------------------|------|
| L1   | X → 1000            | 1000 |
| L2   | X → 1500            | 1500 |
| L3   | Y → 2000            | 2000 |
| L4   | Y → 1800            | 1800 |
| L5   | _(nobody)_          | —    |

L5 has no quoter → **INFEASIBLE**, skip.

---

**mask = 0111 (binary) → `{X, Y, Z}` — 3 transporters = K ✓**

| Lane | Cheapest in {X, Y, Z} | Cost |
|------|-----------------------|------|
| L1   | X → 1000              | 1000 |
| L2   | X → 1500              | 1500 |
| L3   | Y → 2000              | 2000 |
| L4   | Y → 1800              | 1800 |
| L5   | Z → 1200              | 1200 |

All lanes covered. `totalCost = 1000 + 1500 + 2000 + 1800 + 1200 = **7500**` ✓

This is the first feasible solution. `bestCost = 7500`, `bestUsed = 3`.

---

**mask = 1011 (binary) → `{X, Y, W}` — 3 transporters = K ✓**

| Lane | Cheapest in {X, Y, W}        | Cost |
|------|------------------------------|------|
| L1   | X→1000, W→1100 → X wins     | 1000 |
| L2   | X → 1500                    | 1500 |
| L3   | Y → 2000                    | 2000 |
| L4   | Y→1800, W→1750 → W wins     | 1750 |
| L5   | _(nobody)_                  | —    |

L5 has no quoter → **INFEASIBLE**, skip.

---

**mask = 1101 (binary) → `{X, Z, W}` — 3 transporters = K ✓**

| Lane | Cheapest in {X, Z, W}  | Cost |
|------|------------------------|------|
| L1   | X→1000, W→1100 → X    | 1000 |
| L2   | X → 1500              | 1500 |
| L3   | _(nobody)_            | —    |

L3 has no quoter → **INFEASIBLE**, skip.

---

**mask = 1111 (binary) → `{X, Y, Z, W}` — 4 transporters > K=3**

`Integer.bitCount(1111₂) = 4 > 3` → **SKIPPED** immediately.

---

#### Result for K = 3

Only one feasible solution was found: `{X, Y, Z}` with `totalCost = 7500`.

```
L1 → X (1000)
L2 → X (1500)
L3 → Y (2000)
L4 → Y (1800)
L5 → Z (1200)
```

---

### Bonus: What changes at K = 4?

With `maxTransporters = 4`, mask `1111` is now allowed:

**mask = 1111 → `{X, Y, Z, W}` — 4 transporters ≤ K=4 ✓**

| Lane | Cheapest in {X, Y, Z, W}     | Cost |
|------|------------------------------|------|
| L1   | X→1000, W→1100 → X wins     | 1000 |
| L2   | X → 1500                    | 1500 |
| L3   | Y → 2000                    | 2000 |
| L4   | Y→1800, W→1750 → **W wins** | 1750 |
| L5   | Z → 1200                    | 1200 |

`totalCost = 1000 + 1500 + 2000 + 1750 + 1200 = **7450**`

Comparison: `7450 < 7500 (previous best)` → **7450 wins**. `bestCost` updated to 7450, all 4
transporters are selected.

#### Tiebreaker explained

If two subsets produce the **same `totalCost`**, the solver prefers the one with **more transporters
used** (i.e. `bestUsed`). This distributes work more evenly rather than concentrating lanes on
fewer transporters. The comparison in code:

```java
if (totalCost < bestCost || (totalCost == bestCost && used > bestUsed)) {
    bestCost = totalCost;
    bestUsed = used;
    bestAssignment = assignment.clone();
}
```

---

## 5. Error Handling

All exceptions are centralised in `GlobalExceptionHandler`, annotated with `@RestControllerAdvice`.
This means no controller needs try/catch blocks.

### Exception → HTTP status mapping

| Exception class | HTTP Status | Trigger |
|---|---|---|
| `MethodArgumentNotValidException` | **400 Bad Request** | Bean Validation failure (`@Valid`, `@Min`, `@NotEmpty`) |
| `AppException` (and subclasses) | **configurable** (400 / 409) | Business rule violations in the service |
| `NoInputDataException` | **409 Conflict** | Assignment requested when no input has been stored |
| `AssignmentSolver.InfeasibleException` | **422 Unprocessable Entity** | No feasible assignment exists for the given `maxTransporters` |
| `AssignmentSolver.SolverException` | **400 Bad Request** | `n > 20` transporters — exact solver would be too slow |
| `Exception` (catch-all) | **500 Internal Server Error** | Unexpected runtime errors |

### Response format

Every error response uses the same `ErrorResponse` record:

```json
{
  "status": "error",
  "message": "Human-readable description of the problem"
}
```

### Why `@RestControllerAdvice`?

- **DRY**: error formatting logic is written once, not in every endpoint.
- **Consistent**: every error, regardless of origin, uses the same JSON schema.
- **Testable**: the handler class itself can be unit-tested independently.

### Example: validation failure

```
POST /api/v1/transporters/assignment
Body: { "maxTransporters": 0 }

→ 400 Bad Request
{
  "status": "error",
  "message": "maxTransporters: must be greater than or equal to 1"
}
```

### Example: infeasible

```
POST /api/v1/transporters/assignment
Body: { "maxTransporters": 2 }
(when minimum 3 transporters are needed)

→ 422 Unprocessable Entity
{
  "status": "error",
  "message": "No feasible assignment exists with 2 transporter(s). Minimum required: 3."
}
```

---

## 6. Persistence Design

### Entity model

```
┌──────────────┐        ┌─────────────────┐        ┌─────────────────┐
│    Lane      │        │   LaneQuote     │        │  Transporter    │
│──────────────│        │─────────────────│        │─────────────────│
│ id  (PK)     │◄──────│ lane_id  (FK,PK)│        │ id  (PK)        │
│ origin       │        │ transporter_id  │───────►│ name            │
│ destination  │        │   (FK, PK)      │        │                 │
│ quotes (1:N) │        │ quote (long)    │        │ laneQuotes (1:N)│
└──────────────┘        └─────────────────┘        └─────────────────┘
```

### `LaneQuote` composite primary key

`LaneQuote` uses a composite primary key composed of `lane_id` and `transporter_id`. This is
declared with `@IdClass(LaneQuoteId.class)`.

```java
// LaneQuoteId.java  (must implement Serializable)
public class LaneQuoteId implements Serializable {
    private Long lane;         // matches @Id field name in LaneQuote
    private Long transporter;  // matches @Id field name in LaneQuote
    // equals() and hashCode() required
}
```

```java
// LaneQuote.java
@Entity
@Table(name = "lane_quotes")
@IdClass(LaneQuoteId.class)
public class LaneQuote {
    @Id
    @ManyToOne
    @JoinColumn(name = "lane_id")
    private Lane lane;

    @Id
    @ManyToOne
    @JoinColumn(name = "transporter_id")
    private Transporter transporter;

    private long quote;
}
```

The composite key ensures the database enforces the invariant: a transporter can submit only one
quote per lane. No application-level duplicate check is needed for this constraint.

### How JPA cascade handles saving

`Lane` and `Transporter` both declare `CascadeType.ALL` with `orphanRemoval = true` on their
`@OneToMany` collections.

**Saving flow in `saveInput`:**

```
1. Delete all LaneQuote rows first  (avoid FK violation when deleting Lanes)
2. Delete all Lane rows
3. Delete all Transporter rows
4. laneRepository.saveAll(lanes)         → INSERTs into 'lanes' table
5. for each Transporter:
       set laneQuote.lane = savedLane    → resolve the Lane FK reference
   transporterRepository.saveAll(...)   → INSERTs into 'transporters'
                                           CASCADE → INSERTs into 'lane_quotes'
```

**Why set `laneQuote.lane` explicitly?** JPA cascade only persists the object graph; the
`Lane` entity must already be managed (i.e. saved) for the FK to be resolvable. Saving lanes
first guarantees that when the cascade fires for `LaneQuote`, the referenced `Lane` row already
exists in the database.

### Avoiding N+1 queries in `computeAssignment`

Loading transporters with `findAll()` followed by accessing `transporter.getLaneQuotes()` on each
would fire one SQL query per transporter (N+1). Instead, `TransporterRepository` provides:

```java
@Query("SELECT DISTINCT t FROM Transporter t "
     + "LEFT JOIN FETCH t.laneQuotes lq "
     + "LEFT JOIN FETCH lq.lane")
List<Transporter> findAllWithQuotes();
```

This produces a single SQL `JOIN` that fetches all transporters, all their quotes, and all
referenced lane entities in one round-trip.

### Database configuration

| Environment | Driver | URL | Dialect |
|---|---|---|---|
| Dev / Test (default) | H2 | `jdbc:h2:mem:transporterdb` | `H2Dialect` |
| Production | PostgreSQL | `${DB_URL}` | `${DB_DIALECT}` |

Switch by setting environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER`,
`DB_DIALECT`. No code changes required.

---

## 7. PDF Inconsistencies

The original problem PDF contains example API outputs that do **not** match the actual optimal
solutions derivable from the quote data. These inconsistencies are documented here so reviewers
understand why our implementation's output differs from the PDF.

> **Important:** Our unit tests compute expected values independently from first principles. The
> tests are correct; the PDF examples are not.

### Inconsistency 1 — 10-lane example, K=3: impossible assignment

**What the PDF claims:** Lane 7 is assigned to T2.

**Why this is wrong:** T2 only submitted quotes for Lane 3 and Lane 4. T2 has no quote for Lane 7.
An assignment of Lane 7 to T2 would require T2 to carry a lane it never bid on — this is
logically invalid.

**Additional issue:** The stated total cost of **29,000** cannot be reproduced by summing any
valid subset of the quotes provided. The number appears to be invented.

**What our solver does:** Given the 10-lane dataset with partial quotes (each transporter covers
exactly 2 lanes), the minimum number of transporters needed to cover all 10 lanes is **5**, not
3. With `maxTransporters=3`, our solver correctly returns HTTP 422 (Infeasible) with the message
`"Minimum required: 5"`.

### Inconsistency 2 — TC1 (5 lanes, 7 transporters), K=3: wrong optimal cost

**What the PDF claims:** Total cost = **75,000**.

**What is actually optimal:** Total cost = **60,139**.

The optimal assignment for K=3 is:

| Lane | Assigned To | Cost   |
|------|-------------|--------|
| L1   | T5          | 11,601 |
| L2   | T1          | 10,512 |
| L3   | T4          | 14,316 |
| L4   | T4          | 10,678 |
| L5   | T4          | 13,032 |
| **Total** | | **60,139** |

Selected transporters: `{T1, T4, T5}` — exactly 3.

The PDF likely shows the result for a higher K value or a greedy (non-optimal) algorithm.
Our exhaustive bitmask solver finds the true optimum, verified by the `tc1_k3_expectedCost60139`
unit test in `AssignmentSolverTest`.

### Why these errors exist

The PDF was likely authored by hand, using example numbers that were not verified against the
actual quote matrix. Greedy heuristics (e.g., always assign the cheapest available transporter
lane-by-lane without considering subset optimality) would produce sub-optimal or even infeasible
assignments when quote coverage is sparse.

The application solves the problem exactly: every possible subset of transporters up to the allowed
maximum is evaluated, and the globally optimal solution is returned.

---

*End of walkthrough. For quick-start instructions, curl examples, and environment setup, see
[README.md](../README.md).*
