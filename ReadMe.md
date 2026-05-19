# SSV Document Management System

A secure, role-based document management system for reviewing and approving Single Site Verification (SSV) reports in a mobile telecom context.

---

## Architecture

| Layer       | Technology                           |
|-------------|--------------------------------------|
| Frontend    | Angular 19, DaisyUI, Tailwind CSS    |
| Backend     | Spring Boot 4.0, Java 21             |
| Database    | PostgreSQL 17 (Alpine)               |
| Storage     | MinIO (S3-compatible)                |
| PDF Signing | iText 8, PAdES-B-LT digital signature|
| Auth        | JWT (JJWT 0.12), BCrypt              |
| Migrations  | Liquibase                            |
| Proxy       | Nginx                                |
| Containers  | Docker Compose                       |

---

## First-Time Setup

### Prerequisites
- Docker Desktop (or Docker Engine + Compose plugin)
- JDK 21+ (for `keytool` — PDF signing keystore generation only)
- `openssl` (for JWT secret generation — usually pre-installed)

### Quick Start

```bash
# Clone the repo and enter the project root
cd ssv-dms

# Make scripts executable
chmod +x scripts/*.sh

# Run the setup + start script
./scripts/start.sh
```

The script will:
1. Check prerequisites
2. Create `.env` from `.env.example` with an auto-generated JWT secret
3. Generate the PDF signing keystore (`keystore/ssv-signing.p12`)
4. Build all Docker images
5. Start all services
6. Wait for health check

### Access Points

| Service      | URL                          |
|--------------|------------------------------|
| Web UI       | http://\<your-server-ip\>    |
| REST API     | http://\<your-server-ip\>/api |
| MinIO Console| http://\<your-server-ip\>:9001 |

---

## Default Credentials

> ⚠️ **Change these immediately after first login.**

| Role  | Email                  | Password   |
|-------|------------------------|------------|
| Admin | admin@ssv-dms.local    | Admin@123  |

The seeded password hash in `001-initial-schema.xml` corresponds to `Admin@123` (BCrypt, cost 12).

---

## User Roles & Workflow

### Roles

| Role     | Capabilities                                                |
|----------|-------------------------------------------------------------|
| VENDOR   | Upload SSV reports, assign engineer, view own reports       |
| ENGINEER | Download, review, and decision on assigned reports          |
| ADMIN    | All of the above + activate accounts, assign roles          |

### Account Lifecycle

1. User registers → account is **inactive** by default
2. Admin reviews user list → activates account and assigns role
3. User logs in with their credentials

### Report Lifecycle

```
Vendor uploads V1 PDF
        ↓
  [PENDING_REVIEW] ← responsibility: ENGINEER
        ↓
  Engineer downloads stamped PDF, marks issues, re-uploads
        ↓
    ┌───┴──────────────────────────────────────┐
    ↓                                          ↓
[APPROVED]                          [RESUBMISSION_REQUIRED]
[CONDITIONALLY_APPROVED]             → responsibility: VENDOR
[REJECTED]                           → Vendor uploads V2
(CLOSED)                                      ↓
                                       [PENDING_REVIEW] again
                                       (repeat until approved)
```

### Report Naming Convention

```
SITEID_PROJECT_Vn.pdf
KY0001_4G-upgrade-26_V1.pdf
```

The frontend parses Site ID and Project from the filename automatically.
The version number is **always system-assigned** and cannot be set manually.

---

## Anti-Forgery & Tamper Evidence

Every PDF upload (vendor or engineer) goes through a two-step hardening pipeline:

### Step 1 — Audit Trail Page (iText 8)

A system-generated page is appended to the PDF containing:
- Site ID, Project, Version
- Uploader name, company, role, email
- Upload timestamp (UTC ISO-8601)
- SHA-256 hash of the original uploaded bytes
- QR code linking to the verification endpoint
- Legal statement referencing this system

### Step 2 — PAdES-B-LT Digital Signature (iText 8 + BouncyCastle)

The complete document (including the audit page) is signed with the server's RSA-2048 private key using the PAdES (PDF Advanced Electronic Signatures) standard — the same standard used in EU legal documents and ISO 32000-2 compliant systems.

- The signature is embedded in the PDF — verifiable in Adobe Acrobat
- The signature ID is stored in the database
- The SHA-256 of the original bytes is stored for independent verification

### Verification

```
GET /api/reports/verify/{versionId}
```

Anyone with the QR code from a stamped PDF can verify authenticity against the server.

---

## API Reference (Key Endpoints)

### Auth
```
POST /api/auth/register          — Create account (inactive until admin approves)
POST /api/auth/login             — Get JWT token
POST /api/auth/change-password   — Change own password [auth]
GET  /api/auth/me                — Current user info [auth]
```

### Reports
```
POST /api/reports/upload                      — Upload PDF [VENDOR]
POST /api/reports/{id}/review                 — Submit review decision [ENGINEER]
GET  /api/reports/{id}/download/{version}     — Download stamped PDF [auth]
GET  /api/reports/my                          — My reports (paginated) [auth]
GET  /api/reports/search                      — Search with filters [auth]
GET  /api/reports/export/excel                — Export to .xlsx [auth]
GET  /api/reports/export/csv                  — Export to .csv [auth]
GET  /api/reports/verify/{versionId}          — Tamper check (public)
```

### Admin
```
GET   /api/admin/users                        — List all users [ADMIN]
PATCH /api/admin/users/{id}/role?role=        — Set role [ADMIN]
PATCH /api/admin/users/{id}/activate?active=  — Activate/deactivate [ADMIN]
```

### Users
```
GET /api/users/engineers          — Active engineers list [auth] (for upload form)
```

---

## Configuration

All configuration is via environment variables in `.env`. Key variables:

| Variable                   | Purpose                                    |
|----------------------------|--------------------------------------------|
| `DB_PASSWORD`              | PostgreSQL password                        |
| `MINIO_ACCESS_KEY`         | MinIO root username                        |
| `MINIO_SECRET_KEY`         | MinIO root password                        |
| `JWT_SECRET`               | JWT signing secret (min 32 chars, base64)  |
| `SIGNING_KEYSTORE_PASSWORD`| Password for PDF signing keystore          |
| `APP_BASE_URL`             | Base URL for QR code verification links    |

---

## Development Workflow

### Running backend only (hot reload)

```bash
# Start dependencies only
docker compose up -d postgres minio

# Run Spring Boot locally
cd backend
./mvnw spring-boot:run
```

### Running frontend only (dev server with proxy)

```bash
cd frontend
npm install
npm start
# Proxies /api → http://localhost:8080
```

### Useful Docker commands

```bash
# View logs
docker compose logs -f api
docker compose logs -f frontend

# Restart a single service
docker compose restart api

# Rebuild after code changes
docker compose up -d --build api

# Connect to Postgres
docker exec -it ssv-postgres psql -U ssvuser -d ssvdms

# Liquibase status
docker exec -it ssv-api java -jar app.jar --spring.liquibase.show-summary=VERBOSE
```

---

## Adding a New Liquibase Migration

Create a new file in `backend/src/main/resources/db/changelog/changes/`:

```xml
<!-- 002-add-notifications.xml -->
<databaseChangeLog ...>
    <changeSet id="002-add-notifications" author="your-name">
        <createTable tableName="notifications">
            ...
        </createTable>
    </changeSet>
</databaseChangeLog>
```

Then add it to `db.changelog-master.xml`:
```xml
<include file="classpath:db/changelog/changes/002-add-notifications.xml"/>
```

---

## Security Notes

- All accounts are **inactive until manually activated** by an admin — prevents unauthorized access
- Passwords are hashed with **BCrypt (cost 12)**
- JWTs expire after **24 hours** (configurable via `JWT_EXPIRY_MS`)
- The PDF signing keystore is **mounted read-only** into the API container
- MinIO is only accessible internally (no external port exposed except the management console on 9001)
- The management console on 9001 should be firewall-restricted to admin workstations in production

---

## Future Enhancements (Metrics & Monitoring)

The `audit_log` table already captures every action with timestamps and metadata. Future work:

- Dashboard metrics: average review turnaround time per engineer, rejection rate by vendor, backlog trends
- Email/webhook notifications when reports are assigned or decisions made
- Reminder scheduler for reports pending review beyond SLA threshold
- Grafana dashboard reading from `audit_log` via PostgreSQL datasource

---

## Project Structure

```
ssv-dms/
├── docker-compose.yml
├── .env.example
├── nginx/
│   └── nginx.conf
├── keystore/               ← generated by scripts/generate-keystore.sh
│   └── ssv-signing.p12
├── scripts/
│   ├── generate-keystore.sh
│   └── start.sh
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ssv/dms/
│       │   ├── config/         ← Security, MinIO, AppProperties
│       │   ├── controller/     ← Auth, Document, Admin, User
│       │   ├── dto/            ← Request/Response records
│       │   ├── entity/         ← JPA entities
│       │   ├── enums/          ← UserRole, ReportStatus, etc.
│       │   ├── exception/      ← GlobalExceptionHandler
│       │   ├── repository/     ← Spring Data JPA repos
│       │   ├── security/       ← JwtUtil, JwtAuthFilter
│       │   └── service/        ← Auth, Document, Storage, PdfStamp, Audit, Export
│       └── resources/
│           ├── application.yml
│           └── db/changelog/   ← Liquibase migrations
└── frontend/
    ├── Dockerfile
    ├── nginx-spa.conf
    ├── tailwind.config.js
    └── src/app/
        ├── core/
        │   ├── guards/         ← authGuard, roleGuard
        │   ├── interceptors/   ← authInterceptor (JWT)
        │   └── services/       ← AuthService, ReportService, AdminService
        ├── features/
        │   ├── auth/           ← login, register, profile
        │   ├── dashboard/
        │   ├── upload/         ← Vendor upload with drag-drop
        │   ├── review/         ← Engineer review list + detail
        │   ├── search/         ← Search, filter, export
        │   └── admin/          ← User management
        └── shared/
            ├── components/     ← ShellComponent (sidebar layout)
            └── models/         ← TypeScript interfaces, status maps
```