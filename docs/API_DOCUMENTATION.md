# API Documentation

**Project:** Time-Sensitive Surplus Food Rescue
**Base URL:** `http://localhost:8080/api`
**Format:** JSON (`Content-Type: application/json`)

This is the **contract** between frontend and backend. Change it only after telling the whole team. The frontend can build against the example responses below (mock JSON) before the real endpoints exist.

---

## 1. Conventions

| Topic | Rule |
|---|---|
| **Auth** | Session cookie (`JSESSIONID`) set by `POST /auth/login`. Browser sends it automatically (same origin). |
| **Roles** | `DONOR`, `NGO`, `ADMIN` |
| **Timestamps** | ISO-8601 in **UTC**, e.g. `2026-11-20T14:30:00Z`. The UI converts to IST for display. |
| **IDs** | Numeric (`Long`) |
| **Phase tags** | **P1** = Phase 1, **P2** = Phase 2 |
| **Pagination** (lists) | Optional `?page=0&size=20` |

### Error format (all errors)

```json
{
  "timestamp": "2026-11-20T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "message": "This donation has already been claimed by another NGO.",
  "path": "/api/donations/7/claim"
}
```

For validation errors (400), add a field map:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "quantity": "must be at least 1", "safeUntil": "must be at least 30 minutes from now" },
  "path": "/api/donations"
}
```

### Status codes

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 400 | Validation error, illegal status transition, or NGO not eligible for the donation |
| 401 | Not logged in (or session ended by deactivation) |
| 403 | Logged in but wrong role / not the owner |
| 404 | Resource not found |
| 409 | Conflict (donation no longer claimable: already claimed, expired, cancelled, etc.; duplicate email; version clash) |
| 500 | Unexpected server error |

### Enums

| Enum | Values |
|---|---|
| `Role` | `DONOR`, `NGO`, `ADMIN` |
| `FoodCategory` | `COOKED_MEAL`, `BAKERY`, `FRESH_PRODUCE`, `PACKAGED` |
| `DonationStatus` | `AVAILABLE`, `DISPATCHING`, `RESERVED`, `PICKED_UP`, `DELIVERED`, `EXPIRED`, `CANCELLED` |
| `ClaimStatus` | `ACTIVE`, `PICKED_UP`, `COMPLETED`, `CANCELLED`, `EXPIRED` |

---

## 2. Endpoint summary

| # | Method | Path | Role | Phase | Purpose |
|---|---|---|---|---|---|
| 0 | GET | `/health` | public | P1 | Liveness check → `200 { "status": "UP", "timestamp": "..." }` |
| 1 | POST | `/auth/register` | public | P1 | Register Donor or NGO |
| 2 | POST | `/auth/login` | public | P1 | Log in |
| 3 | POST | `/auth/logout` | any | P1 | Log out |
| 4 | GET | `/auth/me` | any | P1 | Current user |
| 4a | GET | `/auth/admin-setup/status` | public | P1 | Check if initial admin setup is available |
| 4b | POST | `/auth/admin-setup` | public + setup key | P1 | One-time first admin creation |
| 5 | GET | `/ngo/profile` | NGO | P1 | Get NGO settings |
| 6 | PUT | `/ngo/profile` | NGO | P1 | Update capacity, radius, categories |
| 7 | POST | `/donations` | DONOR | P1 | Post surplus food |
| 8 | GET | `/donations/mine` | DONOR | P1 | Own donations |
| 9 | GET | `/donations/feed` | NGO | P1 | Live feed, most urgent first |
| 10 | GET | `/donations/{id}` | any* | P1 | One donation |
| 11 | GET | `/donations/{id}/history` | any* | P1 | Status timeline |
| 12 | PATCH | `/donations/{id}/cancel` | DONOR (owner), ADMIN | P2 | Cancel donation |
| 13 | POST | `/donations/{id}/claim` | NGO | P1 | Claim (first wins) |
| 14 | GET | `/claims/mine` | NGO | P1 | Own claims |
| 15 | PATCH | `/claims/{id}/cancel` | NGO (owner); ADMIN from P2 | P1 | Cancel claim |
| 16 | PATCH | `/claims/{id}/pickup` | NGO (owner) | P1 | Mark picked up |
| 17 | PATCH | `/claims/{id}/deliver` | NGO (owner) | P1 | Mark delivered |
| 18 | GET | `/dashboard` | any | P2 | Role-aware stats |
| 19 | GET | `/admin/stats` | ADMIN | P1 | Database-backed platform counts |
| 20 | GET | `/admin/users` | ADMIN | P1 | List registered users |
| 20a | PATCH | `/admin/users/{id}/toggle` | ADMIN | P1 | Activate / deactivate a non-admin user |
| 20b | POST | `/admin/users/admins` | ADMIN | P1 | Create another administrator |
| 21 | GET | `/admin/donations` | ADMIN | P1 | All donations |
| 21a | GET | `/admin/claims` | ADMIN | P1 | Claim lifecycle and status history |
| 22 | GET | `/notifications?page=0&size=20` | authenticated | P1 | Current user's notifications |
| 22a | GET | `/notifications/unread-count` | authenticated | P1 | Current user's unread count |
| 23 | PATCH | `/notifications/{id}/read` | authenticated | P1 | Mark own notification read |
| 23a | PATCH | `/notifications/read-all` | authenticated | P1 | Mark all own notifications read |
| 24 | POST | `/complaints` | any | P2 | File complaint |
| 25 | GET | `/admin/complaints` | ADMIN | P2 | List complaints |
| 26 | PATCH | `/admin/complaints/{id}/resolve` | ADMIN | P2 | Resolve complaint |
| 27 | PATCH | `/admin/claims/{id}/resolve` | ADMIN | P2 | Resolve a stuck picked-up claim |

\* "any" = any authenticated user; donors see only their own, NGOs see claimable donations they are eligible for (see §5.3) or ones they have claimed, admin sees all.

All paths in this document are relative to the base URL `/api` (so `/auth/login` is `/api/auth/login`).

---

## 3. Auth

### 3.1 Register: `POST /auth/register` (P1)

Request:
```json
{
  "name": "Hotel Sunrise",
  "email": "kitchen@sunrise.example",
  "password": "Str0ngPass!",
  "role": "DONOR",
  "phone": "9876543210",
  "address": "Club Road, Belagavi",
  "latitude": 15.8497,
  "longitude": 74.4977
}
```

For `role = NGO`, also send an `ngoProfile` block:
```json
{
  "ngoProfile": {
    "capacityMeals": 120,
    "serviceRadiusKm": 10,
    "acceptedCategories": ["COOKED_MEAL", "BAKERY", "PACKAGED"]
  }
}
```

Response `201`:
```json
{ "id": 12, "name": "Hotel Sunrise", "email": "kitchen@sunrise.example", "role": "DONOR" }
```

Rules: email unique (else `409`); password at least 8 characters; `role` may not be `ADMIN`; latitude in [-90, 90], longitude in [-180, 180]. `latitude` and `longitude` are **required for `role = NGO`** (the feed filters by distance from the NGO's location; missing → `400`). They are optional for donors; a donor without a saved location must send coordinates with each donation (§5.1). `ngoProfile` is required for NGOs.

### 3.2 Login: `POST /auth/login` (P1)

```json
{ "email": "kitchen@sunrise.example", "password": "Str0ngPass!" }
```
Response `200` (sets session cookie):
```json
{ "id": 12, "name": "Hotel Sunrise", "role": "DONOR" }
```
Wrong credentials or a deactivated account: `401`.

### 3.3 Logout / Me
- `POST /auth/logout` → `200 {}`
- `GET /auth/me` → `200 { "id": 12, "name": "...", "email": "...", "role": "DONOR", "latitude": 15.84, "longitude": 74.49 }`

---

## 4. NGO profile

### `GET /ngo/profile` (NGO, P1)
```json
{
  "capacityMeals": 120,
  "serviceRadiusKm": 10,
  "acceptedCategories": ["COOKED_MEAL", "BAKERY", "PACKAGED"],
  "totalClaims": 14,
  "completedClaims": 12,
  "reliabilityPercent": 85.7
}
```

`reliabilityPercent = 100 × completedClaims / totalClaims`, or `null` when `totalClaims` is 0. The counting rules (which claims count) are in `ALGORITHMS.md` §3.2. The counters are updated from Phase 2, so in Phase 1 they stay at 0 and `reliabilityPercent` is `null` (the example above shows Phase 2 values).

`capacityMeals` is the nominal maximum meals the NGO takes in a single pickup. It acts as an informational guideline (used to display warnings when a donation exceeds it and for match scoring), not a hard claim blocker. It is a fixed setting and is **not** reduced when claims are made.

### `PUT /ngo/profile` (NGO, P1)
Request body: `capacityMeals`, `serviceRadiusKm`, `acceptedCategories`. Response `200` with the updated profile.

---

## 5. Donations

### 5.1 Post food: `POST /donations` (DONOR, P1)

Request:
```json
{
  "foodName": "Veg biryani packets",
  "category": "COOKED_MEAL",
  "quantity": 50,
  "description": "Packed in boxes, still warm",
  "pickupAddress": "Club Road, Belagavi",
  "latitude": 15.8497,
  "longitude": 74.4977,
  "readyFrom": "2026-11-20T14:00:00Z",
  "safeUntil": "2026-11-20T18:00:00Z"
}
```

Validation:

| Field | Rule |
|---|---|
| `foodName` | required, 2-100 chars |
| `category` | required, valid enum |
| `quantity` | integer, 1-1000 |
| `pickupAddress` | required |
| `latitude`, `longitude` | Send both or neither. If omitted, the donor's saved location is used. If omitted **and** the donor has no saved location → `400` (`fieldErrors` on both fields). Ranges: latitude [-90, 90], longitude [-180, 180]. A donation is never stored without coordinates, so the response always contains them |
| `readyFrom` | optional; default = now |
| `safeUntil` | required; after `readyFrom` and at least 30 min from now |

Response `201`:
```json
{
  "id": 7,
  "foodName": "Veg biryani packets",
  "category": "COOKED_MEAL",
  "quantity": 50,
  "status": "AVAILABLE",
  "pickupAddress": "Club Road, Belagavi",
  "latitude": 15.8497,
  "longitude": 74.4977,
  "readyFrom": "2026-11-20T14:00:00Z",
  "safeUntil": "2026-11-20T18:00:00Z",
  "minutesLeft": 241,
  "createdAt": "2026-11-20T13:58:12Z"
}
```

### 5.2 My donations: `GET /donations/mine` (DONOR, P1)

Optional `?status=RESERVED`. Response `200`: array of the object above plus, when reserved, `claim`:
```json
[
  {
    "id": 7,
    "foodName": "Veg biryani packets",
    "quantity": 50,
    "status": "RESERVED",
    "safeUntil": "2026-11-20T18:00:00Z",
    "minutesLeft": 195,
    "claim": { "id": 31, "ngoName": "Annadana Trust", "claimedAt": "2026-11-20T14:45:00Z" }
  }
]
```

### 5.3 Live feed: `GET /donations/feed` (NGO, P1)

Returns **claimable** donations (`AVAILABLE` or `DISPATCHING`, not expired) **that the logged-in NGO is eligible for**, sorted by the 4-level feed priority:
1. Urgency score descending (scores equal after rounding to 1 decimal place are treated as tied)
2. Earlier `safeUntil` ascending (earliest expiry first)
3. Larger `quantity` descending (larger batch first)
4. Lower donation `id` ascending (stable, deterministic order)

Eligibility is always applied, using the NGO profile (`ALGORITHMS.md` §3.1):
- the donation's category is in `acceptedCategories`,
- distance from the NGO's location ≤ `serviceRadiusKm`,
- (P2) estimated travel time fits inside `minutesLeft - 10`.

`capacityMeals` is an informational guideline rather than a hard blocker: if `quantity > capacityMeals`, the item is still included with `"exceedsCapacity": true`, signaling the UI to display a warning (*"Exceeds registered capacity. Claim only if you can manage full pickup"*).

Donations no eligible NGO can take stay claimable until they expire. See `ALGORITHMS.md` §3.1, "No eligible NGO".

Query parameters (all optional; they narrow the eligible set further):

| Param | Example | Meaning |
|---|---|---|
| `category` | `COOKED_MEAL` | filter by category (must be one of the NGO's accepted categories to return results) |
| `minQuantity` | `20` | at least this many meals |
| `maxDistanceKm` | `8` | within this distance of the NGO's location; effective limit is `min(maxDistanceKm, serviceRadiusKm)` |
| `page`, `size` | `0`, `20` | pagination |

Response `200`:
```json
[
  {
    "id": 7,
    "foodName": "Veg biryani packets",
    "category": "COOKED_MEAL",
    "quantity": 50,
    "donorName": "Hotel Sunrise",
    "pickupAddress": "Club Road, Belagavi",
    "distanceKm": 2.4,
    "minutesLeft": 28,
    "safeUntil": "2026-11-20T18:00:00Z",
    "urgencyScore": 95.0,
    "exceedsCapacity": false,
    "status": "AVAILABLE"
  },
  {
    "id": 9,
    "foodName": "Bread loaves",
    "category": "BAKERY",
    "quantity": 30,
    "donorName": "City Bakery",
    "pickupAddress": "Tilakwadi, Belagavi",
    "distanceKm": 4.1,
    "minutesLeft": 310,
    "safeUntil": "2026-11-20T22:42:00Z",
    "urgencyScore": 30.1,
    "exceedsCapacity": true,
    "status": "AVAILABLE"
  }
]
```

`urgencyScore` is computed at request time (see `ALGORITHMS.md` §1). Both examples use the same "now" (`17:32Z`). For example, the bread item: timeScore = 100 × (360 − 310) / 330 = 15.15; urgency = 0.6 × 15.15 + 0.3 × 60 + 0.1 × 30 = **30.1**.

### 5.4 One donation / history

`GET /donations/{id}` → `200` donation object (404 if missing; 403 if not allowed to view).

`GET /donations/{id}/history` → `200`:
```json
[
  { "fromStatus": null,        "toStatus": "AVAILABLE", "changedBy": "Hotel Sunrise",  "note": "Donation posted", "changedAt": "2026-11-20T13:58:12Z" },
  { "fromStatus": "AVAILABLE", "toStatus": "RESERVED",  "changedBy": "Annadana Trust", "note": null,              "changedAt": "2026-11-20T14:45:00Z" },
  { "fromStatus": "RESERVED",  "toStatus": "PICKED_UP", "changedBy": "Annadana Trust", "note": null,              "changedAt": "2026-11-20T15:10:00Z" }
]
```
`changedBy` is `"System"` for automatic changes such as expiry.

### 5.5 Cancel donation: `PATCH /donations/{id}/cancel` (DONOR owner or ADMIN, P2)

Request (optional): `{ "reason": "Food was used internally" }`
Response `200`: updated donation (`status: "CANCELLED"`).
Errors: `400` if already `PICKED_UP`, `DELIVERED`, `EXPIRED` or `CANCELLED`; `403` if not the owner.
If a claim is active, it becomes `CANCELLED` with `cancel_reason` "Donation cancelled by donor" (or "by admin"). This does **not** count against the NGO's reliability. The NGO sees the change in `GET /claims/mine`; in Phase 2 it also gets a `DONATION_CANCELLED` notification.

---

## 6. Claims

### 6.1 Claim: `POST /donations/{id}/claim` (NGO, P1)

No body.

Response `201`:
```json
{
  "id": 31,
  "donationId": 7,
  "status": "ACTIVE",
  "claimedAt": "2026-11-20T14:45:00Z",
  "donation": { "id": 7, "status": "RESERVED", "pickupAddress": "Club Road, Belagavi", "safeUntil": "2026-11-20T18:00:00Z" }
}
```

Errors:

| Status | When |
|---|---|
| 409 | Donation is no longer claimable: another NGO already reserved it (optimistic lock or unique-key conflict), or it is not `AVAILABLE`/`DISPATCHING` (expired, cancelled, picked up, delivered), or `safeUntil` has passed |
| 400 | NGO is not eligible for this donation: category not accepted or outside service radius (P2: also cannot arrive in time). Exceeding capacity is allowed and does not return 400. See `ALGORITHMS.md` §3.1 |
| 403 | Not an NGO |
| 404 | Donation not found |

Checks run in this order: 404 → 409 → 400. An inactive NGO cannot log in. In Phase 1, an NGO set inactive while already logged in gets 400 (eligibility rule 1). From Phase 2, deactivation ends its session, so it gets 401 before any of these.

### 6.2 My claims: `GET /claims/mine` (NGO, P1)

Optional `?status=ACTIVE`. Response `200`: array of claim objects with embedded donation summary.

### 6.3 Cancel / pickup / deliver (NGO owner, P1; Admin cancel P2)

| Call | Who | Allowed when | Effect |
|---|---|---|---|
| `PATCH /claims/{id}/cancel` with optional `{ "reason": "Vehicle unavailable" }` | NGO (owner); ADMIN from P2 | claim `ACTIVE` | claim → `CANCELLED`, donation → `AVAILABLE` (P1) / `DISPATCHING` if dispatch rounds remain, else `AVAILABLE` (P2); from P2, counts as not completed for reliability |
| `PATCH /claims/{id}/pickup` | NGO (owner) | claim `ACTIVE` | claim → `PICKED_UP`, donation → `PICKED_UP` |
| `PATCH /claims/{id}/deliver` | NGO (owner) | claim `PICKED_UP` | claim → `COMPLETED`, donation → `DELIVERED`; from P2, NGO `completed_claims` +1 |

Response `200`: updated claim. Illegal order returns `400`; not the owner returns `403`. Admin calling pickup or deliver returns `403`. Admin closes stuck `PICKED_UP` claims with `PATCH /admin/claims/{id}/resolve` instead (§8).

The system closes claims without an API call in two cases: a donation that expires while reserved sets its claim to `EXPIRED`, and a donation that is cancelled sets its claim to `CANCELLED`. The full claim lifecycle is in `ARCHITECTURE.md` §4.

---

## 7. Dashboard

*Phase 2.* No dashboard exists in Phase 1.

### `GET /dashboard` (any authenticated, role-aware, P2)

**Admin (P2):**
```json
{
  "role": "ADMIN",
  "totalDonations": 120,
  "mealsPosted": 4800,
  "mealsRescued": 3600,
  "mealsExpired": 700,
  "rescueRate": 0.837,
  "activeDonors": 18,
  "activeNgos": 9,
  "co2EstimateKg": 9000,
  "co2IsEstimate": true,
  "donationsByDay": [ { "date": "2026-11-18", "count": 8 }, { "date": "2026-11-19", "count": 11 } ]
}
```

**Admin, with the complaints module (P9):** `openComplaints` (number of `OPEN` complaints) is added.

**Donor (P2):** `mealsDonated`, `mealsRescued`, `mealsExpired`, `rescueRate`, `recentDonations[]`.
**NGO (P2):** `mealsReceived`, `activeClaims`, `completionRatePercent` (same value as `reliabilityPercent`, §4), `recentClaims[]`.

`rescueRate = mealsRescued / (mealsRescued + mealsExpired)`, `0` if both are zero.

---

## 8. Admin

*Phase 2.* In Phase 1 the seeded Admin can log in, but none of the endpoints in this section exist yet; an inactive user (`active = false`) simply cannot log in.

| Endpoint | Details |
|---|---|
| `GET /admin/users?role=NGO&active=true` | List users (no password hashes ever returned) |
| `PATCH /admin/users/{id}/status` | Body `{ "active": false }` → `200` updated user. `400` if an admin tries to deactivate their own account |
| `GET /admin/donations?status=EXPIRED` | All donations with filters. Rows with a claim include `claim: { id, ngoName, status, claimedAt, pickedUpAt }`; use `?status=PICKED_UP` to find stuck claims |
| `PATCH /admin/claims/{id}/resolve` | Resolve a stuck `PICKED_UP` claim (below) |

Admin may also use `PATCH /donations/{id}/cancel` and `PATCH /claims/{id}/cancel` on any record. Admin cannot claim, pick up or deliver.

**Resolve a stuck picked-up claim:** `PATCH /admin/claims/{id}/resolve` (ADMIN, P2)

Use this when a claim stays `PICKED_UP` and the NGO never marks it delivered (for example, the NGO was deactivated). The Admin first checks with the NGO and donor outside the platform.

```json
{ "outcome": "DELIVERED", "reason": "NGO confirmed delivery by phone; donor confirmed" }
```

| `outcome` | Claim | Donation | Reliability |
|---|---|---|---|
| `DELIVERED` (delivery verified) | → `COMPLETED` | → `DELIVERED` | `completed_claims` +1 |
| `NOT_DELIVERED` (did not happen or cannot be verified) | → `CANCELLED` (`cancel_reason` = reason) | → `CANCELLED`, never reopened to other NGOs | counts as not completed |

`reason` is required (2-255 chars). The action is written to the donation's history with the Admin as `changedBy` and note "Admin resolution: <reason>". Response `200`: updated claim. Errors: `400` if `reason` is missing or the claim is not `PICKED_UP`; `404` if not found. Details are in `ARCHITECTURE.md` §4.

**Deactivation effects** (`{ "active": false }`, one transaction; full detail in `ARCHITECTURE.md` §7):
- The user can no longer log in (401), and **all their existing sessions end immediately**. Their next request returns 401.
- Donor: their `AVAILABLE` / `DISPATCHING` / `RESERVED` donations → `CANCELLED`. Any active claim on them → `CANCELLED`, not counted against the NGO.
- NGO: their `ACTIVE` claims → `CANCELLED`. The donations reopen to `AVAILABLE` (P1) / `DISPATCHING` if dispatch rounds remain, else `AVAILABLE` (P2).
- Anything already `PICKED_UP` is left unchanged; Admin closes it with `PATCH /admin/claims/{id}/resolve`.
- Reactivating (`{ "active": true }`) restores login only. Cancelled items stay cancelled.

---

## 9. Notifications

`GET /notifications?page=0&size=20` returns only the authenticated user's notifications, newest first. The server caps page size at 100.
```json
[
  {
    "id": 55,
    "type": "DONATION_NEW",
    "title": "New food donation",
    "message": "Fresh rice · 25 meals · Central Market · 120 minutes until safe-until",
    "donationId": 7,
    "read": false,
    "createdAt": "2026-11-20T14:31:00Z",
    "readAt": null
  }
]
```

Types: `DONATION_NEW`, `CLAIM_CREATED`, `CLAIM_CANCELLED`, `CLAIM_PICKED_UP`, `CLAIM_DELIVERED`, `EXPIRY_WARNING`. New donation notifications are sent once at creation to active NGOs matching the category and configured service radius; feed refreshes do not create notifications. Claim lifecycle notifications go only to the involved donor and NGO.

`GET /notifications/unread-count` returns a JSON number.

`PATCH /notifications/{id}/read` and `PATCH /notifications/read-all` return `204`. A user cannot read or modify another user's notifications.

## 9a. Initial and additional administrator setup

Set a long random `ADMIN_SETUP_KEY` in the server environment or ignored `.env` file. No default administrator credentials are seeded.

`GET /auth/admin-setup/status` → `{ "available": true }` only when the key is configured and the database has no ADMIN account.

`POST /auth/admin-setup` (public, one time):
```json
{
  "name": "Platform Administrator",
  "email": "admin@example.com",
  "password": "a-long-unique-password",
  "confirmPassword": "a-long-unique-password",
  "phone": "9876543210",
  "setupKey": "provided-out-of-band"
}
```

The server assigns the `ADMIN` role and hashes the password. The setup key is never returned. Once an admin exists, the endpoint responds with a conflict and setup status becomes unavailable.

An authenticated ADMIN can create further admins using `POST /admin/users/admins` with `{ "name", "email", "password", "phone" }`. Public `/auth/register` accepts only `DONOR` and `NGO`.

---

## 10. Complaints (P2)

`POST /complaints`
```json
{ "donationId": 7, "subject": "Food quality", "description": "Packets were cold on arrival." }
```
→ `201 { "id": 3, "status": "OPEN" }`

`GET /admin/complaints?status=OPEN` → list.
`PATCH /admin/complaints/{id}/resolve` with `{ "resolution": "Donor warned" }` → `200 { "status": "RESOLVED" }`.

---

## 11. Frontend page to API map

| Page | Role | APIs used |
|---|---|---|
| `register.html`, `login.html` | public | 1, 2 |
| `donor-dashboard.html` | DONOR | 4, 7, 8 (P1); 11, 12 (P2: timeline, cancel) |
| `ngo-feed.html` | NGO | 4, 5, 6, 9, 13 |
| `ngo-claims.html` | NGO | 14, 15, 16, 17 |
| `admin-dashboard.html` (P2) | ADMIN | 18, 19, 20, 21, 27 |
| `notifications` (P2) | all | 22, 23 |

## 12. Testing with Postman

- One collection with folders **Auth**, **Donor**, **NGO**, **Admin**.
- Use a Postman environment variable `baseUrl`; enable cookie jar so login persists.
- Include a "double claim" request pair for the demo of `409 Conflict`.
