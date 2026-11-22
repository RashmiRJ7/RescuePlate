# Product Requirements Document (PRD)

**Project:** Time-Sensitive Surplus Food Rescue
**Course:** Advanced Java
**Version:** 1.0 (initial planning)

---

## 1. Overview

Surplus Food Rescue is a web platform where food donors post surplus food and nearby NGOs claim it before it expires. The system automatically ranks donations by urgency and (in Phase 2) ranks NGOs by suitability, so food reaches recipients within its safe window with minimal manual coordination.

**One-line goal:** *Get safe surplus food to a recipient before its safe-until time, with the least human coordination.*

## 2. Problem statement

- Restaurants, hotels, hostels and events regularly have surplus cooked food.
- Cooked food is safe for only a few hours.
- Donors do not know which NGO can collect in time; NGOs do not know the food exists.
- Result: edible food is wasted while people go hungry.

## 3. Goals and non-goals

### Goals
1. Let donors list surplus food in under a minute.
2. Show NGOs the **most urgent** food first.
3. Guarantee that **only one NGO** can reserve a donation (no double claims).
4. Track every donation through a clear status lifecycle.
5. Automatically expire food whose safe time has passed.
6. (Phase 2) Intelligently match and notify the best NGOs.

### Non-goals (out of scope)
- Payments, delivery fleets, or a mobile app.
- Volunteer role and delivery routing (NGO handles its own pickup).
- Real food-safety certification or health-authority integration.
- Google Maps routing (we use straight-line distance).
- Split allocation of one donation across several NGOs, QR pickup verification, and per-category safe-window caps (considered and deliberately cut to keep the project focused).

## 4. Users

| Role | Description | Main needs |
|---|---|---|
| **Donor** | Restaurant, hotel, hostel, event organizer | Post food quickly, see if it was claimed and delivered |
| **NGO** | Charity or food bank | See urgent food nearby, claim it, confirm pickup |
| **Admin** | Platform manager | Manage users, resolve complaints, see impact numbers |

## 5. Scope by phase

| Feature | Phase 1 | Phase 2 |
|---|---|---|
| Registration and login (Donor, NGO; seeded Admin can log in) | ✅ | |
| Donor posts donations, views own donations | ✅ | |
| Live feed sorted by **urgency score** (4-level tie-breaking) | ✅ | |
| Search and filter feed (category, quantity, distance) | ✅ | |
| NGO claim with duplicate-claim prevention | ✅ | |
| NGO cancels own claim → donation reopens | ✅ | |
| NGO marks pickup and delivery | ✅ | |
| Status tracking and history (stored, readable via API) | ✅ | |
| Expiry monitor (background job) | ✅ | |
| Donor cancels donation | | ✅ |
| Status timeline UI | | ✅ |
| Admin features: user management, deactivation cascade, donation list, claim cancel, stuck-claim resolve | | ✅ |
| Admin dashboard and stats | | ✅ |
| NGO reliability counters | | ✅ |
| **NGO match score** | | ✅ |
| **Smart Batch Dispatch** (top 3, windows, widening radius) | | ✅ |
| Notifications (in-app + email) | | ✅ |
| Donor and NGO dashboards | | ✅ |
| Complaints module | | ✅ |
| Unit and integration tests, polish | | ✅ |

## 6. Functional requirements

Priority: **M** = must, **S** = should, **C** = could.

### 6.1 Accounts and access
| ID | Requirement | Role | Phase | Pri |
|---|---|---|---|---|
| FR-01 | Register as Donor or NGO with name, email, password, phone, address and location (lat/lng; **required for NGOs**, because the feed filters by distance) | Public | 1 | M |
| FR-02 | Log in and log out; passwords stored hashed (BCrypt); inactive users cannot log in | All | 1 | M |
| FR-03 | Role-based access: each endpoint and page is restricted by role | All | 1 | M |
| FR-04 | NGO sets nominal capacity (maximum meals it normally takes in a single pickup; informational guideline to display warnings when exceeded, not a hard claim disqualifier), service radius (km) and accepted categories, and a required location | NGO | 1 | M |
| FR-05 | Admin can activate or deactivate users; deactivation ends the user's sessions and cancels their open donations/claims (see `ARCHITECTURE.md` §7) | Admin | 2 | S |

### 6.2 Donations
| ID | Requirement | Role | Phase | Pri |
|---|---|---|---|---|
| FR-10 | Post a donation: food name, category, quantity, pickup address, location, ready-from, safe-until. Location defaults to the donor's saved location; a donor without one must enter it for the donation (no profile location is forced) | Donor | 1 | M |
| FR-11 | Validate input: quantity ≥ 1, safe-until after ready-from, safe-until at least 30 min from now, valid latitude/longitude ranges; a donation is never created without coordinates | Donor | 1 | M |
| FR-12 | View own donations with current status | Donor | 1 | M |
| FR-13 | Cancel own donation while not yet picked up | Donor | 2 | M |
| FR-14 | Each donation has a time-dependent **urgency score** | System | 1 | M |

### 6.3 Live feed and claiming
| ID | Requirement | Role | Phase | Pri |
|---|---|---|---|---|
| FR-20 | Live feed of claimable donations the NGO is eligible for (accepted category, service radius; flags donations exceeding nominal capacity with a warning), sorted by 4-level priority: (1) urgency score ↓, (2) earlier safeUntil ↑, (3) larger quantity ↓, (4) lower donation id ↑ | NGO | 1 | M |
| FR-21 | Filter by category, minimum quantity and max distance | NGO | 1 | S |
| FR-22 | Claim a donation; **first valid claim wins**, later attempts are rejected with a clear message | NGO | 1 | M |
| FR-23 | NGO can cancel its own claim; donation returns to the feed (Admin claim cancel: Phase 2) | NGO | 1 | M |
| FR-24 | NGO marks **Picked Up** then **Delivered** | NGO | 1 | M |
| FR-25 | Estimated travel time must fit inside the remaining safe time | System | 2 | S |

### 6.4 Tracking and expiry
| ID | Requirement | Role | Phase | Pri |
|---|---|---|---|---|
| FR-30 | Every status change is stored with who/when (history timeline) | System | 1 | M |
| FR-31 | Background job marks unclaimed/unpicked donations **Expired** once safe-until passes | System | 1 | M |
| FR-32 | Illegal status transitions are rejected | System | 1 | M |

### 6.5 Smart matching and dispatch (Phase 2)
| ID | Requirement | Role | Phase | Pri |
|---|---|---|---|---|
| FR-40 | Compute an **NGO match score** from distance, capacity fit, reliability | System | 2 | M |
| FR-41 | Notify the **top 3** eligible NGOs at the same time | System | 2 | M |
| FR-42 | Response window scales with urgency: `clamp(0.15 × minutesLeft, 2, 10)` minutes | System | 2 | M |
| FR-43 | If unclaimed, notify the next batch with a wider radius | System | 2 | M |
| FR-44 | Donation stays on the live feed until reserved | System | 2 | M |
| FR-45 | If a claim is cancelled, reopen the donation and resume dispatch if rounds remain | System | 2 | M |
| FR-46 | When all dispatch rounds end unclaimed, the donation returns to Available and stays claimable on the feed until expiry | System | 2 | M |

### 6.6 Notifications, dashboard, admin (Phase 2)
| ID | Requirement | Role | Phase | Pri |
|---|---|---|---|---|
| FR-50 | In-app notifications for new/urgent food, claim, cancel, expiry | All | 2 | M |
| FR-51 | Email notifications for the same events | All | 2 | S |
| FR-60 | Dashboard with a view per role (see §8) | All | 2 | M |
| FR-61 | Users can file a complaint; admin can review and resolve | All / Admin | 2 | S |
| FR-62 | Admin can view and force-cancel any donation | Admin | 2 | S |
| FR-63 | Admin can resolve a claim stuck in Picked Up, with a required reason: mark it Delivered after verification, or close it as not delivered (food is never reopened to another NGO) | Admin | 2 | S |

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| **Correctness** | No double reservation under simultaneous claims (optimistic locking plus DB constraint) |
| **Security** | BCrypt passwords, role checks on every endpoint, input validation, no secrets in Git |
| **Time handling** | Store all timestamps in UTC; display in IST; validate every time field |
| **Performance** | Feed loads in under 1 second for ~1,000 active donations (indexed queries) |
| **Usability** | Responsive Bootstrap UI; clear error messages (for example "Already claimed by another NGO") |
| **Maintainability** | Layered code, score logic in plain Java classes (unit-testable), weights in config |
| **Reliability** | Background jobs idempotent; a failed email must not break a claim |

## 8. Impact dashboard

*Phase 2.* One dashboard component with role-specific content.

| Role | Shows |
|---|---|
| **Admin** | Total meals posted, meals rescued, meals expired, rescue rate, active donors/NGOs, donations over time, open complaints (Phase 2, with the complaints module) |
| **Donor** | Their meals donated, rescued vs expired, recent donations and statuses |
| **NGO** | Meals received, active claims, pickup completion rate (their reliability; counting rules in `ALGORITHMS.md` §3.2) |

Definitions: **Rescued** = quantity of donations in `DELIVERED`. **Expired** = quantity in `EXPIRED`. **Rescue rate** = rescued ÷ (rescued + expired). Any CO₂ figure is a labelled *estimate* using a configurable factor.

## 9. Core user stories

1. As a **donor**, I post 50 meal packets with a safe-until time so an NGO can collect them in time.
2. As an **NGO**, I see the most urgent food first so I do not waste time on food that can wait.
3. As an **NGO**, I claim food and am told immediately if another NGO got it first.
4. As a **donor**, I see my donation move from Available to Reserved to Delivered.
5. (Phase 2) As an **admin**, I see how much food the platform has saved and deactivate misbehaving accounts.
6. (Phase 2) As an **NGO**, I get notified when urgent food appears near me.

## 10. Acceptance criteria (Phase 1)

- [ ] Donor and NGO can register; all three roles (Admin is seeded) can log in and only reach their own pages and APIs.
- [ ] A donor can post a valid donation; invalid input shows clear errors.
- [ ] The NGO feed is ordered by urgency with 4-level tie-breaking (urgency score ↓, safeUntil ↑, quantity ↓, donation id ↑); a nearly-expired cooked meal appears above a long-life packaged item.
- [ ] Two NGOs claiming the same donation simultaneously: exactly one succeeds, the other receives a conflict message.
- [ ] Cancelling a claim returns the donation to the feed.
- [ ] Status timeline shows every change with time and actor (checked via `GET /donations/{id}/history`; the timeline UI is Phase 2).
- [ ] A donation past its safe-until time becomes **Expired** automatically within 1–2 minutes.

## 11. Edge cases to handle

- (Phase 2) Donor cancels after an NGO has reserved → claim becomes Cancelled (not counted against the NGO's reliability); NGO sees it in "my claims" and is notified.
- NGO reserves but never picks up → donation and claim become Expired at safe-until; from Phase 2 this counts against the NGO's reliability (Phase 2: optional reservation timeout).
- Food expires while reserved → donation Expired, claim Expired.
- No eligible NGO (category or radius) → donation stays claimable and appears in the feed of any NGO that becomes eligible; otherwise it expires at safe-until. (Exceeding nominal capacity does not disqualify an NGO from claiming).
- Two claims at the same millisecond → one wins, the other gets HTTP 409.
- Clock/time-zone mismatch → UTC storage, ISO-8601 in APIs.
- Inactive user → cannot log in (Phase 1). (Phase 2) Deactivation by an Admin also ends sessions immediately and cancels open donations (donor) or active claims (NGO); items already picked up are left unchanged (see `ARCHITECTURE.md` §7).
- NGO picks up but never marks Delivered (including a deactivated NGO) → claim stays Picked Up; from Phase 2 an Admin resolves it with a reason: Delivered if verified, otherwise the claim and donation are Cancelled. The food is never reopened to another NGO.
- Dispatch rounds exhausted with no claim (Phase 2) → donation returns to Available, stays in the feed of eligible NGOs, and expires at safe-until if nobody claims it.

## 12. Course alignment (Advanced Java)

| Concept | Where it is used |
|---|---|
| OOP | Entities, services, enums, calculators, custom exceptions |
| Collections | `PriorityQueue` (urgency ordering), `HashMap`, `HashSet` (notified NGOs), `Queue` (pending notifications), `List` |
| JDBC / Hibernate (JPA) | Entities, repositories, optimistic locking (`@Version`) |
| Spring Boot | Layered backend, dependency injection, configuration |
| REST APIs | All frontend-backend communication |
| Multithreading | `@Scheduled` expiry monitor and dispatcher, `@Async` notifications |
| Exception handling | Custom exceptions plus `@RestControllerAdvice` |
| Security | Spring Security, roles, BCrypt |
| Testing | JUnit 5 and Mockito for scoring and claim logic |

## 13. Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 17, Spring Boot 3, Spring Data JPA/Hibernate, Spring Security |
| Database | MySQL 8 |
| Frontend | HTML, CSS, JavaScript, Bootstrap 5, `fetch` for REST calls |
| Auth | Session-based (cookie) for simplicity; same-origin static frontend |
| Location | Latitude/longitude stored per user and donation; browser Geolocation API to autofill; Haversine distance (no Maps API) |
| Background | Spring `@Scheduled` and `@Async` |
| Email | JavaMail (Phase 2) |
| Tools | Maven, Git, Postman, IntelliJ IDEA / Eclipse |

## 14. Assumptions and risks

| Risk / assumption | Mitigation |
|---|---|
| Only 4–5 days for Phase 1 | Strict scope; skip dispatch, notifications and extra dashboards until Phase 2 |
| Team new to Spring Boot | Boilerplate generated with AI help, but core logic (scoring, claim) written and understood by the owner |
| Time-zone / expiry bugs | UTC everywhere; unit tests for time-based scoring |
| SMTP setup problems | In-app notifications first; email is optional |
| Teammates blocked on each other | API contract agreed on day 1; frontend uses mock JSON until backend is ready |
| Users may enter wrong coordinates | Autofill via browser Geolocation; validate ranges |

**Cut order if time is short:** email, complaints module, Donor/NGO dashboards. **Never cut:** state machine, claim concurrency, urgency score.

## 15. Phase 1 timeline

Phase 1 is a working vertical slice (about 30% of the project): donor posts → NGO sees the urgency-ordered feed → claims (conflict-safe) → picks up → delivers, with automatic expiry.

| Day | Focus |
|---|---|
| 1 | Project setup, database, entities, exceptions, security with seeded admin; in parallel: frontend skeleton, urgency calculator, distance util |
| 2 | Register/login, NGO profile, status changes with history, donation posting; login/register and donor pages |
| 3 | Urgency-sorted eligible feed, donation details/history API, claim with 409 handling; NGO feed page |
| 4 | Cancel claim, pickup/deliver, my claims, expiry monitor; my-claims page, role guards, demo data |
| 5 | Concurrency test, minimal Postman run, acceptance check, README check, demo script; bug fixes |

## 16. Future work (outside this project's scope)

Useful for the report's conclusion only: split allocation across NGOs, QR pickup verification, perishability-based safe-window caps, volunteer role, SSE/WebSocket live updates, Google Maps routing.
