# Time-Sensitive Surplus Food Rescue

A food-donation platform that connects **donors with surplus food** (restaurants, hotels, hostels, event organizers) to **nearby NGOs** *before the food becomes unsafe to eat*.

> Advanced Java course project · Phase 1 (25 marks) · Phase 2 (25 marks) · Report (10 marks)
>
> **Status:** Phase 1 Backend Complete (`[core]` services, APIs, schedulers, and 204 passing unit/integration tests). See [`docs/TASKS.md`](docs/TASKS.md) and [`docs/BACKEND_ROADMAP.md`](docs/BACKEND_ROADMAP.md).

---

## The problem

Surplus cooked food is usually safe for only a few hours. Today it is thrown away because donors do not know which NGO can take it, and NGOs do not know it exists. The real problem is **time**: getting the right food to the right NGO before it expires.

## Our solution

```
Donor posts surplus food
   → system scores how urgent it is
   → best nearby NGOs are notified
   → first valid claim reserves it
   → NGO picks it up
   → status becomes Delivered
```

This is **not** a plain CRUD "add and view donations" site. The value is in automatic prioritization and smart matching.

## Key features

| Feature | What it does |
|---|---|
| **Donation Urgency Score** | Decides *which food should be rescued first* (time left, food type, quantity) |
| **NGO Match Score** | Decides *who is the best NGO for it* (distance, capacity fit, reliability) |
| **Smart Batch Dispatch + Live Claiming** | Notifies the top 3 NGOs at once, widens the search if nobody claims, first valid claim wins |
| **Safe claiming** | Only one NGO can reserve a donation (optimistic locking plus a database constraint) |
| **Automatic expiry** | A background job expires food once its safe-until time passes |
| **Impact Dashboard** | Meals rescued, rescued vs expired, one dashboard with a view per role |

## User roles

| Role | Can do |
|---|---|
| **Donor** | Register, post surplus food, track and cancel own donations |
| **NGO** | Browse the live feed, claim food, mark pickup and delivery |
| **Admin** | Manage users, donations and complaints; see platform stats |

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3, Spring Data JPA / Hibernate, Spring Security |
| Database | MySQL 8 |
| Frontend | HTML, CSS, JavaScript, Bootstrap 5 (served by Spring Boot, calls REST APIs) |
| Background jobs | Spring `@Scheduled`, `@Async` |
| Email | JavaMail (Phase 2) |
| Testing | JUnit 5, Mockito, Postman |
| Tools | Maven, Git, IntelliJ IDEA / Eclipse |

## Documentation

| File | Contents |
|---|---|
| [`docs/PRD.md`](docs/PRD.md) | Requirements, scope, user stories, phase split |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layers, package structure, data model (ERD, tables, indexes), state machine, concurrency |
| [`docs/ALGORITHMS.md`](docs/ALGORITHMS.md) | Urgency score, match score, batch dispatch, data structures |
| [`docs/API_DOCUMENTATION.md`](docs/API_DOCUMENTATION.md) | REST endpoints, request/response examples, error format |
| [`docs/TASKS.md`](docs/TASKS.md) | Task board, Definition of Done, Phase 1 / Phase 2 task breakdown |
| [`docs/BACKEND_ROADMAP.md`](docs/BACKEND_ROADMAP.md) | Step-by-step backend build guide, Learn-Build-Break-Explain cycle, viva prep |

## Getting started

**Prerequisites:** JDK 17+, MySQL 8, Git. Maven is optional: the included Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.9 automatically.

```bash
# 1. Clone
git clone <repo-url>
cd RescuePlate

# 2. Create the database
mysql -u root -p -e "CREATE DATABASE food_rescue CHARACTER SET utf8mb4;"

# 3. Configure credentials: copy the template and fill in your own values
cp .env.example .env          # Windows: copy .env.example .env
#    DB_URL=jdbc:mysql://localhost:3306/food_rescue?serverTimezone=UTC
#    DB_USERNAME=<your-user>
#    DB_PASSWORD=<your-password>

# 4. Run standard or with demo seed data (from repository root)
./mvnw spring-boot:run                                  # Standard run
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo  # Seeded demo data (1 admin, 3 donors, 3 NGOs, 10 donations)
# Windows:
# mvnw.cmd spring-boot:run
# mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo

# 5. Open
#    http://localhost:8080             landing page
#    http://localhost:8080/api/health  {"status":"UP",...}
```

Build and run all 204 automated tests:
```bash
./mvnw test               # Windows: mvnw.cmd test
```

A default **Admin** account is seeded on first start (`admin@rescueplate.com` / `Admin@12345`). When the `demo` profile is activated, realistic donor, NGO, and urgency-ranked donation records in Belagavi are automatically seeded.

> Never commit real passwords or API keys. Keep them in `.env` or environment variables (`.env` and `application-local.properties` are listed in `.gitignore`).

## Project structure

```
RescuePlate/
├── docs/                      PRD, architecture, algorithms, API, task board
├── src/main/java/com/foodrescue/
│   ├── FoodRescueApplication.java
│   ├── config/        security (placeholder until F4), scheduling, scoring properties
│   ├── controller/    REST controllers (currently: HealthController)
│   ├── dto/           request / response objects
│   ├── entity/        JPA entities
│   ├── enums/         Role, FoodCategory, DonationStatus, ClaimStatus
│   ├── exception/     custom exceptions + global handler
│   ├── repository/    Spring Data JPA repositories
│   ├── service/       business logic (Donation, Claim, Dispatch, ...)
│   ├── algorithm/     score calculators, distance util (plain Java)
│   └── scheduler/     expiry monitor, dispatch scheduler
├── src/main/resources/
│   ├── static/        HTML pages at the root; css/, js/, assets/, vendor/bootstrap/
│   └── application.properties
├── src/test/java/     JUnit tests
├── .mvn/wrapper/  mvnw  mvnw.cmd   Maven Wrapper
├── .env.example               configuration template (placeholders only)
├── pom.xml
└── README.md
```

Empty packages contain only a `package-info.java` describing what goes there; they are filled task by task.

## Roadmap

| Phase | Focus |
|---|---|
| **Phase 1** (4–5 days, ~30% vertical slice) | Donor/NGO registration and login (seeded Admin can log in), donation posting, eligibility-filtered urgency-sorted live feed, claim with duplicate prevention, NGO cancel/pickup/deliver, status history, expiry job |
| **Phase 2** | Donor cancel, status timeline UI, admin features (users and deactivation cascade, donations, claim resolve) and admin dashboard, reliability counters, NGO match score, batch dispatch, notifications (in-app + email), Donor/NGO dashboards, complaints, tests and polish |

## Team

| Member | Area |
|---|---|
| Rashmi | Backend core: entities, state machine, scoring, claim logic, scheduler, dispatch |
| [Name] | Backend support: security and roles, profile and donation APIs, notifications, admin stats, tests |
| [Name] | Frontend: Bootstrap pages, API integration, report, demo |

## Team rules

- One repo, **one branch per feature**, merge into `main` daily.
- Agree on entities and API shapes first; nobody changes them without telling the others.
- Everyone must understand the code they commit.
- See [`docs/TASKS.md`](docs/TASKS.md) for the task board and Definition of Done.
