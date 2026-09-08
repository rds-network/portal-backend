# Volunteer Portal Backend

[![License: GNU GPL v3](https://img.shields.io/badge/License-GNU-yellow.svg)](https://opensource.org/license/gpl-3-0)
[![Security Checks](https://github.com/rds-network/portal-ui/actions/workflows/security-checks.yml/badge.svg)](https://github.com/russian-rs/portal-backend/actions/workflows/security-checks.yml)

A production-ready backend for volunteer management portals built with Kotlin and Spring Boot. Features include
volunteer application processing, user management with SSO integration, file storage, email notifications, and
reporting.

## Features

- **[Volunteer Application Processing](#volunteer-application-workflow)** - Multi-stage workflow for onboarding new
  volunteers
- **SSO Integration** - OAuth2/OIDC authentication via Authentik
- **[Weekly Reports & Task Tracking](#weekly-reports--task-tracking)** - Time tracking system with heatmap visualization
- **[Programs & Projects](#programs--projects)** - Organizational structure for volunteer activities
- **[External Service Integrations](#external-service-integrations)** - Unified role system synced across Authentik,
  WordPress, Outline, and Helpdesk
- **User Management** - Role-based access control (18 roles including Volunteer, Mentor, Admin)
- **File Storage** - S3-compatible storage (MinIO/AWS S3)
- **Email Notifications** - Template-based emails with outbox pattern for reliability
- **API-First Design** - OpenAPI 3.0 specification with generated interfaces

---

## Core Features

### Volunteer Application Workflow

A multi-stage workflow for processing volunteer applications from initial submission to account creation.
Application States include CREATED, IN_PROGRESS, CLARIFICATION, DOCS_SENT and more.

#### Application Types

- **NEW** - First-time volunteer applications
- **PROLONGATION** - Contract extension for existing volunteers

#### Workflow Features

- **Automatic Account Creation** - When application reaches `DONE` status, a user account is automatically created (or
  reactivated for prolongations)
- **Contract Management** - Contract dates and type are set before completion
- **Internal Notes** - Staff can add private notes for tracking decisions and required actions
- **Auto-Expiration** - Applications inactive for 1 month are automatically denied (except `PAUSED`)
- **Duplicate Detection** - Prevents duplicate applications by email/passport
- **Email Notifications** - Confirmation emails sent on submission

---

### Weekly Reports & Task Tracking

Volunteers submit weekly reports documenting their activities. Each report contains multiple tasks with time tracking.  
Reports needed to be reviewed by specific roles.

#### Report Structure

```
Report
├── Status: CREATED → ACCEPTED / REJECTED
├── Volunteer (Account)
├── Program & Project (copied from volunteer profile)
├── Tasks[]
│   ├── Date (work date)
│   ├── Name (task title)
│   ├── Description
│   ├── Time Spent (minutes)
│   ├── Result (optional)
│   ├── Customer (optional - work for another volunteer)
│   └── Files[] (attachments)
└── Notes[] (admin feedback)
```

#### HeatMap Visualization

The system provides a heatmap view of volunteer activity:

- **Hours Worked** - Aggregated from task `timeSpent` per week
- **Hours Required** - 10 hours/week for volunteers with `REGULAR` contracts
- **Deficit/Surplus** - Visual indication of under/over hours
- **Year View** - All weeks in a year displayed as a heatmap

#### Key Features

- **File Attachments** - Tasks can include supporting documents
- **Customer Assignment** - Tasks can be done on behalf of another volunteer
- **Soft Deletes** - Reports are archived, not permanently deleted
- **Statistics** - Aggregated by program, project, and volunteer demographics

---

### Programs & Projects

Hierarchical organizational structure for grouping volunteer activities.

#### Structure

```
Program (e.g., IT, MEDIA, DESIGN)
└── Projects (e.g., LAYOUT, FORMS, BOTS)
    └── Statistic Groups (for reporting categories)
```

#### Volunteer Assignment

- Each volunteer is assigned to **one program** and **one project**
- Project assignment automatically sets the parent program
- Changing program clears the project if it belongs to a different program
- Assignment is reflected in reports for tracking and statistics

#### Example Hierarchy

```
IT Program
├── LAYOUT (web design)
├── FORMS (form development)
├── SCRAPERS (data collection)
├── BOTS (automation)
└── APPS (applications)

MEDIA Program
├── ARTICLES (content writing)
├── SMM (social media)
├── VIDEO_EDITING
└── PHOTO
```

#### Multilingual Support

We have support 3 languages:

- Russian (`nameRu`)
- English (`nameEn`)
- Serbian (`nameSr`)

---

### External Service Integrations

The portal integrates with multiple external services using a **unified role system**. User accounts and roles are
synchronized automatically across all platforms.

#### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     AUTHENTIK SSO                                │
│              (Single Source of Truth)                            │
│         Users + Groups (OAuth2/OIDC)                            │
└─────────────────────┬───────────────────────────────────────────┘
                      │ Hourly Sync
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PORTAL BACKEND                                 │
│            UserSyncScheduler (hourly)                           │
│         UserGroup enum → oauthGroup mapping                     │
└──────┬──────────────┬──────────────┬──────────────┬─────────────┘
       │              │              │              │
       ▼              ▼              ▼              ▼
┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────┐
│ Outline  │  │  WordPress   │  │ Helpdesk │  │  Future  │
│ (KB)     │  │  (multiple)  │  │          │  │ Services │
└──────────┘  └──────────────┘  └──────────┘  └──────────┘
```

#### Unified Role System

All roles are defined once in the portal and automatically synced to external services.

#### Integrated Services

**Authentik SSO** (Identity Provider)

- OAuth2/OIDC authentication
- Single Sign-On across all services
- User and group management
- Password recovery link generation

**Outline** (Knowledge Base)

- User provisioning with role-based access
- Automatic group creation and membership sync
- User suspension on deactivation

**WordPress** (CMS - Multiple Instances)

- Multi-site support with independent sync
- Role mapping to WordPress roles
- Automatic token refresh (every 12 hours)
- User creation/update/deletion

#### Synchronization Features

- **Scheduled Sync** - Runs hourly to pull users from Authentik
- **Distributed Locking** - ShedLock prevents concurrent syncs
- **Error Isolation** - One service failure doesn't affect others
- **Auto-Deprovisioning** - Inactive users are suspended in all services
- **Event-Driven** - Account creation triggers welcome email with SSO recovery link

#### Extensibility

New services can be added by implementing the `AccountSynchronizer` interface:

```kotlin
interface AccountSynchronizer {
    fun sync(accounts: List<Account>)
    fun delete(account: Account)
}
```

---

## Tech Stack

- **Language:** Kotlin
- **Framework:** Spring Boot
- **Database:** PostgreSQL with Liquibase migrations
- **Cache:** Redis for sessions and caching
- **Storage:** S3-compatible (MinIO for development)
- **Build:** Gradle
- **API:** OpenAPI 3.0 with code generation

## Prerequisites

- JDK 21+
- Docker & Docker Compose
- Gradle 8.x (or use included wrapper)

## Quick Start

### 1. Start Dependencies

```bash
docker-compose up -d
```

This starts PostgreSQL, Redis, and MinIO.

### 2. Run Application

```bash
./gradlew bootRun --args='--spring.profiles.active=local,no-auth'
```

Or run `PortalBackendApplication.kt` from your IDE with profiles: `local,no-auth`

### 3. Access API

- API Base URL: `http://localhost:8081/`
- OpenAPI Spec: `api/openapi.yaml`

Import the OpenAPI spec into Postman or your preferred API client.

## Project Structure

```
portal-backend/
├── api/                          # OpenAPI specifications
│   ├── openapi.yaml             # Main API spec
│   ├── authentik-api/           # Authentik SSO client spec
│   ├── outline-api/             # Outline KB client spec
│   └── wordpress-api/           # WordPress client spec
├── service/                      # Main application module
│   └── src/main/kotlin/
│       └── rs/russian/portal/   # Domain-grouped code
└── *-api-generation/            # Generated API clients
```

## Configuration

### Environment Variables

| Variable               | Description                                                       | Required              |
|------------------------|-------------------------------------------------------------------|-----------------------|
| `DB_HOST`              | PostgreSQL host:port                                              | Yes                   |
| `DB_NAME`              | Database name                                                     | Yes                   |
| `DB_USER`              | Database username                                                 | Yes                   |
| `DB_PASS`              | Database password                                                 | Yes                   |
| `REDIS_HOST`           | Redis host                                                        | Yes                   |
| `REDIS_PORT`           | Redis port                                                        | Yes                   |
| `REDIS_PASS`           | Redis password                                                    | Yes                   |
| `S3_ENDPOINT`          | S3/MinIO endpoint                                                 | Yes                   |
| `S3_ACCESS_KEY`        | S3 access key                                                     | Yes                   |
| `S3_SECRET_KEY`        | S3 secret key                                                     | Yes                   |
| `S3_BUCKET`            | S3 bucket name                                                    | Yes                   |
| `AUTHENTIK_BASE_URL`   | Authentik SSO URL                                                 | For SSO               |
| `AUTHENTIK_API_KEY`    | Authentik API key                                                 | For SSO               |
| `CLANOVI_API_KEY`      | Permanent header key for the Clanovi office app (`X-Clanovi-Key`) | For `/api/clanovi/**` |
| `OAUTH2_CLIENT_ID`     | OAuth2 client ID                                                  | For SSO               |
| `OAUTH2_CLIENT_SECRET` | OAuth2 client secret                                              | For SSO               |
| `SMTP_HOST`            | SMTP server host                                                  | For email             |
| `SMTP_PORT`            | SMTP server port                                                  | For email             |
| `SMTP_USERNAME`        | SMTP username                                                     | For email             |
| `SMTP_PASSWORD`        | SMTP password                                                     | For email             |

### Spring Profiles

- `local` - Local development configuration
- `no-auth` - Disables authentication (for development)
- `test` - Testing with Testcontainers

## Development

### Build

```bash
./gradlew clean build        # Full build with tests
./gradlew build -x test      # Build without tests
./gradlew bootJar            # Build executable JAR
```

### Testing

```bash
./gradlew test                              # Run all tests
./gradlew test --tests "TestClassName"      # Run specific test
./gradlew :service:test                     # Run service module tests
```

For integration tests, use `docker-compose-test.yml` with profiles: `local`, `no-auth`, `test`

### API Development

The API follows an API-first approach:

1. Define endpoints in `api/openapi.yaml`
2. Generate interfaces: `./gradlew :server-api:openApiGenerate`
3. Implement generated interfaces in controllers

## Security

### Account deactivation

OIDC login verifies the current Authentik status before creating a portal session and does not reactivate disabled local
accounts. Every authenticated user request checks the local account status; remote status is cached for at most 60
seconds per backend instance. Deactivation directly in Authentik therefore blocks subsequent requests within that
window, without waiting for the hourly profile sync. Local deactivation blocks the next request and revokes the user's
portal sessions, including when applied by the sync scheduler.

Inactive, missing, or depersonalized accounts receive HTTP 401. If a required status lookup fails, the request receives
HTTP 503 without discarding the session or using an expired cached status. Explicit service-account JWTs retain their
existing authorization rules because they have no local volunteer account. The `no-auth` development profile bypasses
these checks.

### Pre-commit Checks

Enable automatic security checks:

```bash
git config core.hooksPath .git-hooks
```

Manual security scan:

```bash
./scripts/security-check.sh
```

Full scan with optional tools (requires `gitleaks`, `exiftool`):

```bash
export SKIP_OPTIONAL_TOOLS=false
./scripts/security-check.sh
```

**What's checked:** secrets, large files, certificates, backup files, gitleaks patterns

### OWASP Dependency Vulnerability Check

```bash
./gradlew dependencyCheckAnalyze
```

Report: `service/build/reports/dependency-check-report.html`

**NVD API Key** (optional, speeds up checks):

1. Get key: https://nvd.nist.gov/developers/request-an-api-key
2. Local: `export NVD_API_KEY=your-key`
3. GitHub Actions: Settings → Secrets → `NVD_API_KEY`

## Deployment

### Docker

```bash
./gradlew bootJar
docker build -t volunteer-portal-backend .
```

### Required Services

For production deployment, you'll need:

1. **PostgreSQL** - Primary database
2. **Redis** - Session storage and caching
3. **S3-compatible storage** - File storage (AWS S3, MinIO, etc.)
4. **Authentik** (optional) - SSO authentication
5. **SMTP server** (optional) - Email notifications
6. **Cloudflare Turnstile** (optional) - Captcha verification for public forms

## Customization

This project is designed to be forked and customized for your organization:

1. **Domain Configuration** - Update URLs in `application.yaml` and OpenAPI specs
2. **Seed Data** - Modify Liquibase migrations for your cities, programs, projects
3. **Email Templates** - Customize templates in `resources/templates/email/`
4. **Branding** - Update organization details in configuration

## License

This project is licensed under the GNU GPL v3 License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

Originally developed for [Ruska Dijaspora](https://russian.rs) volunteer portal.
