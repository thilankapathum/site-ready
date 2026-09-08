# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

SiteReady (SSV Document Management System) — a role-based system for uploading, reviewing, and digitally
signing Single Site Verification (SSV) reports in a mobile telecom context.

| Layer       | Technology                             |
|-------------|-----------------------------------------|
| Frontend    | Angular 19 (standalone components), DaisyUI, Tailwind CSS |
| Backend     | Spring Boot 4.0, Java 21                |
| Database    | PostgreSQL 17, Liquibase migrations     |
| Storage     | MinIO (S3-compatible)                   |
| PDF Signing | iText 8 + BouncyCastle, PAdES-B-LT      |
| Auth        | JWT (JJWT 0.12), BCrypt                 |
| Proxy       | Nginx                                   |
| Containers  | Docker Compose                          |

Note: `ReadMe.md` describes an earlier version of the system (VENDOR/ENGINEER/ADMIN roles only). The
codebase has since grown a `MANAGER` role and manager dashboard/controller/service — trust the code over
the README when they disagree.

## Common commands

### Backend (`backend/`)
```bash
./mvnw spring-boot:run              # run locally (needs postgres+minio up)
./mvnw test                         # run all tests
./mvnw test -Dtest=ClassName        # run a single test class
./mvnw test -Dtest=ClassName#method # run a single test method
./mvnw clean package                # build jar
```

### Frontend (`frontend/site-ready/`)
```bash
npm install
npm start        # ng serve, proxies /api -> http://localhost:8080 (see proxy.conf.json)
npm run build
npm test         # karma/jasmine
```

### Full stack via Docker
```bash
./scripts/start.sh                       # first-time setup: .env, keystore, build, start, healthcheck
docker compose up -d postgres minio      # deps only, for local backend hot-reload
docker compose up -d --build api         # rebuild+restart one service
docker compose logs -f api
docker exec -it ssv-postgres psql -U ssvuser -d ssvdms
```

## Architecture

### Backend package layout (`backend/src/main/java/dev/thilanka/site_ready/`)
- `controller/` — one controller per domain: `AuthController`, `VendorController`, `EngineerController`,
  `ManagerController`, `AdminController`, `CompanyController`, `DocumentController`, `UserController`.
- `security/` — `JwtUtil` (issue/parse), `JwtAuthFilter` (per-request auth), `SecurityConfig`
  (role-based endpoint rules), `AuthService`.
- `service/` — business logic: `DocumentService` (upload/review/version workflow), `PdfStampService`
  (audit-trail page + PAdES signing), `PdfDiffService`, `StorageService` (MinIO), `AuditService`
  (writes `audit_log`), `ExportService` (Excel/CSV via Apache POI), `ReportAccessService` (role-based
  visibility rules), `ManagerService`, `CompanyService`.
- `entity/` + `entity/enums/` — JPA entities and enums (roles, report status, etc).
- `spec/` — JPA Specifications, used for the report search/filter endpoint.
- `dto/`, `exception/` (`GlobalExceptionHandler`), `config/` (`SecurityConfig` wiring, `MinioConfig`,
  `AppProperties` for `.env`-driven config).
- Liquibase changelogs live in `src/main/resources/db/changelog/changes/`; register new ones in
  `db.changelog-master.xml`.

### PDF hardening pipeline (core domain logic — read before touching upload/review code)
Every uploaded PDF (vendor or engineer) goes through two steps in `PdfStampService`:
1. An audit-trail page is appended (iText 8): site ID, project, version, uploader identity, UTC
   timestamp, SHA-256 of the original bytes, a QR code linking to the verify endpoint.
2. The whole document (including that page) is signed with the server's RSA-2048 key using
   PAdES-B-LT (`itext-core`/`sign`/`bouncy-castle-adapter`), embedding a verifiable signature.
Tamper checks are exposed publicly at `GET /api/reports/verify/{versionId}`, comparing against the
stored SHA-256/signature ID — don't bypass or weaken this when editing the upload/review flow.

### Report lifecycle (state machine to preserve when touching `DocumentService`/`ReportAccessService`)
```
Vendor uploads Vn -> PENDING_REVIEW (owned by ENGINEER)
  -> engineer decision: APPROVED / CONDITIONALLY_APPROVED / REJECTED (terminal-ish)
                      / RESUBMISSION_REQUIRED (owned by VENDOR, vendor uploads Vn+1, loop)
```
Version numbers are always system-assigned, never client-supplied. Filenames follow
`SITEID_PROJECT_Vn.pdf`; the frontend parses Site ID/Project from the filename on upload.

### Frontend layout (`frontend/site-ready/src/app/`)
- `pages/shell/` — `ShellComponent` (authenticated layout/sidebar) plus `user-actions/` (login,
  register, admin, profile — the unauthenticated/account-management screens).
- `pages/features/` — one folder per feature screen: `dashboard`, `manager-dashboard`, `upload`,
  `review-list`/`review-detail`, `report-detail`, `search`, `modals/`.
- `services/` — one Angular service per backend domain (`vendor`, `engineer`, `manager`, `admin`,
  `company`, `report`), plus `services/auth/` holding `authGuard`, `roleGuard`, and the JWT auth
  interceptor.
- `models/api.models.ts` — shared TypeScript interfaces mirroring backend DTOs.
- Route guarding pattern (`app.routes.ts`): every authenticated route sits under a parent gated by
  `authGuard`; per-route `data: { roles: [...] }` + `roleGuard` restricts by role (`ROLE_VENDOR`,
  `ROLE_ENGINEER`, `ROLE_MANAGER`, `ROLE_ADMIN`). Follow this pattern for new routes rather than
  checking roles inside components.

### Auth/account model
New registrations are inactive until an admin activates them and assigns a role
(`PATCH /api/admin/users/{id}/activate`, `/role`). Don't assume a freshly registered user can log in
and hit protected endpoints — activation is a separate admin step.

### Config
All runtime config comes from environment variables (see `.env.example`), consumed via
`AppProperties`/`application.yml` on the backend and Docker Compose service env on the frontend/nginx
sides. `docker-compose.yml` services: `nginx` (public entry), `frontend`, `api`, `postgres`, `minio`,
`pgadmin`. The MinIO console (9001) and pgadmin are internal/admin-only — don't expose them further.
