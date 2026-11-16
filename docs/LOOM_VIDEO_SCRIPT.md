# 🎙️ Official FreightFox Loom Video Script (Aligned to Submission Rubric)
**Presenter:** Harshit Soni  
**Target Duration:** ~3:45 – 4:00 minutes (Within the 2–5 minute requirement)  
**Mandatory Requirement:** Screen and face visible throughout (Keep your Loom camera bubble turned ON).

---

## 🖥️ Screen Setup (Before You Click "Record")

Have these **3 tabs** ready in your browser:

1. **Tab 1 — Architecture Diagram:**  
   GitHub: `https://github.com/harshit-soni-2004/transporter-assignment-api/blob/main/docs/ARCHITECTURE_DIAGRAMS.md`  
   *(Scrolled to Diagram 1: Cloud Architecture)*
2. **Tab 2 — Live Swagger UI:**  
   Live URL: `https://transporter-assignment-api-9kfh.onrender.com/swagger-ui.html`  
   *(Open and refresh it once so the server is warm)*
3. **Tab 3 — Solver Code:**  
   VS Code or GitHub: `src/main/java/com/transporter/assignment/solver/AssignmentSolver.java`  
   *(Scrolled to lines 50–95)*

---

## 🎬 Word-for-Word Script & Screen Cues

---

### 1. Approach: What Did You Build? [0:00 – 0:50]

🔴 **SCREEN ACTION:**  
*Stay on **Tab 1 (GitHub Architecture)**. Ensure your Loom face bubble is active in the corner.*

🗣️ **SAY WORD-FOR-WORD:**
> "Hello Team FreightFox, my name is Harshit Soni. Today, I'm presenting my solution for the **Transporter Assignment on Lanes** backend assignment.
> 
> **For my approach, what I built** is a production-grade REST API that solves the logistics procurement challenge: shippers manage freight routes across India, and various carriers submit price quotes. However, managing dozens of vendor contracts introduces heavy administrative overhead and operational risk.
> 
> My application determines the mathematical minimum cost to achieve 100% lane coverage, while strictly enforcing a user-defined cap of **at most $k$ distinct transporters**.
> 
> As you can see in this cloud architecture diagram, I built this using **Spring Boot 3.3** and **Java 17**, deployed live inside a Docker container on **Render**, and backed by a cloud **Neon Serverless PostgreSQL** database."

---

### 2. API Demo: Live Deployed Swagger UI [0:50 – 2:05]

🔴 **SCREEN ACTION:**  
*Switch to **Tab 2 (Live Swagger UI)**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Now, let's look at the **API Demo** on our live deployed Render domain.
> 
> First, our health check at `/health`."

🔴 **SCREEN ACTION:**  
*Expand `GET /health` $\rightarrow$ Click **Try it out** $\rightarrow$ Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Executing `/health` returns `HTTP 200` with `status: UP`.
> 
> Next is `/api/v1/transporters/input`."

🔴 **SCREEN ACTION:**  
*Collapse `/health`. Expand `POST /api/v1/transporters/input` $\rightarrow$ Click **Try it out** $\rightarrow$ Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "This endpoint accepts our master list of lanes and carrier quotes. In a single `@Transactional` block, it atomically clears old records and persists the new dataset into PostgreSQL. We get `200 OK: Input data saved successfully`.
> 
> Now let's test our optimization endpoint at `/api/v1/transporters/assignment`."

🔴 **SCREEN ACTION:**  
*Collapse `/input`. Expand `POST /api/v1/transporters/assignment` $\rightarrow$ Click **Try it out** $\rightarrow$ Set `"maxTransporters": 2`.*

🗣️ **SAY WORD-FOR-WORD:**
> "In this dataset, carriers quote disjoint routes, meaning it is mathematically impossible to cover all 5 lanes with only 2 transporters. 
> 
> When I execute `maxTransporters: 2`..."

🔴 **SCREEN ACTION:**  
*Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "The API returns a clean **HTTP 422 Unprocessable Entity**: *'No feasible assignment exists with 2 transporters. Minimum required: 3.'*
> 
> Now, let's set `maxTransporters` to **3**..."

🔴 **SCREEN ACTION:**  
*Change `2` to `3` in the request body $\rightarrow$ Click **Execute**.*

🗣️ **SAY WORD-FOR-WORD:**
> "Instantly, we get `200 OK`! The solver returns the global minimum cost of **7,500**, selecting Transporters 1, 2, and 3, with every lane assigned to its cheapest available carrier."

---

### 3. Technical Decisions: Key Architectural Choices [2:05 – 3:05]

🔴 **SCREEN ACTION:**  
*Switch to **Tab 3 (`AssignmentSolver.java`)**, showing lines 55 to 92.*

🗣️ **SAY WORD-FOR-WORD:**
> "Next, I’d like to highlight **two key technical decisions**:
> 
> **Decision 1: Exact Bitmask Enumeration over Greedy or Recursion.**  
> A greedy approach fails because choosing individual lane minimums traps you into an expensive vendor subset. A recursive approach adds call-stack overhead and object allocations. 
> 
> Instead, each transporter is mapped to a bit in an integer `mask`. As the loop counts through subsets, `Integer.bitCount(mask)` checks team size. This maps directly to the CPU's hardware-level `POPCNT` instruction, pruning oversized teams in just one nanosecond with zero heap allocations during the search.
> 
> **Decision 2: Business Tie-Breaking for Supply Chain Resilience.**  
> On line 88, if two candidate teams produce the exact same minimum cost, the solver tie-breaks by picking the team with *more* transporters (`used > bestUsed`). This deliberately distributes freight volume across vendors rather than concentrating supply chain risk on a single carrier."

---

### 4. Challenge: Problem Faced & How I Solved It [3:05 – 3:55]

🔴 **SCREEN ACTION:**  
*Scroll up to lines 45–54 of `AssignmentSolver.java` (showing `computeMinTransportersNeeded`).*

🗣️ **SAY WORD-FOR-WORD:**
> "Regarding a **major challenge I faced**:
> 
> The sample data in the problem PDF contained inconsistencies—for instance, the 10-lane example assigned Lane 7 to Transporter 2, even though Transporter 2 never submitted a quote for that lane, and the printed manual sums did not match. 
> 
> **How I solved it:**  
> First, I enforced strict database and algorithmic invariants ensuring a carrier can *only* win a lane they actually bid on. 
> 
> Second, to prevent expensive computations or corrupt states on sparse datasets, I designed an upfront feasibility algorithm: `computeMinTransportersNeeded()`. Before entering the main optimization loop, it computes the exact minimum number of carriers required to cover 100% of the lanes. If $k$ is less than this minimum, it immediately halts and surfaces the informative HTTP 422 error we saw earlier."

---

### Conclusion [3:55 – 4:05]

🔴 **SCREEN ACTION:**  
*Switch back to Tab 1 or look directly into the camera.*

🗣️ **SAY WORD-FOR-WORD:**
> "The project is thoroughly tested with **27 automated tests**, fully documented, and live in production on Render.
> 
> Thank you for your time, and I look forward to speaking with the FreightFox engineering team!"
