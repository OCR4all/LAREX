# LAREX Evaluation and Roadmap

## Overall Assessment

LAREX is a feature-rich release candidate, not an early MVP. Its annotation, workflow, processing, dataset, administration, and self-hosting capabilities are already unusually complete. The best next move is stabilization before adding more breadth.

The evaluated `next` branch was healthy:

- 438 backend tests with zero failures
- 368 frontend tests passing
- Frontend lint and type-check passing
- Clean working tree before this document was added
- Production Compose, migrations, dependency updates, image builds, quotas, backups, and extensive operator documentation already present

The application is still marked `1.0.0-SNAPSHOT`, which accurately reflects its maturity: functionally advanced, but with remaining operational and UX risks.

## Current Strengths

| Area | Assessment |
| --- | --- |
| Domain capability | Excellent: PAGE XML editing, text correction, geometry, reading order, relations, version comparison, validation, toolkits, datasets, releases, IIIF, and external Actions |
| Collaboration | Sensible single-writer leases with read-only presence and following; safer than premature multi-writer merging |
| Self-hosting | Strong Compose deployment, bundled or external Keycloak, secret bootstrapping, quotas, local documentation, and health checks |
| Extensibility | LAREX Actions provide a good self-hosted processor boundary without embedding every OCR engine in the core |
| Testing | Strong unit and integration coverage across frontend and backend |
| Portability | Structured project packages and numerous export formats reduce lock-in |
| Administration | More complete than most open-source annotation tools: users, workspaces, errors, storage, quotas, jobs, imports, and Actions |

## Recommended Roadmap

### P0 — Before a Stable 1.0

#### 1. Unified, Verifiable Backup and Restore

This is the biggest self-hosting gap. The built-in dump covers file data, while operators must separately preserve PostgreSQL, Keycloak, volumes, configuration, and secrets.

Add operator commands that:

- create a consistent PostgreSQL dump plus application files
- optionally include bundled Keycloak state
- record LAREX and database schema versions and checksums
- support restore preflight and compatibility checks
- verify archives without restoring
- run an automated restore drill in CI

#### 2. Browser-Level and Upgrade Testing

Current CI thoroughly tests modules and builds, but contains no real end-to-end workflow suite.

Add a small Playwright suite covering:

- sign-in
- project creation and upload
- opening, editing, saving, and versioning PAGE XML
- Action dispatch through the mock processor
- project and dataset release downloads
- backup and restore
- migration from the previous release

#### 3. Accessibility Pass and Automated Checks

The live walkthrough found anonymous buttons and tabs in the editor toolbar. Tooltips exist, but many icon-only buttons have no accessible name.

Improvements should include:

- reuse tooltip labels as `aria-label` values
- correct focus order and active-tool state announcements
- add automated axe checks to browser tests
- provide keyboard-accessible alternatives for important canvas selections
- test the core workflow with a screen reader

#### 4. Fix Generated-Avatar Hydration Mismatches

Normal navigation repeatedly produced Vue hydration warnings from generated avatar SVG IDs.

Make SVG IDs deterministic between server and client, or render generated avatars client-side.

#### 5. Release Supply-Chain Hardening

Add:

- SBOMs for released images
- container vulnerability scanning
- signed images and provenance attestations
- dependency-review checks
- `no-new-privileges` and capability dropping for core services where feasible

Action containers already demonstrate the desired runtime hardening.

### P1 — High-Value Product and Operator Improvements

#### 6. Review-Oriented Workflow Profiles

Pages currently have only `OPEN`, `IN_PROGRESS`, and `DONE`, with `DONE` acting as a lock.

Add narrowly scoped workspace workflow profiles rather than a generic workflow engine:

- Draft → In review → Approved
- optional distinct reviewer requirement
- validation rules that must pass before approval
- reopen reason and audit event
- bulk review queues

This should build on existing tasks, validation rules, roles, and page states.

#### 7. Full ALTO/METS Import Round-Trip

ALTO export exists, but ALTO import is currently a stub.

Implement:

- ALTO parsing
- compatibility with common ALTO versions
- METS-based image association
- import preview with warnings about lossy mappings
- round-trip tests for supported structures

This would materially improve interoperability with library and OCR ecosystems.

#### 8. Generic OIDC Mode

Authentication is currently tightly coupled to Keycloak claims and its admin client.

Keep bundled Keycloak as the easiest default, but add a generic OIDC and just-in-time provisioning mode with configurable:

- subject, email, and display-name claims
- group-to-global-role mappings
- workspace invitation matching
- disabled in-app user lifecycle controls when the identity provider is authoritative

#### 9. Operator Diagnostics and Optional Metrics

Production currently exposes only `health` and `info` through Actuator.

Add optional Prometheus-compatible metrics and a sanitized diagnostic bundle containing:

- application and schema version
- queue depths and oldest job age
- indexing lag
- database and storage health
- recent error summaries
- effective non-secret configuration

#### 10. Crash-Recovery Drafts

Preserve unsaved editor work locally, keyed by instance, workspace, page, and source XML version.

On return, offer:

- restore
- compare
- discard

This is safer than silent server autosave and works during a temporary self-hosted outage.

### P2 — Valuable, Larger Investments

#### 11. Pluggable Filesystem and S3-Compatible Storage

Files currently live in a single application data volume.

Add a storage abstraction supporting:

- local filesystem
- S3-compatible systems such as MinIO or Ceph
- migration between configured backends
- checksum verification
- storage connectivity and permission diagnostics

#### 12. Scoped Automation API and CLI

Stabilize a versioned subset of the existing API for:

- project import and export
- jobs
- datasets
- releases
- Actions

Add scoped service accounts or tokens rather than requiring operators to reuse a human bearer token.

#### 13. Internationalization

Most user-facing strings are embedded directly in Vue components.

Introduce message catalogs, beginning with English and German, and make translations community-contributable.

#### 14. Element-Anchored Review Discussions

Allow review threads attached to a PAGE region or textline, with:

- resolved and reopened states
- links into the editor
- mentions and notifications
- preservation or explicit warnings when element IDs change

Reuse the existing task and comment infrastructure rather than creating a separate social system.

#### 15. Dataset Provenance and Quality Reports

Enrich releases with:

- checksums
- validation summaries
- split statistics
- PAGE schema distribution
- processor and Action versions
- source-project provenance

Keep the report inside the downloadable release so it remains useful offline.

## Features to Deliberately Defer

- True simultaneous multi-writer PAGE editing: the current lease and follow model is safer and fits the data model.
- Kubernetes or complex high-availability orchestration before storage abstraction and measured demand.
- Centralized processor marketplaces, hosted analytics, billing, or mandatory telemetry.
- A built-in cloud AI assistant. New OCR and HTR capabilities belong in independently self-hostable LAREX Actions.
- Mobile annotation. Responsive review and task views may be useful, but the geometry editor should remain desktop-focused.

## Suggested Implementation Order

If only three initiatives fit the next milestone:

1. Unified backup and restore
2. End-to-end and upgrade testing
3. Accessibility and hydration cleanup

After those, configurable review gates and ALTO/METS import offer the strongest domain value.
