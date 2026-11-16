# 🎙️ Master Loom Video Script (With Screen Cues & Diagram Walkthrough)
**Presenter:** Harshit Soni  
**Target Duration:** ~4 minutes (Paced for clear, confident delivery)  
**Use Case:** Read this script from your phone/tablet while recording your computer screen with Loom.

---

## 🖥️ Screen Setup (Do This Before Clicking "Record")

Have these **3 tabs** open in your browser side-by-side:

* **Tab 1 — Architecture Diagrams:**  
  Open GitHub: `https://github.com/harshit-soni-2004/transporter-assignment-api/blob/main/docs/ARCHITECTURE_DIAGRAMS.md`  
  *(Keep it scrolled to Diagram 1: Cloud & Infrastructure)*
* **Tab 2 — Live Swagger UI:**  
  Open: `https://transporter-assignment-api-9kfh.onrender.com/swagger-ui.html`  
  *(Refresh it once before recording to keep the server warm!)*
* **Tab 3 — Code:**  
  Open in VS Code or GitHub: `src/main/java/com/transporter/assignment/solver/AssignmentSolver.java`  
  *(Keep lines 50–95 centered on screen)*

---

## 🎬 Minute-by-Minute Script & Action Cues

---

### Part 1: Introduction & The Core Problem [0:00 – 0:35]

🔴 **SCREEN ACTION:**  
*Stay on **Tab 1 (GitHub Architecture Doc)** at the top title, with your Loom camera bubble visible.*

🗣️ **SAY WORD-FOR-WORD:**
> "Hello Team FreightFox, my name is Harshit Soni. Today, I'm excited to present my solution for the **Transporter Assignment on Lanes** backend problem.
> 
> In freight procurement, shippers contract shipping routes—called 'lanes'—across India. Various logistics carriers submit price quotes, but managing dozens of individual vendor contracts creates heavy administrative overhead and operational risk. 
> 
> The core problem is to find the mathematical minimum cost to achieve 100% lane coverage, while strictly adhering to a user-defined limit of **at most $k$ distinct transporters**."

---

### Part 2: System Architecture & Cloud Infrastructure [0:35 – 1:30]

🔴 **SCREEN ACTION:**  
*Scroll down slightly on Tab 1 to display **Diagram 1: High-Level Cloud & Infrastructure Architecture**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Let’s take a look at the system architecture.
> 
> The application is built on **Spring Boot 3.3** using **Java 17**. 
> 
> As illustrated in this cloud diagram, the service is packaged into a multi-stage Docker container deployed live on **Render**. Requests arrive through Cloudflare edge SSL termination and hit our embedded Tomcat server on port 10,000. 
> 
> For persistence, the application connects over TLS to a cloud-hosted **Neon Serverless PostgreSQL** database running in AWS us-east-1, utilizing HikariCP for connection pooling."

🔴 **SCREEN ACTION:**  
*Scroll down to **Diagram 2: Software Layer Architecture**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Looking at the software design, the codebase strictly follows clean separation of concerns:
> 
> 1. A **Presentation Layer** with Bean Validation handling incoming REST requests and OpenAPI documentation.
> 2. A **Business Orchestration Layer** coordinating database transactions and entity mapping.
> 3. An isolated, **Pure-Java Optimization Solver** with zero Spring or JPA dependencies, making the core algorithm 100% unit-testable in isolation.
> 4. And a **Spring Data JPA Persistence Layer** with optimized fetch joins to eliminate N-plus-one query overhead."

---

### Part 3: Live Swagger Demonstration [1:30 – 2:45]

🔴 **SCREEN ACTION:**  
*Switch to **Tab 2 (Live Swagger UI)**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Now, let’s see the live application in action on our deployed Render domain.
> 
> First, our health check."

🔴 **SCREEN ACTION:**  
*Click and expand `GET /health` $\rightarrow$ Click **Try it out** $\rightarrow$ Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "As you can see, `/health` returns `HTTP 200` with `status: UP`."

🔴 **SCREEN ACTION:**  
*Collapse `/health`. Click and expand `POST /api/v1/transporters/input` $\rightarrow$ Click **Try it out**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Next, we have the `/input` endpoint. This takes our master list of lanes and transporter quotes. In a single database transaction, it atomically clears any previous dataset and persists the fresh lanes and quotes. 
> 
> I’ll submit Test Case 2 from the assignment specification..."

🔴 **SCREEN ACTION:**  
*Click **Execute** on `POST /input`.*

🗣️ **SAY WORD-FOR-WORD:**
> "And we receive `HTTP 200: Input data saved successfully`.
> 
> Now let’s test the optimization endpoint."

🔴 **SCREEN ACTION:**  
*Collapse `/input`. Click and expand `POST /api/v1/transporters/assignment` $\rightarrow$ Click **Try it out** $\rightarrow$ In the body, set `"maxTransporters": 2`.*

🗣️ **SAY WORD-FOR-WORD:**
> "In Test Case 2, carriers quote sparse, disjoint routes. It is mathematically impossible to cover all 5 lanes with only 2 transporters. 
> 
> Watch what happens when I request `maxTransporters: 2`..."

🔴 **SCREEN ACTION:**  
*Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Instead of crashing or returning an invalid state, the API returns a clean **HTTP 422 Unprocessable Entity**, explicitly telling the user: *'No feasible assignment exists with 2 transporters. Minimum required: 3.'*"

🔴 **SCREEN ACTION:**  
*In the request body, change `2` to `3` $\rightarrow$ Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Now, with $k = 3$, we click execute... and we get `HTTP 200 OK`!
> 
> The solver found the globally optimal solution with a total cost of **7,500**, selecting Transporters 1, 2, and 3, and cleanly mapping each lane to its cheapest winning carrier."

---

### Part 4: The Optimization Algorithm [2:45 – 3:45]

🔴 **SCREEN ACTION:**  
*Switch to **Tab 3 (`AssignmentSolver.java`)**, showing lines 55 to 95.*

🗣️ **SAY WORD-FOR-WORD:**
> "Now let’s look under the hood at `AssignmentSolver.java`.
> 
> A naive greedy approach—simply picking the cheapest carrier on each lane—fails because taking individual lane minimums can trap you into an expensive vendor subset.
> 
> Instead, I implemented an **Exact Bitmask Enumeration** algorithm:
> 
> As you can see on line 60, each carrier is represented by a single bit in an integer `mask`. As the loop counts through all $2^n$ subsets, `Integer.bitCount(mask)` checks the team size. This maps directly to the CPU's hardware-level `POPCNT` assembly instruction, allowing us to prune oversized teams in just one nanosecond with zero heap allocations.
> 
> For each feasible team, the inner loop identifies the cheapest active carrier for every lane. If any lane is left uncovered, the entire team is discarded.
> 
> Finally, on line 88, notice the **business tie-breaker**: if two different teams produce the exact same minimum cost, the solver chooses the team that utilizes *more* transporters. This distributes shipping volume fairly across multiple carriers and mitigates single-vendor supply chain risk."

---

### Part 5: Test Suite & Closing [3:45 – 4:10]

🔴 **SCREEN ACTION:**  
*Switch back to **Tab 1 (GitHub)**, show the repository home page or README, and look into the camera.*

🗣️ **SAY WORD-FOR-WORD:**
> "For quality assurance, the application is covered by **27 automated unit and integration tests**, validating edge cases, constraint violations, and solver correctness. The repository also includes comprehensive documentation, architecture diagrams, and automated smoke test scripts.
> 
> The application is live on Render, connected to Neon PostgreSQL, and ready for production use.
> 
> Thank you so much for your time and consideration, and I look forward to the next steps with FreightFox!"

---

## 🎯 Quick Rehearsal Tips:
* **Point with your mouse:** When talking about Tomcat/Neon in the diagram, circle them with your cursor. When talking about `bitCount` and `used > bestUsed` in the code, hover over those lines.
* **Keep your cadence steady:** You don't need to rush! 520 words at 130 words/minute leaves you with 20 seconds of breathing room.
