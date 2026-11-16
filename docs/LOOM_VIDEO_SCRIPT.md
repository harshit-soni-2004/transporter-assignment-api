# 4-Minute Loom Video Script: Transporter Assignment on Lanes
**Presenter:** Harshit Soni  
**Target Duration:** ~4 minutes (~520 words at a natural, steady pace)  
**Preparation:** Open your browser with two tabs ready:
1. **Tab 1:** Live Swagger UI (`https://transporter-assignment-api-9kfh.onrender.com/swagger-ui.html`)
2. **Tab 2:** Your IDE or GitHub open to `AssignmentSolver.java`

---

## ⏱️ Timeline & Screen Prompts

| Timestamp | Phase | On-Screen Visual |
|---|---|---|
| **0:00 – 0:35** | Intro & Problem Definition | Webcam + Title Slide or Swagger UI landing page |
| **0:35 – 1:15** | Architecture & Tech Stack | Swagger UI or Architecture diagram |
| **1:15 – 2:35** | Live API Demonstration | Live Swagger UI: `/health`, `/input`, `/assignment` ($k=2, 3$) |
| **2:35 – 3:35** | Algorithm & Code Deep Dive | `AssignmentSolver.java` in IDE / GitHub |
| **3:35 – 4:00** | Test Suite & Closing | Terminal test output (`27 passed`) + Webcam |

---

## 🎙️ Spoken Script (Word-for-Word)

### [0:00 – 0:35] Part 1: Introduction & Problem Statement
*(Visual: Webcam + Swagger UI landing page)*

> "Hello Team FreightFox, my name is Harshit Soni. Today, I am excited to walk you through my solution for the **Transporter Assignment on Lanes** backend assignment.
> 
> In freight procurement, shippers contract routes—or 'lanes'—across the country. Various carriers submit price quotes, but managing dozens of vendor contracts introduces heavy administrative overhead and risk. 
> 
> The challenge is to find the mathematical minimum cost to service 100% of the lanes, while strictly adhering to a user-defined cap of **at most $k$ distinct transporters**."

---

### [0:35 – 1:15] Part 2: Architecture & Cloud Setup
*(Visual: Switch to Swagger UI header showing live URL `transporter-assignment-api-9kfh.onrender.com`)*

> "To solve this, I designed a production-ready application using **Spring Boot 3.3** on **Java 17**, backed by **Spring Data JPA**. 
> 
> As you can see from the URL, the service is deployed live on **Render** inside a multi-stage Docker container configured with low-memory JVM flags. For persistence, it connects over TLS to a cloud-hosted **Neon Serverless PostgreSQL** database.
> 
> The application follows clean architectural separation: a controller layer for HTTP validation, a transactional service layer for business invariants, and a completely isolated, pure-Java optimization solver."

---

### [1:15 – 2:35] Part 3: Live Swagger UI Walkthrough
*(Visual: Live Swagger UI)*

> "Let's test the live endpoints.
> 
> First, our health probe at `/health`. I'll click **Try it out** and **Execute**... and we receive `HTTP 200` with `status: UP`.
> 
> *(Click and expand `POST /api/v1/transporters/input`)*  
> Next is `/api/v1/transporters/input`. This endpoint accepts our master list of lanes and carrier quotes. It atomically clears previous data and persists the new dataset in a single database transaction. Let's submit Test Case 2 from the problem statement... Click **Execute**... and we get `200 OK: Input data saved successfully`.
> 
> *(Click and expand `POST /api/v1/transporters/assignment`)*  
> Now for the optimization endpoint. In Test Case 2, carriers quote disjoint routes, meaning it is mathematically impossible to cover all 5 lanes with only 2 transporters. 
> 
> Watch what happens if I request `maxTransporters: 2`... Click **Execute**...  
> Rather than failing silently, the API returns a clean **HTTP 422 Unprocessable Entity**, explicitly informing the client: *'No feasible assignment exists with 2 transporters. Minimum required: 3.'*
> 
> Now, let's change `maxTransporters` to **3** and click **Execute**...  
> Instantly, we receive `200 OK`! The solver returns the global optimal cost of **7,500**, using Transporters 1, 2, and 3, with each lane cleanly mapped to its cheapest winning carrier."

---

### [2:35 – 3:35] Part 4: The Optimization Algorithm
*(Visual: Switch tab to `AssignmentSolver.java` lines 50–95)*

> "Now, let's look at the brain of the system in `AssignmentSolver.java`.
> 
> A naive greedy approach—picking individual lane minimums—fails because it gets trapped in local minima and can violate the transporter cap. 
> 
> Instead, I implemented an **Exact Bitmask Enumeration** algorithm:
> 
> Each transporter is represented as a single bit in an integer `mask`. As the loop counts through all $2^n$ subsets, it evaluates `Integer.bitCount(mask)`. This translates directly into the hardware-level `POPCNT` assembly instruction, allowing us to prune oversized teams in just one nanosecond with zero heap allocations.
> 
> For each valid team, the solver checks every lane, picking the cheapest active carrier. If any lane cannot be covered, the team is discarded. 
> 
> Furthermore, I included a **business tie-breaker**: if two different combinations tie for the exact same minimum cost, the solver chooses the combination that utilizes *more* transporters, promoting fair load distribution and mitigating single-vendor supply chain risk."

---

### [3:35 – 4:00] Part 5: Quality Assurance & Conclusion
*(Visual: Show terminal with `Tests run: 27, Failures: 0, Errors: 0` or webcam)*

> "For reliability, the codebase is covered by **27 automated unit and integration tests**, validating edge cases, infeasibility states, and tie-breakers. The GitHub repository also includes full documentation and deployment scripts.
> 
> Thank you so much for reviewing my assignment, and I look forward to discussing my solution with the FreightFox engineering team!"
