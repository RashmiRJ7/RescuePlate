# Task Board

**Project:** Time-Sensitive Surplus Food Rescue
**Purpose:** Small, self-contained tasks so that **anyone who is free can pick one up immediately**.

Read first: `README.md`, then `ARCHITECTURE.md` and `API_DOCUMENTATION.md`. Use `ALGORITHMS.md` for any scoring task.

---

## 1. How to use this board

1. Copy the tables into GitHub Projects or Notion (one card per row, columns: **Backlog → In Progress → Review → Done**).
2. Pick a task whose **Needs** column is empty or fully Done.
3. Put your name on it and move it to **In Progress**. **One task per person at a time.**
4. Create a branch: `feature/<ID>-short-name` (example: `feature/D1-urgency-calculator`).
5. Keep the pull request small. **One review** from someone other than the author, then merge into `main`.
6. Move the card to **Done** only when it is merged and its **Done when** checks pass.
7. Add 2-3 lines on the card: *what I built and why*. These notes feed the report and prepare you for the viva.

### Tags

| Tag | Meaning |
|---|---|
| `[core]` | Viva-critical logic (scoring, state machine, claim safety, scheduler). Owner: **Rashmi**. Others may pick one only if they **pair** with the owner and can explain it afterwards. |
| `[BE]` | Backend, standard Spring Boot work. Anyone comfortable with Java. |
| `[FE]` | Frontend (HTML/CSS/JS/Bootstrap). |
| `[TEST]` | Tests or Postman. |
| `[DOC]` | Documents, screenshots, report. |

### Size

**S** ≤ 1 h · **M** 1-3 h · **L** 3-5 h. If a task grows beyond L, split it and tell the team.

### Definition of Done (every task)

- [ ] Compiles; app starts; no failing tests.
- [ ] Matches `API_DOCUMENTATION.md` (paths, JSON field names, status codes). If you must deviate, update the doc **and** tell the team.
- [ ] Input validated; errors use the standard error JSON.
- [ ] No secrets or passwords committed.
- [ ] Merged to `main` via reviewed PR.

### API paths

Endpoint paths in this board are relative to the base URL `/api`, as in `API_DOCUMENTATION.md` (so `/auth/login` means `/api/auth/login`).

### Package reference

`com.foodrescue.{config, controller, dto, entity, enums, exception, repository, service, algorithm, scheduler}`

---

## 2. Phase 1 tasks

Phase 1 is a working vertical slice (about 30% of the project): donor posts → NGO sees the urgency-ordered feed → claims (conflict-safe) → picks up → delivers, with automatic expiry. The Admin can log in (seeded account) but has no admin features yet. Reliability counters (`total_claims`, `completed_claims`) exist in the schema but are only updated from Phase 2 (task P15).

### Group F: Foundation (Day 1, do in order)

Everything else depends on this block. F5 can run in parallel.

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| F1 | Project setup: Maven, Java 17, Spring Boot 3, dependencies (web, data-jpa, security, validation, mysql). `application.properties`, `.gitignore`, create DB `food_rescue` | `[core]` | none | M | `mvn spring-boot:run` starts and connects to MySQL |
| F2 | Enums (`Role`, `FoodCategory`, `DonationStatus`, `ClaimStatus`), entities (`User`, `NgoProfile`, `Donation` with `@Version`, `Claim` with unique `activeDonationId`, `DonationStatusHistory`) and repositories | `[core]` | F1 | L | Tables are created in MySQL with the columns in `ARCHITECTURE.md` §3 |
| F3 | Exceptions + `GlobalExceptionHandler` (`ResourceNotFound`, `DonationNotClaimable` → 409, `NgoNotEligible` → 400, `InvalidStatusTransition`, validation errors) returning the standard error JSON | `[BE]` | F1 | M | A thrown exception returns the right HTTP code and JSON shape (`ARCHITECTURE.md` §8) |
| F4 | Security: `SecurityConfig`, session login, BCrypt bean, role-based path rules, `DataSeeder` for the admin account | `[core]` | F2 | L | Seeded admin can log in; Phase 1 role rules from `ARCHITECTURE.md` §7 enforced (Admin rules on the cancel endpoints and `SessionRegistry` come in Phase 2, M1/M2) |
| F5 | Frontend skeleton in `resources/static`: Bootstrap layout, role-aware navbar, `api.js` (`fetch` wrapper with error handling), a `mock/` folder with example JSON from the API doc | `[FE]` | none | M | Pages share one layout; `api.js` can call a test endpoint |
| F6 | Git repo setup, branch rules, empty Postman collection with folders Auth / Donor / NGO / Admin | `[TEST]` | none | S | Everyone can clone, run, and push a branch |

### Group A: Accounts (Day 2)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| A1 | `POST /auth/register` (Donor/NGO, NGO profile block, NGO latitude/longitude required, duplicate email → 409, validation) | `[BE]` | F2, F3, F4 | M | Both roles register; admin role is rejected; NGO without location → 400 |
| A2 | `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` | `[BE]` | F4 | M | Login sets session; `me` returns role; inactive user (`active = false`) gets 401 |
| A3 | `GET/PUT /ngo/profile` (nominal capacity guideline, radius, accepted categories; `reliabilityPercent` is `null` until Phase 2 counters exist) | `[BE]` | A1 | S | NGO can read and update only its own profile |
| A4 | `register.html` and `login.html` with role-based redirect after login | `[FE]` | F5 (mock) | M | Forms validate; redirect Donor / NGO to their pages; Admin to a placeholder page (admin dashboard is Phase 2, U5) |

### Group D: Donations (Days 2-3)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| D1 | `UrgencyScoreCalculator` + `ScoringProperties` (weights in config) + JUnit tests. Pure Java, no Spring in the calculator. See `ALGORITHMS.md` §1 | `[core]` | F1 | M | Worked example gives **95.0 / 66.7 / 26.9**; all §1.4 test cases pass |
| D2 | `DistanceUtil` (Haversine) + unit tests | `[BE]` | F1 | S | Known city pair is within ±1 km of the real distance |
| D3 | `DonationService.changeStatus(...)`: transition validator, history row, only place that sets status. See `ARCHITECTURE.md` §4 | `[core]` | F2, F3 | L | Illegal move throws `InvalidStatusTransition`; every legal move writes history; unit tests |
| D4 | `POST /donations` (validation rules from the API doc, including the coordinate rule: request coordinates, else the donor's saved location, else 400) and `GET /donations/mine` | `[BE]` | F2, F3, F4, D3 | M | Valid post → 201 `AVAILABLE` with coordinates; invalid → 400 with `fieldErrors`; donor without saved location and no coordinates → 400; donor sees only own |
| D5 | `GET /donations/feed`: claimable only, **NGO eligibility filter** (accepted category, distance ≤ service radius; `ALGORITHMS.md` §3.1), urgency computed at read time, sorted by urgency ↓ then `safeUntil` ↑ (Comparator / `PriorityQueue`), filters (category, minQuantity, maxDistanceKm capped at service radius), returns `distanceKm`, `minutesLeft`, `urgencyScore`, and `exceedsCapacity` flag (true if `quantity > capacityMeals`) | `[core]` | D1, D2, D4, A3 | L | Cooked 30-min item appears above packaged 5-hour item; donations outside the NGO's categories or radius are hidden; donations exceeding capacity appear with `exceedsCapacity=true`; filters work |
| D7 | `GET /donations/{id}` and `GET /donations/{id}/history` | `[BE]` | D3 | S | History shows `System` for automatic changes |

### Group C: Claims (Day 3-4)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| C1 | `ClaimService.claim()` + `POST /donations/{id}/claim`: state check (→ **409**), eligibility check (→ **400** for category/radius; exceeding capacity is permitted), `@Version` and unique `activeDonationId`, map conflicts to **409**. See `ARCHITECTURE.md` §5 | `[core]` | D3, D4, A3 | L | Second claim on the same donation returns 409; claiming an expired/cancelled donation returns 409; ineligible NGO gets 400; claiming donation exceeding capacity succeeds |
| C2 | `PATCH /claims/{id}/cancel` (owner NGO): claim → `CANCELLED`, `activeDonationId` → null, donation reopens to `AVAILABLE` | `[core]` | C1 | M | Cancelled donation reappears in the feed; can be claimed again; another NGO gets 403 |
| C3 | `PATCH /claims/{id}/pickup` and `/deliver` with order checks | `[BE]` | C1 | M | Wrong order → 400; deliver sets claim `COMPLETED` and donation `DELIVERED` |
| C4 | `GET /claims/mine` with optional status filter | `[BE]` | C1 | S | NGO sees only own claims |
| C5 | Concurrency test: two threads claim the same donation; one 201 and one 409 | `[TEST]` | C1 | M | Test passes repeatedly (run it 20×) |

### Group S: Scheduler (Day 4)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| S1 | `ExpiryMonitor` (`@Scheduled`, every 60 s): expire past-due `AVAILABLE/DISPATCHING/RESERVED`, set the active claim to `EXPIRED` (`activeDonationId` → null), write history as `System`; idempotent. Enable scheduling in config | `[core]` | D3, C1 | M | A donation past `safeUntil` becomes `EXPIRED` within ~1-2 min, and its claim too; running twice changes nothing |

### Group U: Frontend pages (Days 2-4, against mock JSON first)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| U1 | Donor page: post-food form (browser Geolocation autofill + manual lat/lng, prefilled from the donor's saved location from `GET /auth/me` when present, time pickers in IST converted to UTC) and "my donations" list with status badges | `[FE]` | F5 | L | Form validates client-side; lat/lng required when the donor has no saved location, both or neither, ranges checked; list renders from mock, then from real API |
| U2 | NGO feed page: cards ordered as returned, urgency and time-left badges, capacity warning badge if `exceedsCapacity=true`, filters, **Claim** button with clear message on 409 (and on 400 "not eligible") | `[FE]` | F5 | L | "No longer available" shown nicely; warning displayed if exceeding capacity; feed refreshes every ~30 s |
| U3 | NGO "my claims" page: actions Cancel / Picked Up / Delivered shown only when valid; `CANCELLED` and `EXPIRED` shown as closed | `[FE]` | F5 | M | Buttons match claim status |
| U6 | Role guards: redirect to login on 401, hide pages by role, global toast for errors | `[FE]` | A4 | S | Opening an NGO page as a donor redirects away |

### Group T: Testing, demo, documents (Day 5; some can start earlier)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| T1 | Minimal Postman collection: one happy path per role (Donor: register → login → post → my donations; NGO: register → login → feed → claim → pickup → deliver; Admin: login only), plus the double-claim pair | `[TEST]` | F6 + endpoints | S | Collection runs top to bottom on a fresh DB |
| T2 | `DemoDataSeeder`: 1 admin, 3 donors, 3 NGOs, ~10 donations with **varied expiry times** and categories (profile `demo`). Seeded NGOs and donations must satisfy the eligibility rules (`ALGORITHMS.md` §3.1): accepted categories cover the donations, donation locations within each NGO's `serviceRadiusKm`, and at least one donation demonstrating the `exceedsCapacity` warning | `[BE]` | F2 | M | Fresh start: each seeded NGO's feed is non-empty and visibly shows the urgency ordering |
| T3 | Acceptance run against `PRD.md` §10 checklist; log bugs as cards | `[TEST]` | most of Phase 1 | M | Every checkbox ticked or a bug card exists |
| T4 | Fresh-machine check: a teammate follows `README.md` exactly and reports what is missing | `[DOC]` | F1 | S | README steps work without help |
| T5 | Demo script (5 minutes) and screenshots for the report | `[DOC]` | most of Phase 1 | M | Script walks: donor posts → NGO sees urgency order → claim → conflict demo → pickup → delivered → expiry shown on a short-lived donation |

### Suggested order by day

| Day | Start with |
|---|---|
| 1 | F1 → F2 → F3 → F4 (Rashmi) · **in parallel:** F5, F6, D1, D2 |
| 2 | A1, A2, A3 · D3, D4 · A4, U1 |
| 3 | D5, D7 · C1 · U2 |
| 4 | C2, C3, C4 · S1 · U3, U6 · T2 |
| 5 | C5 · T1, T3, T4, T5 · bug fixing |

**Tasks anyone can start right now (no dependencies):** D1, D2, F5, F6. **Next once F1 is merged:** T4.

---

## 3. Phase 2 backlog (do not start until Phase 1 is merged)

### Moved from Phase 1 (IDs and dependencies unchanged)

| ID | Task (files) | Tag | Needs | Est | Done when |
|---|---|---|---|---|---|
| D6 | `PATCH /donations/{id}/cancel` (owner or admin; active claim → `CANCELLED`, NGO `total_claims` −1) | `[BE]` | D3, D4 | S | Owner and admin can cancel; others get 403; terminal states rejected; NGO reliability unchanged |
| M1 | `GET /admin/users` (filters) and `PATCH /admin/users/{id}/status` with the deactivation effects in `ARCHITECTURE.md` §7 (`SessionRegistry` bean, end sessions, cancel open donations/claims); never return password hashes | `[BE]` | F4, D3, C2 | L | Admin deactivates a user; that user can no longer log in and a logged-in session gets 401; their open donations/claims are cancelled; admin cannot deactivate self |
| M2 | `GET /admin/donations` with filters; Admin access to `PATCH /claims/{id}/cancel` (security rule + skip ownership check) | `[BE]` | D3 | S | Admin sees all donations, filtered by status; admin can cancel a claim but not pick up/deliver |
| M3 | `DashboardService` and `GET /dashboard` (admin view): meals posted / rescued / expired, rescue rate, active donors and NGOs, donations by day (no `openComplaints` until P9) | `[BE]` | F2 | M | Numbers match manual counts in the DB |
| M4 | `PATCH /admin/claims/{id}/resolve` (`DELIVERED` / `NOT_DELIVERED`, required reason, history note) and the `claim` summary in `GET /admin/donations`. See `ARCHITECTURE.md` §4 | `[BE]` | C3, M2 | S | Stuck `PICKED_UP` claim can be resolved either way; non-`PICKED_UP` claim → 400; donation never reopens; reliability counters correct |
| U4 | Status timeline component (uses `/history`), reused on donor and NGO pages | `[FE]` | F5 | M | Timeline shows time (IST), actor, status |
| U5 | Admin dashboard: stat cards, users table with activate/deactivate, donations table (with a "Resolve" action on `PICKED_UP` rows that asks for outcome and reason) | `[FE]` | F5 | L | All three sections populated |

### Phase 2 features

| ID | Task | Tag | Needs | Est |
|---|---|---|---|---|
| P15 | Reliability counters (`ALGORITHMS.md` §3.2): `total_claims` +1 on claim (C1), −1 when a donation cancel closes a claim (D6, M1), `completed_claims` +1 on deliver (C3, M4); `reliabilityPercent` in the NGO profile (A3) | `[core]` | C1, C3, D6 | S |
| P1 | `MatchScoreCalculator` + eligibility filter + tests (`ALGORITHMS.md` §3) | `[core]` | D2, P15 | L |
| P2 | `DispatchLog` entity + repository; `DispatchService.runNextRound()` (rank, top 3, window, widen radius) | `[core]` | P1 | L |
| P3 | `DispatchScheduler` (every 30 s, `PriorityQueue` by urgency) and `DISPATCHING` status handling, including `DISPATCHING → AVAILABLE` when rounds are exhausted (`ALGORITHMS.md` §4.2) | `[core]` | P2, S1 | L |
| P4 | Claim-cancel hook: reopen to `DISPATCHING` and resume dispatch if rounds remain, else `AVAILABLE` | `[core]` | P3, C2 | M |
| P5 | `Notification` entity/service + endpoints (`GET /notifications`, `PATCH /{id}/read`) | `[BE]` | F2 | M |
| P6 | Notification events: new/urgent donation, claim confirmed/cancelled, donation cancelled/expired | `[BE]` | P5 | M |
| P7 | Email via JavaMail with `@Async` (failure never breaks a claim) | `[BE]` | P5 | M |
| P8 | Donor and NGO dashboard endpoints | `[BE]` | M3 | M |
| P9 | Complaints: entity, `POST /complaints`, admin list and resolve; add `openComplaints` to the admin dashboard | `[BE]` | F2, M3 | M |
| P10 | UI: notification bell and list | `[FE]` | P5 | M |
| P11 | UI: Donor and NGO dashboards | `[FE]` | P8 | M |
| P12 | UI: complaints form and admin complaints table | `[FE]` | P9 | M |
| P13 | Tests: match score, dispatch rounds (including the 5 km / 10 km example), notification events | `[TEST]` | P1-P4 | L |
| P14 | Report assembly (see section 4) | `[DOC]` | most | L |

---

## 4. Report helper (10 marks)

Each task's "what I built and why" note feeds these sections:

| Report section | Source |
|---|---|
| Introduction, problem, objectives | `PRD.md` §1-3 |
| Requirements | `PRD.md` §6-7 |
| System design, ERD, state machine | `ARCHITECTURE.md` |
| Algorithms and data structures | `ALGORITHMS.md` |
| Implementation | Task notes plus code screenshots |
| Testing | Unit tests, concurrency test, Postman results, `PRD.md` §10 run |
| Results | Demo screenshots, dashboard |
| Conclusion and future work | `PRD.md` §16 |
