# 5-Minute Evaluation Demo Script (Task T5)

**Project:** RescuePlate (Surplus Food Rescue Platform)  
**Target Duration:** 5 minutes  
**Audience:** Evaluators & Course Instructors  

---

## Preparation (Before Starting)
1. Start MySQL database:
   ```bash
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS food_rescue CHARACTER SET utf8mb4;"
   ```
2. Start the Spring Boot backend with the `demo` profile:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
   ```
   *(Windows: `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo`)*
3. Open Postman or Browser and import `docs/postman/RescuePlate_Phase1.postman_collection.json`.

---

## 5-Minute Script Walkthrough

### Minute 0:00 - 0:45: The Problem & Architecture Intro
- **Say:** *"Surplus food rescue is not a traditional CRUD application—it is a race against time. Cooked meals spoil in hours. Our platform solves this using real-time urgency scoring, location filtering, and concurrency protection."*
- **Show:** 
  - Application startup logs showing `DataSeeder` and `DemoDataSeeder` populating 3 donors, 3 NGOs, and 10 realistic food donations in Belagavi.
  - Health endpoint: `GET /api/health` returning `{"status":"UP"}`.

### Minute 0:45 - 1:30: Donor Food Posting & Validation
- **Action:** 
  - Log in as Donor (`donor1@example.com` / `Password@123`).
  - Demonstrate posting food with past `safeUntil` or mismatched lat/lng: show immediate **400 Bad Request** with clear field errors.
  - Post valid food: 50 meals of "Freshly Cooked Veg Biryani", ready now, safe for 2 hours.
- **Show:** Returns **201 Created** with status `AVAILABLE` and automatic coordinate fallback.

### Minute 1:30 - 2:30: The Urgency Feed & 4-Level Tie-Breaking
- **Action:** 
  - Log in as NGO 1 (`ngo1@example.com` / `Password@123`).
  - Hit `GET /api/donations/feed`.
- **Show:** 
  - Donations are ordered strictly by **Urgency Score (0-100)**:
    `urgency = 0.60 × timeScore + 0.30 × perishabilityScore + 0.10 × quantityScore`
  - High perishability (`COOKED_MEAL`) with short time left appears at the very top.
  - Point out the `exceedsCapacity` warning flag on donations larger than the NGO's nominal capacity.
  - Mention 4-level tie-breaking: (1) Urgency ↓ (2) safeUntil ↑ (3) quantity ↓ (4) id ↑.

### Minute 2:30 - 3:30: The Showpiece — Atomic Claim & Double-Claim Concurrency Demo
- **Say:** *"What happens when two NGOs click Claim at the exact same millisecond?"*
- **Action:**
  - Execute claim as NGO 1 on Donation #1 $\to$ Returns **201 Created**, status becomes `RESERVED`.
  - Immediately execute claim on the same donation as NGO 2 $\to$ Returns **409 Conflict** with message:
    `"This donation has already been claimed by another NGO."`
- **Explain:** Dual protection layers:
  1. JPA `@Version` optimistic locking on the donation row.
  2. MySQL `UNIQUE` constraint on `claims.active_donation_id`.

### Minute 3:30 - 4:15: Claim Lifecycle (Pickup & Delivery)
- **Action:**
  - Try to call `PATCH /api/claims/{id}/deliver` before pickup $\to$ Returns **400 Bad Request** (*"Claim must be picked up before delivery"*).
  - Call `PATCH /api/claims/{id}/pickup` $\to$ Returns **200 OK**, status becomes `PICKED_UP`.
  - Call `PATCH /api/claims/{id}/deliver` $\to$ Returns **200 OK**, status becomes `COMPLETED` / `DELIVERED`.
  - Check `GET /api/donations/{id}/history` $\to$ Show immutable audit trail tracking each state transition and actor.

### Minute 4:15 - 5:00: Automatic Expiry Monitor Demo & Wrap Up
- **Action:**
  - Explain background processing: `@Scheduled(fixedDelay = 60_000)` running every minute in `ExpiryMonitor`.
  - Show how past-due donations automatically transition to `EXPIRED` with actor `"System"`, releasing any active reservation.
  - Explain why `ExpiryMonitor` is in a separate bean from `DonationService` (avoiding Spring's self-invocation proxy trap).
  - Mention test suite: **204 tests passing** with 0 failures, including 20 multi-threaded concurrency iterations (`CountDownLatch`).
