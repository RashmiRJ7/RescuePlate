# Algorithms

**Project:** Time-Sensitive Surplus Food Rescue

This is the core of the project. The system answers **two different questions**, and keeps them separate:

| Question | Algorithm | Inputs |
|---|---|---|
| *Which food needs rescuing first?* | **Donation Urgency Score** | time left, food category (perishability), quantity |
| *Who is the best NGO for this food?* | **NGO Match Score** | distance, capacity fit, reliability |

Mixing these would confuse "what is urgent" with "who is nearby". Urgency orders the **donations**; match orders the **NGOs** for a given donation.

All weights and thresholds live in `application.properties` (bound by a `ScoringProperties` class), so they can be tuned and explained.

---

## 1. Donation Urgency Score (0-100)

*Phase 1.*

```
urgency = 0.60 × timeScore + 0.30 × perishabilityScore + 0.10 × quantityScore
```

### 1.1 Components

**timeScore**: less time left means higher urgency. Linear between two thresholds.

```
minutesLeft = (safeUntil - now) in whole minutes, rounded down
              (the same value is returned as `minutesLeft` in the API)

if minutesLeft <= 30  : timeScore = 100
if minutesLeft >= 360 : timeScore = 0
else                  : timeScore = 100 × (360 - minutesLeft) / (360 - 30)
```

**perishabilityScore**: how fast the category spoils.

| Category | Score |
|---|---|
| `COOKED_MEAL` | 100 |
| `BAKERY` | 60 |
| `FRESH_PRODUCE` | 60 |
| `PACKAGED` | 20 |

**quantityScore**: bigger batches matter more because more meals are at risk.

```
quantityScore = min(quantity, 100)   // 100 or more meals = 100
```

Quantity is capped at 100 in the score and batch size is only used as a tie-breaker, while the NGO match score handles capacity fit.

### 1.2 Worked example

| Donation | Category | Time left | Qty | timeScore | perish. | qtyScore | **Urgency** |
|---|---|---|---|---|---|---|---|
| A | Cooked meal | 30 min | 50 | 100 | 100 | 50 | 0.6×100 + 0.3×100 + 0.1×50 = **95.0** |
| B | Cooked meal | 3 h (180 min) | 40 | 54.5 | 100 | 40 | 32.7 + 30 + 4 = **66.7** |
| C | Packaged | 5 h (300 min) | 100 | 18.2 | 20 | 100 | 10.9 + 6 + 10 = **26.9** |

Order shown to NGOs: **A → B → C**. Feed order follows a 4-level priority: (1) Urgency score descending (scores equal after rounding to 1 decimal place are tied), (2) earlier `safeUntil` ascending, (3) larger `quantity` descending, and (4) lower donation `id` ascending (stable, deterministic order).

### 1.3 Important notes

- The score is **time-dependent**, so it is computed when the feed is read. It is never stored.
- If `minutesLeft <= 0` the donation is expired and excluded from the feed.
- Pure function: `double score(Donation d, Instant now)`. No database, no Spring. Easy to unit test with a fixed `now`.

### 1.4 Test cases to write

| Case | Expected |
|---|---|
| 20 min left | timeScore = 100 |
| 400 min left | timeScore = 0 |
| 195 min left | timeScore = 50 |
| Cooked vs packaged, same time/qty | cooked scores higher |
| qty = 500 | qtyScore capped at 100 |
| Benchmark A (Cooked, 30m, qty 50) | urgency = 95.0 |
| Benchmark B (Cooked, 180m, qty 40) | urgency = 66.7 |
| Benchmark C (Packaged, 300m, qty 100) | urgency = 26.9 |
| Benchmark D (Bakery, 310m, qty 30) | urgency = 30.1 |
| Clear urgency difference | higher urgency sorts first |
| Tied urgency (raw scores differing only after 1st decimal) | earlier safeUntil sorts first |
| Tied urgency and safeUntil | larger quantity sorts first |
| Everything tied (urgency, safeUntil, quantity) | lower donation id sorts first (deterministic) |
| Qty 150 vs 200 (both qtyScore = 100) | 200 listed first (tie-breaker) |

---

## 2. Distance (Haversine)

Straight-line distance between two lat/lng points. No external API needed.

```
a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlng/2)
distanceKm = 2 × R × asin(√a)        where R = 6371 km
```

Estimated travel time (used for feasibility): `travelMinutes = distanceKm × 3` (assumes about 20 km/h city traffic; configurable).

---

## 3. NGO Match Score (0-100)

*Match scoring is Phase 2. The eligibility filter (§3.1) is used **from Phase 1**: the NGO feed and the claim check use it, and the feed shows the distance next to each donation. Phase 1 does not score NGOs.*

```
match = 0.50 × distanceScore + 0.30 × capacityScore + 0.20 × reliabilityScore
```

### 3.1 Eligibility filter (applied *before* scoring)

One set of hard rules decides whether an NGO is eligible for a donation. They are used in three places so that the feed, the claim check and dispatch never disagree. Distance is Haversine (§2) from the NGO user's stored latitude/longitude to the donation's pickup location. Both are guaranteed to exist: NGO coordinates are required at registration, and a donation cannot be created without coordinates (`API_DOCUMENTATION.md` §5.1).

An NGO is eligible only if **all** hard rules are true:

| # | Rule | Feed (`GET /donations/feed`) | Claim (`POST /donations/{id}/claim`) | Dispatch (P2) |
|---|---|---|---|---|
| 1 | NGO user is active | ✓ | ✓ | ✓ |
| 2 | Distance within radius | `distance ≤ ngo.serviceRadiusKm` | `distance ≤ ngo.serviceRadiusKm` | `distance ≤ min(roundRadius, ngo.serviceRadiusKm)` |
| 3 | Donation category is in the NGO's accepted categories | ✓ | ✓ | ✓ |
| 4 | `travelMinutes ≤ minutesLeft - 10` (it can physically arrive in time, with a 10-minute buffer; FR-25) | P2 | P2 | ✓ |
| 5 | NGO has not already been notified for this donation (see `dispatch_log`) | n/a | n/a | ✓ |

> **Capacity is an informational guideline, not a hard disqualifier:** `ngo.capacityMeals` indicates the NGO's nominal pickup capacity. It is **not** a hard filter. An NGO can see and claim donations where `quantity > capacityMeals` (full donation only; split allocation is out of scope). When `quantity > capacityMeals`, the feed returns `exceedsCapacity = true`, alerting the NGO: *"⚠️ Exceeds registered pickup capacity. Claim only if you can manage the full quantity."* In Phase 2 match scoring, under-capacity reduces `capacityScore` rather than blocking the NGO.

**Feed:** the feed shows only claimable donations for which the NGO passes rules 1-3 (plus rule 4 in Phase 2). The optional `maxDistanceKm` filter can narrow the radius further but never widen it beyond `serviceRadiusKm`. Because the feed is computed on every read, a profile change affects the feed immediately.

**Claim:** the claim request re-checks the hard rules (active, distance, category). A failure returns **400** (`NgoNotEligibleException`). This guards against direct API calls and stale pages. Exceeding nominal capacity does not return 400.

**No eligible NGO:** nothing special happens. The donation stays in its claimable status. It appears in the feed of any NGO that becomes eligible later (for example by widening its radius or adding categories). If nobody claims it, the expiry monitor sets it to `EXPIRED` at `safeUntil`. The donor sees the status on "my donations", and the admin sees it in the admin donation list and in `mealsExpired`. In Phase 2, dispatch also widens the radius round by round (§4).

### 3.2 Components

**distanceScore**: closer is better, normalised by the NGO's own radius.

```
distanceScore = 100 × (1 - distanceKm / ngo.serviceRadiusKm)
```

**capacityScore**: how well the NGO's capacity fits the donation. Perfect fit from exact match up to 3× quantity; under-capacity NGOs receive a proportional penalty; oversized NGOs score lower so small donations do not always go to the biggest NGO.

```
if capacity < quantity      : capacityScore = max(20, 100 × capacity / quantity)
else if capacity <= 3 × quantity : capacityScore = 100
else                        : capacityScore = max(40, 100 × (3 × quantity) / capacity)
```

**reliabilityScore**: past follow-through.

```
if totalClaims < 3 : reliabilityScore = 70        // new NGOs are not punished
else               : reliabilityScore = 100 × completedClaims / totalClaims
```

*The counters are updated from Phase 2 (task P15); in Phase 1 they stay at 0.* The counters (`ngo_profiles.total_claims`, `completed_claims`) follow the claim lifecycle in `ARCHITECTURE.md` §4. **A claim counts only if its outcome was the NGO's responsibility:**

| Claim outcome | `totalClaims` | `completedClaims` | Why |
|---|---|---|---|
| `COMPLETED` (delivered by the NGO, or verified by an Admin resolution) | counted | +1 | success |
| `CANCELLED` by the NGO, by an Admin, because the NGO was deactivated, or by an Admin resolution of a stuck picked-up claim as `NOT_DELIVERED` | counted | no change | NGO did not follow through |
| `EXPIRED` (reserved but not picked up before `safeUntil`) | counted | no change | NGO did not collect in time |
| `CANCELLED` because the *donation* was cancelled (donor, admin, or donor deactivated) | **not counted** (`totalClaims` −1) | no change | not the NGO's fault |
| `ACTIVE` / `PICKED_UP` (still open) | counted | no change | outcome pending |

In practice, `totalClaims` +1 on claim, −1 only in the "donation cancelled" case, and `completedClaims` +1 on delivery. The same ratio is shown to the NGO as `reliabilityPercent` (`API_DOCUMENTATION.md` §4) and on the NGO dashboard as `completionRatePercent`.

### 3.3 Worked example

Donation: **50 meals** of cooked food, three NGOs nearby.

| NGO | Distance | Radius | Capacity | Reliability | distScore | capScore | relScore | **Match** |
|---|---|---|---|---|---|---|---|---|
| NGO1 | 2 km | 10 km | 80 | 9/10 | 80 | 100 | 90 | 40 + 30 + 18 = **88** |
| NGO3 | 1 km | 5 km | 300 | 8/10 | 80 | 50 (300 > 150) | 80 | 40 + 15 + 16 = **71** |
| NGO2 | 6 km | 10 km | 60 | new | 40 | 100 | 70 | 20 + 30 + 14 = **64** |

Ranking: **NGO1 (88) → NGO3 (71) → NGO2 (64)**. NGO3 is closer but loses points because its capacity is far larger than needed.

If round 1 has radius 5 km, NGO2 (6 km away) is not eligible yet; it appears in round 2 (see below).

### 3.4 Test cases to write

| Case | Expected |
|---|---|
| NGO capacity < quantity | eligible, exceedsCapacity=true flag, capacityScore scaled down (not filtered out) |
| Distance beyond radius | filtered out |
| Travel time exceeds time left | filtered out |
| Category not accepted | filtered out |
| New NGO (< 3 claims) | reliability 70 |
| Claim closed because donor cancelled the donation | does not lower reliability |
| Capacity 10× quantity | capacityScore floors at 40 |
| Equal scores | stable ordering (nearest first) |

---

## 4. Smart Batch Dispatch + Live Claiming

*Phase 2.* Instead of offering food to one NGO at a time (slow), notify a **batch** at once and let the first valid claim win.

### 4.1 Rules

1. Build the candidate list (eligibility filter, §3.1) and rank by match score.
2. Notify the **top 3** at the same time. Record each in `dispatch_log`.
3. Start a **response window**:
   ```
   windowMinutes = clamp(0.15 × minutesLeft, 2, 10)
   ```
   (25 min left gives 3.75 min; 3 hours left gives 10 min.)
4. If nobody claims when the window ends, run the **next round**: widen the radius and notify the next 3 not-yet-notified NGOs.
5. The donation **stays on the live feed** the whole time. Any eligible NGO may claim, even if not notified.
6. **First valid claim wins**; every other claim attempt gets HTTP 409.
7. If a claim is cancelled, the donation reopens. If rounds remain (`dispatchRound < maxRounds`), it returns to `DISPATCHING` and dispatch resumes with NGOs not yet notified; otherwise it returns to `AVAILABLE` (§4.2).

### 4.2 Round configuration

| Round | Radius (default) |
|---|---|
| 1 | 5 km |
| 2 | 10 km |
| 3 | 15 km |
| 4 | 20 km (cap) |

**After the final round** (the response window of round `maxRounds` ends with no claim), dispatch is finished:

| Question | Answer |
|---|---|
| Status | `AVAILABLE`. A `DISPATCHING` donation moves `DISPATCHING → AVAILABLE` (System, history note "Dispatch rounds exhausted"), and `roundEndsAt` is set to `null`. `DISPATCHING` is never shown when no round or window is scheduled. |
| Visible in the feed? | Yes. `AVAILABLE` is claimable, so it stays in the feed of every eligible NGO (§3.1), sorted by urgency like any other donation. |
| How is it claimed? | Normally: an eligible NGO sees it in the feed and calls `POST /donations/{id}/claim`. No further notifications are sent. |
| Afterwards | It stays `AVAILABLE` until it is claimed, cancelled by the donor/admin, or expires at `safeUntil`. If a later claim on it is cancelled, it reopens as `AVAILABLE` (rule 7). |

### 4.3 Scheduler loop (every 30 seconds)

```
// Step 1: finish dispatch for donations whose last round has ended
finished = donations in (AVAILABLE, DISPATCHING)
           where dispatchRound >= maxRounds
           and roundEndsAt is not null and roundEndsAt <= now
for d in finished:
    if d.status == DISPATCHING:
        changeStatus(d, AVAILABLE, "Dispatch rounds exhausted")   // via DonationService only
    d.roundEndsAt = null
    save(d)

// Step 2: run the next round for the rest
candidates = donations in (AVAILABLE, DISPATCHING)
             where dispatchRound < maxRounds          // rounds 1..maxRounds
             and (roundEndsAt is null or roundEndsAt <= now)

queue = new PriorityQueue(by urgency DESC)   // most urgent donation first
queue.addAll(candidates)

while queue not empty:
    d = queue.poll()
    round = d.dispatchRound + 1
    ngos = rank(eligibleNgos(d, radius(round)))           // match score
    batch = first 3 of ngos
    if batch is empty:
        d.dispatchRound = round                            // try wider radius next tick
    else:
        notify(batch); log each in dispatch_log
        d.dispatchRound = round
        if d.status == AVAILABLE:
            changeStatus(d, DISPATCHING)                   // via DonationService only
    d.roundEndsAt = now + window(d)
    save(d)
```

Because the queue is ordered by **urgency**, the most urgent donation is always dispatched first. This is where the two scores work together.

### 4.4 Edge cases

| Situation | Behaviour |
|---|---|
| No eligible NGO in a round | Round counter advances, radius widens next tick |
| No eligible NGO in any round | Dispatch ends; donation stays (or becomes) `AVAILABLE` on the feed until expiry (§3.1, "No eligible NGO"; §4.2) |
| All rounds used, nobody claimed | Step 1 of the loop sets `DISPATCHING → AVAILABLE`, `roundEndsAt = null` (§4.2) |
| Donation expires mid-dispatch | `ExpiryMonitor` sets `EXPIRED`; dispatcher skips it |
| Claim cancelled, rounds left | Status back to `DISPATCHING`, `roundEndsAt = now`, next tick continues with un-notified NGOs |
| Claim cancelled, no rounds left | Status back to `AVAILABLE`, `roundEndsAt = null`; no further dispatch |
| Scheduler runs twice | Idempotent: `roundEndsAt`, the `roundEndsAt = null` reset, and `dispatch_log` prevent duplicate notifications or transitions |

---

## 5. Claim allocation ("first valid claim wins")

Not a scoring algorithm, but a correctness one. Summary of the safeguards (full detail in `ARCHITECTURE.md` §5):

1. State check: donation must be `AVAILABLE` or `DISPATCHING` and not past `safeUntil`; otherwise **HTTP 409**.
2. Eligibility check: the NGO must pass §3.1; otherwise **HTTP 400**.
3. Optimistic lock: `@Version` on `Donation`.
4. Unique constraint on `claims.active_donation_id`.
5. Lock and unique-key conflicts return **HTTP 409**, the same as step 1.

---

## 6. Data structures used

| Structure | Where | Why |
|---|---|---|
| `PriorityQueue<Donation>` | Dispatch loop; feed ordering | Always yields the most urgent donation next (comparator: urgency ↓, safeUntil ↑, quantity ↓, id ↑) |
| `ArrayList` | Feed results, NGO candidate lists | Ordered display lists, sorting with `Comparator` |
| `HashMap<Long, Donation>` | In-memory lookup during a dispatch tick | O(1) lookup by donation ID |
| `HashSet<Long>` | NGO IDs already notified for a donation | Prevents re-notifying the same NGO |
| `Queue` (`ArrayDeque` / `LinkedBlockingQueue`) | Pending notifications (Phase 2) | Process in order, off the request thread |

Complexities worth quoting in the report: ranking *n* candidate NGOs is O(n log n); polling the next donation from a `PriorityQueue` is O(log n); expiry scan uses the `(status, safe_until)` index.

---

## 7. Impact metrics

Computed by `DashboardService` from donations and claims.

```
mealsRescued = sum(quantity) where status = DELIVERED
mealsExpired = sum(quantity) where status = EXPIRED
rescueRate   = mealsRescued / (mealsRescued + mealsExpired)     // 0 if denominator is 0
co2Estimate  = mealsRescued × co2PerMeal                       // labelled "estimate"
```

`co2PerMeal` is a configurable placeholder, not a measured value; show it as an estimate in the UI.

---

## 8. Configuration (excerpt)

```properties
# Urgency
food-rescue.urgency.weight-time=0.60
food-rescue.urgency.weight-perishability=0.30
food-rescue.urgency.weight-quantity=0.10
food-rescue.urgency.time-full-urgency-minutes=30
food-rescue.urgency.time-zero-urgency-minutes=360
food-rescue.urgency.quantity-cap=100

# Match (Phase 2)
food-rescue.match.weight-distance=0.50
food-rescue.match.weight-capacity=0.30
food-rescue.match.weight-reliability=0.20
food-rescue.match.new-ngo-reliability=70
food-rescue.match.new-ngo-claim-threshold=3
food-rescue.match.travel-minutes-per-km=3
food-rescue.match.arrival-buffer-minutes=10

# Dispatch (Phase 2)
food-rescue.dispatch.batch-size=3
food-rescue.dispatch.window-factor=0.15
food-rescue.dispatch.window-min-minutes=2
food-rescue.dispatch.window-max-minutes=10
food-rescue.dispatch.radius-km=5,10,15,20

# Impact
food-rescue.impact.co2-kg-per-meal=2.5
```

Weights in each group must sum to 1.0; validate this at startup.
