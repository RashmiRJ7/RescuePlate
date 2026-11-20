# Backend Roadmap (Phase 1)

**Project:** RescuePlate (Time-Sensitive Surplus Food Rescue)
**Owner:** Rashmi + Mehak (backend core)
**Scope:** The reduced Phase 1 (about 30% of the project). Task IDs refer to `TASKS.md`; paths and rules refer to `API_DOCUMENTATION.md`, `ARCHITECTURE.md` and `ALGORITHMS.md`. If those docs disagree with this file, the docs win.

---

## 0. How to work (read once)

**The loop for every step:** **Learn** (20-30 min) → **Build** → **Break it on purpose** → **Explain** (3 lines on the task card).

**Use AI like this:**

| Let AI generate | Write yourself, or retype and explain every line |
|---|---|
| Entities, repositories, DTO records, controllers, Bootstrap pages, test boilerplate | `UrgencyScoreCalculator`, `changeStatus`, `ClaimService.claim()`, `ExpiryMonitor`, security config logic |

Rule: **if you can't explain a line, you don't commit it.**

**Daily routine**
1. Pull `main`, run `.\mvnw.cmd verify` (Windows) before you start.
2. One task at a time, one branch per task (`feature/D1-urgency-calculator`).
3. Run the app and hit the endpoint in Postman before opening a PR.
4. Write your "what I built and why" note on the card (feeds the report).
5. End of day: merge, push, tell the team what changed.

**Commands (Windows)**
```
.\mvnw.cmd verify                 # build + all tests
.\mvnw.cmd spring-boot:run        # start the app
.\mvnw.cmd test -Dtest=UrgencyScoreCalculatorTest   # one test class
```

**Plan by day**

| Day | Steps |
|---|---|
| 1 | 0, 1, 2, 3 |
| 2 | 4, 5, 6 |
| 3 | 7, 8 |
| 4 | 9, 10 |
| 5 | 11, 12 |

If you fall behind, protect **Steps 3, 4, 8, 9, 11**. They are the core of the project.

---

## Step 0: Environment (1-2 h)

**Goal:** the app runs and talks to MySQL.

**Do**
- Install MySQL 8. Create the database: `CREATE DATABASE food_rescue CHARACTER SET utf8mb4;`
- Copy `.env.example` to `.env` and fill `DB_USERNAME` / `DB_PASSWORD`. Never commit `.env`.
- Run the app and open `GET /api/health`.

**Learn:** how Spring Boot starts, what a bean is, what dependency injection means, how `application.properties` reads `.env` values.

**Done when:** the app starts with the DB connected and `/api/health` returns JSON.

**If it fails:** "Public Key Retrieval is not allowed" → add `allowPublicKeyRetrieval=true` to `DB_URL`. "Access denied" → check the user and password in `.env`.

---

## Step 1: Enums and entities (F2)

**Goal:** the database schema from `ARCHITECTURE.md` §3 exists.

**Learn**
- `@Entity`, `@Id @GeneratedValue`, `@ManyToOne(fetch = LAZY)`, `@Column`
- `@Enumerated(EnumType.STRING)` (never `ORDINAL`)
- `@Version` for optimistic locking
- Unique columns and indexes
- `Instant` for timestamps (UTC)

**Build:** enums (`Role`, `FoodCategory`, `DonationStatus`, `ClaimStatus` incl. `EXPIRED`); entities `User`, `NgoProfile`, `Donation`, `Claim`, `DonationStatusHistory`; repositories.

**Check:** run the app with `JPA_DDL_AUTO=update`, then in MySQL: `SHOW CREATE TABLE claims;`. Confirm `active_donation_id` is unique and nullable, and `donations` has `version`.

**Traps**
- Don't use Lombok `@Data` on entities (it breaks `equals`/`hashCode` with JPA).
- Every entity needs a no-arg constructor.
- Don't return entities from controllers. Use DTOs.

**Explain:** *Why `LAZY`? Why `STRING` enums?*

---

## Step 2: Exceptions and DTOs (F3)

**Goal:** every error returns the standard JSON from `API_DOCUMENTATION.md` §1.

**Learn:** `@RestControllerAdvice`, `@ExceptionHandler`, Bean Validation (`@Valid`, `@NotBlank`, `@Min`, `@Size`), Java `record` DTOs.

**Build**
- `ResourceNotFoundException` → 404
- `DonationNotClaimableException` → **409**
- `NgoNotEligibleException` → **400**
- `InvalidStatusTransitionException` → 400
- Validation errors → 400 with `fieldErrors`
- Optimistic-lock and unique-key exceptions → 409
- A fallback handler → 500 with a generic message

**Check:** temporarily throw each exception from a test controller and confirm the status code and JSON. Remove the test controller afterwards.

**Explain:** *Why does "already claimed" give 409 but "your NGO is too small" gives 400?* (The first is a conflict with the current state; the second is a problem with this NGO's request.)

---

## Step 3: Pure-Java algorithms (D1, D2)

**Goal:** your first big win. No Spring, no database.

**Learn:** JUnit 5, `@ParameterizedTest`, why you pass `Instant now` into the method (so tests are deterministic), `Comparator`.

**Build**
- `ScoringProperties` (weights from `application.properties`)
- `UrgencyScoreCalculator.score(Donation, Instant now)` per `ALGORITHMS.md` §1
- `DistanceUtil.haversineKm(lat1, lng1, lat2, lng2)`

**Check (write the tests first)**

| Case | Expected |
|---|---|
| Cooked, 30 min, qty 50 | 95.0 |
| Cooked, 180 min, qty 40 | 66.7 |
| Packaged, 300 min, qty 100 | 26.9 |
| Bakery, 310 min, qty 30 | 30.1 |
| 20 min left | timeScore 100 |
| 400 min left | timeScore 0 |
| Two cities with a known distance | within ±1 km |

**Traps:** integer division (`50/330` is `0`), forgetting to cap quantity at 100, and computing `minutesLeft` with the wrong unit.

**Explain:** *Why is urgency computed instead of stored?*

---

## Step 4: State machine (D3), `[core]`

**Goal:** one place decides what status changes are legal.

**Learn:** a transition table (`Map<DonationStatus, Set<DonationStatus>>`), `@Transactional`, why a single entry point prevents bugs.

**Build:** `DonationService.changeStatus(donation, newStatus, actor, note)` that
1. checks the transition is allowed (else `InvalidStatusTransitionException`),
2. sets the status,
3. writes a `DonationStatusHistory` row (actor = user, or null for the system),
4. closes the active claim when the new status is `EXPIRED` or `CANCELLED` (see `ARCHITECTURE.md` §4).

**Check:** one unit test per legal transition and several illegal ones (for example `DELIVERED → AVAILABLE`). **Nothing else in the code may call `setStatus`.** Search for it before you finish.

**Explain:** *Draw the state diagram from memory and say who triggers each arrow.*

---

## Step 5: Security (F4), `[core]`

**Goal:** three roles, session login, role rules from `ARCHITECTURE.md` §7.

**Learn:** the `SecurityFilterChain` bean (no more `WebSecurityConfigurerAdapter`), `UserDetailsService`, `PasswordEncoder` (BCrypt), `AuthenticationManager`, role matchers with `requestMatchers`, how the `JSESSIONID` session cookie works.

**Build**
- Load a user by email and map the role to an authority (`ROLE_DONOR` and so on)
- BCrypt bean
- Role rules, with more specific rules **before** general ones
- `DataSeeder` that creates the admin from properties if missing
- JSON 401 and 403 responses instead of redirects to a login page

**Senior tip:** in Spring Security 6 a JSON login endpoint must **save the security context to the session** itself after `authenticate(...)`; otherwise login "works" but the next request is anonymous. Look up `SecurityContextRepository.saveContext`.

**Check (Postman, cookie jar on)**

| Request | Expected |
|---|---|
| Any protected path without login | 401 |
| Donor calls an NGO-only path | 403 |
| NGO calls an NGO-only path | passes |
| Wrong password | 401 |
| Inactive user logs in | 401 |

**Explain:** *What does BCrypt give you that SHA-256 doesn't?* (Salt and a deliberately slow work factor.)

---

## Step 6: Accounts (A1-A3)

**Do:** `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`, `GET/PUT /ngo/profile`.

**Rules to enforce**
- Duplicate email → 409
- `role = ADMIN` rejected on registration
- Password at least 8 characters
- **NGO registration requires latitude and longitude** (the feed filters by distance)
- Lat in [-90, 90], lng in [-180, 180]
- An NGO can read and update **only its own** profile

**Check:** register a donor and an NGO; log in as each; `GET /auth/me` returns the right role.

**Learn:** getting the current user inside a service (`SecurityContextHolder` or `@AuthenticationPrincipal`) and why you never trust a user ID sent by the client.

---

## Step 7: Donations (D4, D7)

**Do:** `POST /donations`, `GET /donations/mine`, `GET /donations/{id}`, `GET /donations/{id}/history`.

**Validation (from the API doc):** quantity 1-1000; `safeUntil` after `readyFrom` and at least 30 minutes from now; `readyFrom` defaults to now; coordinates default to the donor's.

**Learn:** converting between `Instant` (UTC) and the JSON ISO string; putting the donor from the session, not from the request, into `donor_id`.

**Check**
- A valid post returns 201 and status `AVAILABLE`.
- `safeUntil` in the past returns 400 with `fieldErrors`.
- Donor A cannot read donor B's donation (403 or 404).
- History shows one row ("Donation posted").

---

## Step 8: The feed (D5), `[core]`

**Goal:** claimable food, most urgent first, only for NGOs that are eligible for it.

**Learn:** fetch the candidate rows from the DB (`status in (AVAILABLE, DISPATCHING)` and `safeUntil > now`), then **filter, score and sort in Java**. Why: urgency depends on "now", so SQL can't pre-sort it.

**Build** `FeedService.getFeed(ngoUser, filters)`:
1. Load claimable donations.
2. Keep only those the NGO is eligible for (active, accepted category, distance ≤ radius). `maxDistanceKm` may narrow the radius, never widen it. (Capacity is an informational guideline, not a blocker).
3. Compute `urgencyScore`, `minutesLeft` (whole minutes, rounded down), `distanceKm`, and `exceedsCapacity` flag (`quantity > ngo.capacityMeals`).
4. Sort by urgency descending, then `safeUntil` ascending. Use a `Comparator`; `PriorityQueue` is the alternative to explain.
5. Return DTOs.

**Check:** seed 3 donations (cooked/30 min, cooked/3 h, packaged/5 h). The order must be 95.0, 66.7, 26.9. Then change the NGO's accepted categories and confirm the feed shrinks.

**Explain:** *Why is the sort done in Java? What's the time complexity?* (O(n log n).)

---

## Step 9: Claim, your showpiece (C1, C5), `[core]`

**Goal:** exactly one winner when two NGOs claim at once.

**Learn:** race conditions, optimistic locking (`@Version`), unique constraints, how `ObjectOptimisticLockingFailureException` and `DataIntegrityViolationException` happen.

**Build** `ClaimService.claim(donationId, ngoUser)` in one `@Transactional` method (order of checks: **404 → 409 → 400**):
1. Load the donation (404 if missing).
2. If not claimable (wrong status or expired) → `DonationNotClaimableException` (409).
3. If the NGO isn't eligible (category or distance mismatch) → `NgoNotEligibleException` (400). Note: exceeding capacity is allowed for full pickup.
4. Create `Claim` with `status = ACTIVE` and `activeDonationId = donationId`.
5. `changeStatus(RESERVED)`.
6. `saveAndFlush`, then map the two exceptions above to 409 in the handler.

**Senior tip:** a version conflict is raised at **flush or commit**, which can be *after* your method returns. If you only wrap the body in try/catch you'll miss it. Use `saveAndFlush`, and let the exception handler translate whatever escapes.

**Check**
- Postman: claim the same donation as two NGOs → 201 then 409.
- **C5:** a test with two threads, a `CountDownLatch` so both start together, asserting exactly one success and one 409. **Run it 20 times.**

**Explain:** *Why both `@Version` and the unique column?* (Two independent guards: the first protects the donation row, the second protects the database even if application logic is wrong.) *Redraw the sequence diagram from `ARCHITECTURE.md` §5 from memory.*

---

## Step 10: Claim lifecycle (C2, C3, C4)

**Do**
- `PATCH /claims/{id}/cancel`: claim → `CANCELLED`, `activeDonationId` → null, donation reopens to `AVAILABLE` (Phase 1).
- `PATCH /claims/{id}/pickup`: claim and donation → `PICKED_UP`.
- `PATCH /claims/{id}/deliver`: claim → `COMPLETED`, donation → `DELIVERED`.
- `GET /claims/mine` with optional `?status=`.

**All status changes go through `changeStatus`.** Check ownership every time (an NGO can only touch its own claim). Wrong order (deliver before pickup) → 400. No reliability counters in Phase 1.

**Check:** cancel a claim, confirm the donation reappears in the feed, then claim it again with a different NGO.

---

## Step 11: Expiry monitor (S1), `[core]`

**Goal:** food past `safeUntil` expires by itself.

**Learn:** `@EnableScheduling`, `@Scheduled(fixedDelay = 60_000)`, idempotency, and the **self-invocation trap**: `@Transactional` does nothing when a method calls another method *in the same class*. Put the transactional work in a separate service bean (for example `DonationService.expireOverdue()`), and let the scheduler class only call it.

**Build:** find `AVAILABLE`, `DISPATCHING` and `RESERVED` donations with `safeUntil < now`; call `changeStatus(EXPIRED)` (actor = System), which also sets any active claim to `EXPIRED`.

**Check:** the 30-minute minimum blocks quick tests. Edit `safe_until` directly in MySQL to a past time, wait one tick, and confirm `EXPIRED`, the history row says "System", and the claim is `EXPIRED`. Run the job twice: the second run must change nothing.

**Explain:** *What makes the job idempotent? Why a separate bean?*

---

## Step 12: Harden and demo (T1-T5)

- **T2 `DemoDataSeeder`:** 1 admin, 3 donors, 3 NGOs, about 10 donations with varied expiry and categories. NGOs must satisfy the eligibility rules, or the feed will look empty.
- **T1 Postman:** happy path per role plus the double-claim pair.
- **T3 Acceptance run** against `PRD.md` §10.
- **T4:** a teammate follows `README.md` from a clean checkout.
- **T5 Demo script:** donor posts → NGO sees urgency order → claim → conflict demo → pickup → delivered → (expiry demo with a past `safe_until`).
- **Final read-through** of every `[core]` file. Remove dead code and debug prints. Check that no secrets are committed.

---

## Appendix A: Phase 1 Postman matrix

| Area | Request | Expected |
|---|---|---|
| Auth | Register donor / NGO | 201 |
| Auth | Duplicate email | 409 |
| Auth | Register as ADMIN | 400 |
| Auth | Login wrong password | 401 |
| Donor | Post valid donation | 201 `AVAILABLE` |
| Donor | Post with past `safeUntil` | 400 with `fieldErrors` |
| NGO | Feed | Sorted by urgency |
| NGO | Claim | 201 |
| NGO 2 | Claim same donation | **409** |
| NGO | Pickup, then deliver | 200, 200 |
| NGO | Deliver before pickup | 400 |
| Donor | Call an NGO-only endpoint | 403 |
| None | Protected endpoint without login | 401 |

## Appendix B: Viva questions you should answer without notes

1. Why do we have both `@Version` and a unique `active_donation_id`?
2. Why is the urgency score computed at read time and not stored?
3. What's the difference between the urgency score and the match score?
4. Why use DTOs instead of returning entities?
5. What does `@Transactional` actually do, and when doesn't it work?
6. What happens when two NGOs claim at the same time? Walk through it.
7. Why BCrypt instead of a plain hash?
8. Why 409 for an already-claimed donation but 400 for an ineligible NGO?
9. Why does only `changeStatus` set the status?
10. How does the expiry monitor avoid expiring something twice?
11. Why store timestamps in UTC?
12. Why Haversine instead of the Google Maps API?

## Appendix C: Quick cheat sheet

| You need | Use |
|---|---|
| Optimistic lock | `@Version Long version` on `Donation` |
| Unique constraint | `@Column(unique = true)` on `Claim.activeDonationId` |
| Enum in DB | `@Enumerated(EnumType.STRING)` |
| Validation | `@Valid` on the request body, constraints on the record fields |
| Global errors | `@RestControllerAdvice` |
| Background job | `@Scheduled` plus `@EnableScheduling` |
| Current user | `@AuthenticationPrincipal` or `SecurityContextHolder` |
| Deterministic time in tests | pass `Instant now` as a parameter (or inject a `Clock`) |
| Test a race | `ExecutorService` plus `CountDownLatch`, run repeatedly |

## Appendix D: Phase 2 preview (do not start yet)

`MatchScoreCalculator` → `DispatchLog` and `DispatchService` → `DispatchScheduler` (PriorityQueue by urgency) → claim-cancel hook for dispatch → notifications (in-app, then email with `@Async`) → admin endpoints and dashboard → donor and NGO dashboards → complaints → reliability counters. See the Phase 2 backlog in `TASKS.md`.
