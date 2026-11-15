# Deployment Guide: Transporter Assignment API on Render with Neon PostgreSQL

This guide provides end-to-end instructions for deploying the **Transporter Assignment API** on **Render** using Docker and **Neon Serverless PostgreSQL**.

---

## Architecture Overview

```
┌─────────────────────────────────┐
│     Client / Browser / curl     │
└────────────────┬────────────────┘
                 │ HTTPS
                 ▼
┌─────────────────────────────────┐
│       Render Web Service        │
│  (Docker: Eclipse Temurin JRE)  │
│   Spring Boot (Profile: prod)   │
└────────────────┬────────────────┘
                 │ TLS (sslmode=require)
                 ▼
┌─────────────────────────────────┐
│      Neon Serverless Postgres   │
│         (PostgreSQL 16)         │
└─────────────────────────────────┘
```

---

## Step 1: Push Code to GitHub

1. Open a terminal in the project root directory:
   ```bash
   git init
   git add .
   git commit -m "feat: complete transporter assignment application"
   ```

2. Create a new GitHub repository (e.g., `transporter-assignment`), then push:
   ```bash
   git branch -M main
   git remote add origin https://github.com/<your-username>/transporter-assignment.git
   git push -u origin main
   ```

---

## Step 2: Create a Neon PostgreSQL Database

1. Navigate to [Neon](https://neon.tech) and sign up / log in.
2. Click **Create Project**:
   - **Project name:** `transporter-db`
   - **Postgres version:** `16` (default)
   - **Region:** Select a region close to your Render deployment (e.g., `US East (Ohio)` or `Europe (Frankfurt)`).
3. Once created, navigate to the **Dashboard** and view your **Connection Details**.
4. Neon displays a connection string in the format:
   ```text
   postgresql://USER:PASSWORD@HOST/DB?sslmode=require&channel_binding=require
   ```
5. Convert this connection string into JDBC form:
   - **Drop** the `USER:PASSWORD@` credentials from the URL.
   - **Drop** the `&channel_binding=require` parameter.
   - **Prepend** `jdbc:`.
   - Resulting **`DB_URL`**:
     ```text
     jdbc:postgresql://HOST/DB?sslmode=require
     ```
   - *(Example: `jdbc:postgresql://ep-example-123456.us-east-2.aws.neon.tech/neondb?sslmode=require`)*
6. Note down the three separate values to configure in Render:
   - **`DB_URL`**: `jdbc:postgresql://HOST/DB?sslmode=require`
   - **`DB_USERNAME`**: `USER` (e.g., `neondb_owner`)
   - **`DB_PASSWORD`**: `PASSWORD` (your Neon database password)

---

## Step 3: Create a Render Web Service

1. Navigate to [Render Dashboard](https://dashboard.render.com/) and click **New +** $\rightarrow$ **Web Service**.
2. Connect your GitHub repository (`transporter-assignment`).
3. Configure the service settings:
   - **Name:** `transporter-assignment`
   - **Region:** Match your Neon DB region (e.g., `Ohio (US East)`).
   - **Branch:** `main`
   - **Root Directory:** Leave blank if the repository root contains `pom.xml` and `Dockerfile`.
   - **Runtime:** **Docker** (Render will automatically detect the multi-stage `Dockerfile`).
   - **Instance Type:** **Free** (512 MB RAM, 0.1 CPU).
4. Click **Advanced** and expand the **Environment Variables** section.

---

## Step 4: Environment Variables to Configure on Render

Add the following environment variables in the Render dashboard:

| Variable Name | Value | Purpose |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `prod` | Activates `application-prod.yml` |
| `DB_URL` | `jdbc:postgresql://HOST/DB?sslmode=require` | Neon PostgreSQL JDBC endpoint (without credentials or channel_binding) |
| `DB_USERNAME` | `<neon-username>` | PostgreSQL database username |
| `DB_PASSWORD` | `<neon-password>` | PostgreSQL database password |

> [!NOTE]
> - `PORT` is automatically injected by Render (typically `10000`). `application.yml` binds to `${PORT:8080}`, so no manual port configuration is necessary.
> - The multi-stage `Dockerfile` uses `-XX:MaxRAMPercentage=70` and `-XX:+UseSerialGC`, guaranteeing the app uses under 350 MB RAM and safely stays within Render's 512 MB free tier memory cap.

> [!IMPORTANT]
> **Render Free Tier Spin-Down / Cold Starts:**
> Render's free tier automatically spins down (sleeps) after 15 minutes of inactivity. When waking up on a new request, the initial cold start can take about **60 seconds (1 minute)** while the container is spun up and the JVM boots.
> **Tip for Loom / Video Walkthroughs:** Always hit `/health` (or visit `/swagger-ui.html` in your browser) a minute beforehand to wake up the service before recording your Loom!

5. Click **Create Web Service**. Render will pull the code, build the multi-stage Docker image, and start the container.

---

## Step 5: Verify the Live Deployment

Once Render finishes deploying and displays **Status: Live**:

1. Note your live URL: `https://transporter-assignment.onrender.com`

2. **Verify Health Endpoint:**
   ```bash
   curl -i https://transporter-assignment.onrender.com/health
   ```
   **Expected Response (200 OK):**
   ```json
   {"status": "UP"}
   ```

3. **Verify Interactive Swagger UI:**
   Open in your browser:
   ```text
   https://transporter-assignment.onrender.com/swagger-ui.html
   ```
   You will see:
   - Title: **Transporter Assignment on Lanes API**
   - Endpoints `/api/v1/transporters/input` and `/api/v1/transporters/assignment` expanded by default with sample JSON request bodies pre-loaded.

4. **Run the Automated Smoke Test:**
   Execute `smoke-test.sh` directly against your deployed domain:
   ```bash
   ./smoke-test.sh https://transporter-assignment.onrender.com
   ```
   The test will:
   - Check `/health` $\rightarrow$ 200 OK
   - Submit TC2 payload $\rightarrow$ 200 OK
   - Run assignment $K=2$ $\rightarrow$ 422 Unprocessable Entity (needs at least 3)
   - Run assignment $K=3$ $\rightarrow$ 200 OK, cost `7500`
   - Run assignment $K=4$ $\rightarrow$ 200 OK, cost `7450`
   - Print pass/fail summary.

---

## Troubleshooting & FAQ

* **Cold Starts:** Free instances on Render spin down after 15 minutes of inactivity. The first request after sleep may take ~30–50 seconds to wake up.
* **Database Connection Issues:** Ensure `?sslmode=require` is present at the end of `DB_URL`. Neon requires SSL for all external connections.
* **Schema Updates:** In `prod` profile, Hibernate's `ddl-auto` is set to `update`, ensuring tables (`lanes`, `transporters`, `lane_quotes`) are created automatically without dropping production data.
