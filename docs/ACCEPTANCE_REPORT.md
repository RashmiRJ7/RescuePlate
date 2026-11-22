# Phase 1 Acceptance Report & PRD §10 Verification

**Project:** RescuePlate (Time-Sensitive Surplus Food Rescue)  
**Evaluation Standard:** PRD.md §10 & BACKEND_ROADMAP.md Step 12  
**Date:** 2026-11-22  
**Test Suite Status:** 204 / 204 Passing Tests (0 Failures, 0 Errors)

---

## 1. Acceptance Checklist (PRD.md §10)

| Criteria | Status | Verification & Evidence |
|---|:---:|---|
| **1. Role Registration & Access Control:** Donor and NGO can register; all three roles (Admin seeded) can log in and reach only their own pages and APIs. | **PASSED** | Verified via `AuthControllerTest`, `SecurityConfigTest`, and `DataSeederTest`. Role enforcement prevents Donors from calling `/api/donations/feed` (403), prevents NGOs from posting donations (403), and rejects unauthenticated requests with 401. |
| **2. Donor Food Posting & Validation:** A donor can post a valid donation; invalid input shows clear field errors. | **PASSED** | Verified via `DonationControllerTest` and `DonationServiceTest`. Latitude/longitude fallback, coordinate pairing validation, and minimum 30-minute safeUntil validation properly return 400 Bad Request with field error maps. |
| **3. Urgency-Ranked Live Feed:** NGO feed is ordered by urgency with 4-level tie-breaking (urgency score ↓, safeUntil ↑, quantity ↓, donation id ↑); nearly-expired cooked meals appear above long-life packaged items. | **PASSED** | Verified via `UrgencyScoreCalculatorTest`, `DonationFeedComparatorTest`, and `FeedServiceTest`. Pure math urgency formula: `0.60×time + 0.30×perishability + 0.10×quantity`. Distance calculated using Haversine algorithm. |
| **4. Concurrency Protection (Double Claim):** Two NGOs claiming the same donation simultaneously: exactly one succeeds (201 Created), the other receives a conflict message (409 Conflict). | **PASSED** | Verified via `ClaimConcurrencyTest` (Task C5) repeated 20 times using a multi-threaded `CountDownLatch`. Guarded by dual layers: `@Version` on `donations` and `UNIQUE` constraint on `claims.active_donation_id`. |
| **5. Claim Cancellation & Feed Reopening:** Cancelling a claim returns the donation to the feed and allows another NGO to claim it. | **PASSED** | Verified via `ClaimLifecycleTest` and `ClaimServiceTest`. Cancellation sets `activeDonationId = null` (releasing the constraint) and transitions donation from `RESERVED` $\to$ `AVAILABLE`. |
| **6. Audit Status Timeline:** Status timeline records every state change with time and actor (`GET /donations/{id}/history`). | **PASSED** | Verified via `DonationServiceTest` and `DonationControllerTest`. All status changes route strictly through `DonationService.changeStatus`, logging immutable history records with `changedBy` (or `"System"` when automated). |
| **7. Automatic Overdue Expiry:** A donation past its safe-until time becomes **Expired** automatically within 1–2 minutes. | **PASSED** | Verified via `ExpiryMonitorTest` and `DonationServiceTest`. Scheduled job running every 60 seconds finds overdue donations, transitions them to `EXPIRED`, and cancels active reservations as `"System"`. Completely idempotent. |

---

## 2. Security & Layer Validation Summary

- **Authentication:** Spring Security session cookie (`JSESSIONID`), BCrypt password hashing.
- **Exception Handling:** Centralized `@RestControllerAdvice` mapping exceptions to standard HTTP error bodies:
  - 400: `ValidationException`, `InvalidStatusTransitionException`, `NgoNotEligibleException`
  - 401: `BadCredentialsException`, unauthenticated entry point
  - 403: `AccessDeniedException`, role mismatch
  - 404: `ResourceNotFoundException`, `NoResourceFoundException`
  - 409: `DonationNotClaimableException`, `DuplicateResourceException`, `OptimisticLockingFailureException`, `DataIntegrityViolationException`
- **Architectural Rules:** Zero instances of direct `donation.setStatus(...)` outside `DonationService.changeStatus`.
