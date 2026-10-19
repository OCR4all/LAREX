# Reference documentation audit

Audited 2026-10-18 against the local LAREX source and adjacent Action repositories.
Scope: Getting Started (except the introduction, owned by the main documentation
rewrite), Development, Deployment, Troubleshooting, and Actions. This file is
maintenance evidence rather than a user-facing reference page.

## Method and evidence standard

Existing documentation was compared with the configuration, service implementation,
UI proxy routes, build files, and SDK methods that own the described behavior.
Claims below are **verified in source**, not claims that a fresh production deployment
was exercised. Accurate existing schema, training/evaluation, result-import, security,
and release information was retained. Corrections prioritize lost data, impossible
instructions, incorrect contracts, and missing operational prerequisites.

## Verified errors and completed corrections

| Finding | Evidence | Completed correction |
| --- | --- | --- |
| Result Import documented 512 MiB per file and 2 GiB per request, disagreeing with the actual 100/480 MiB defaults. | [ActionProperties.Results](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/config/ActionProperties.java#L191-L217), [multipart configuration](../backend/src/main/resources/application.yaml#L13-L18) | Corrected the table, per-submission file count, request overhead, and Compose forwarding guidance. |
| The reference claimed every result validation error fails the entire run; page-local recoverable rejection and bulk partial success already exist. | [ActionRunService result processing](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionRunService.java#L1940-L2128), [report state/retry selection](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionRunReportService.java#L149-L203) | Explained recoverable page failures, fatal failures, retained imports, and report inspection. |
| Running Actions described retry as failed/cancelled only with the same complete page selection. | [retry guards and selected pages](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionRunService.java#L1121-L1158) | Added `COMPLETED_WITH_ISSUES`, failed/unfinished-only retry, warning-only exclusion, revalidation, and new-snapshot behavior for other kinds. |
| The SDK quickstart pinned 0.15 while later examples require 0.20. Integration/run-report pages still referred to SDK publication as future work. | [mock requirements](../scripts/mock-processor/requirements.txt), [SDK 0.20 source](https://github.com/OCR4all/larex-action-sdk/blob/f1dda1f8aff14e57ee96344c0c71c3f1f529056d/src/larex_actions/client.py), [SDK version metadata](https://github.com/OCR4all/larex-action-sdk/blob/f1dda1f8aff14e57ee96344c0c71c3f1f529056d/pyproject.toml) | Aligned installation/evaluation examples and compatibility guidance to the repository's 0.20 baseline; retained minimum-version history where useful. Registry availability was not independently checked. |
| Native setup instructed host processes to use Docker-only DNS and assumed infrastructure routes a native frontend automatically. | [backend defaults](../backend/src/main/resources/application.yaml#L19-L71), [Nuxt runtime defaults](../frontend/nuxt.config.ts#L31-L56), [Docker-discovered routing](../traefik/traefik.yml), [infrastructure task](../Taskfile.yml#L167-L170) | Added explicit host database/JWK/admin/mail/storage/bridge settings, native frontend `.env`, redirect/proxy choices, and host/container communication requirements. |
| Native debug examples used `-Pdebug`, but no build logic consumes that property. | [backend Taskfile](../backend/Taskfile.yml#L12-L15), [build tasks](../backend/build.gradle.kts) | Use Gradle's `bootRun --debug-jvm`; disclose the current shortcut discrepancy. Did not change runtime code or Taskfiles. |
| Docker development advertised a Swagger UI that has no implementation/dependency. | [backend dependencies](../backend/build.gradle.kts), [backend routes](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/controller) | Removed the unsupported route from Docker Development; main rewrite owns Introduction. |
| Troubleshooting treated Keycloak development as `:8090`, removed a nonexistent development `keycloak_data` volume, and checked health on the wrong application port. | [development services and volumes](../compose.yaml#L49-L90), [production Keycloak database/readiness](../compose.prod.auth.bundled-keycloak.yaml#L1-L115) | Distinguish development host routing, production upstream ports, internal management readiness, and PostgreSQL realm persistence. |
| Troubleshooting prescribed database-volume destruction first, deleting committed lockfiles, broad cache/volume pruning, and a fabricated Traefik ACME renewal flow. | [destructive development task](../Taskfile.yml#L162-L165), [Traefik HTTP configuration](../traefik/traefik.yml), [operator-supplied Nginx certificates](../compose.prod.nginx.yaml) | Replaced with symptom-driven diagnosis, preserved lockfiles and data, explicit disposable-reset scope, and actual certificate ownership. |
| Environment variables were presented as if adding them to `.env.prod` always configures the backend; Compose forwards only declared entries. | [production environment map](../compose.prod.base.yaml#L50-L126), [backend placeholders](../backend/src/main/resources/application.yaml) | Added configuration-layer explanation and concrete `compose.operator.yaml` example. Several limits remain backend settings requiring explicit forwarding; docs do not claim a runtime fix. |
| Action callback docs omitted `/api/v1`, public frontend proxy behavior, and the difference between explicit vs request-derived origin validation. | [base URL validation](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionPublicBaseUrlService.java#L27-L68), [dedicated callback proxy](../frontend/server/api/v1/public/actions/[...path].ts), [production empty allowlist](../compose.prod.base.yaml#L118-L123) | Added working internal/external callback bases, bearer/multipart routing, allowlist scope, and production derivation caveat. |
| Model backups were described only in terms of an old cache/state volume while training publishes into a separate shared models volume. | [processor mounts](../compose.actions.yaml#L70-L170), [snapshot contents](../scripts/larex-instance-backup.sh#L480-L495) | Added training service and `action_kraken_models`/training-state inventory, separate model backup responsibility, and restore rehearsal guidance. |
| Mock documentation described processing/training only although integration and OCR/layout evaluation handlers exist. | [mock mounted apps](../scripts/mock-processor/main.py#L498-L549) | Updated development/admin/examples catalogs and endpoint table; clarified evaluation outputs are fixtures. |
| Training/evaluation snippets appeared standalone but omitted required endpoint configuration. | [definition validation](../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionDefinitionService.java#L340-L480) | Explicitly label them as fragments and link to full definitions. |

## Verified coverage added

- **Operational limits:** quota defaults/enforcement/warnings, XML version retention,
  upload sessions/chunks, bounded PDF rasterization, server-import path/mount limits,
  and error-log retention. Source: [application configuration](../backend/src/main/resources/application.yaml#L87-L286).
- **Deployment topology:** training service, shared model volume, private readiness
  port, WebSocket/streaming proxy expectations, and process-local scaling constraints.
  Sources: [Compose services](../compose.actions.yaml), [frontend refresh map](../frontend/server/utils/auth.ts#L20),
  [collaboration maps](../frontend/server/utils/collaboration-state.ts#L57-L60), and
  existing [edit lease reference](./content/04.deployment/03.service-reference.md).
- **Realtime and public shares:** paired notification secrets, collaboration secret,
  and project/dataset/Action share URL bases. Sources: [production env wiring](../compose.prod.base.yaml#L126-L193)
  and [application URL defaults](../backend/src/main/resources/application.yaml#L234-L239).
- **Processor author journey:** install → save handler → run ASGI service → matching
  definition → endpoint test → workspace activation. Source: [SDK FastAPI adapter](https://github.com/OCR4all/larex-action-sdk/blob/f1dda1f8aff14e57ee96344c0c71c3f1f529056d/src/larex_actions/fastapi.py).
  Explained process-local background work, capacity, complete capability overrides,
  and that `ctx.step()` does not heartbeat continuously. Source: [SDK client helpers](https://github.com/OCR4all/larex-action-sdk/blob/f1dda1f8aff14e57ee96344c0c71c3f1f529056d/src/larex_actions/client.py).
- **PAGE XML text/NER exports:** added input/output contract, separate deployment,
  model discovery, deliberate invalid-page handling, and durable output behavior.
  Sources: [processor definition](https://github.com/OCR4all/larex-action-pagexml-ner/blob/main/action/pagexml-text-ner-export.yaml)
  and [processor implementation](https://github.com/OCR4all/larex-action-pagexml-ner/blob/main/src/larex_action_pagexml_ner/main.py),
  inspected locally. The implementation generates two files per successful page plus
  three project files, so the default 500-file callback ceiling permits 248 successful
  pages; this derived operational limit is stated explicitly.
- **Troubleshooting by symptom:** route/port, authentication, database, file storage,
  editor locks/realtime, Actions, builds, and TLS recovery paths. Source evidence is
  linked in the verified-error table above.

## Source inspection limitations and remaining uncertainty

- No fresh installation, live login, GPU/CPU model training, remote Action dispatch,
  backup restoration, or third-party IIIF request was executed as part of this
  documentation-only audit. Source validation is not an end-to-end runtime result.
- Adjacent SDK source reports 0.20.0. Adjacent Kraken source reports 0.5.1 while
  this repository pins image 0.6.0. The Compose image pin is the deployment source of
  truth; registry contents and the exact source/image correspondence remain unverified.
- The NER repository's README contains older SDK/default claims; the new section uses
  its actual Action YAML and Python implementation instead. No sibling repository
  was edited.
- Package/version release claims already present in CI/CD were checked against
  workflows where inspected; container signatures, registry releases, certificate
  state, and external Keycloak administration were not verified online.
- Native host configuration was derived from shipped settings and routing. The
  direct JVM debugger command is documented, but this audit did not launch a JVM.
  The development Dockerfile's Gradle JDWP invocation was not changed or proven to
  accept debugger connections; the port table now asks readers to verify listening logs.

## Validation performed

- `git diff --check`: passed after the reference changes.
- Python `ast.parse`: passed for all 19 Python examples in Actions pages.
- Bundled `yaml` parser: passed frontmatter for 29 pages in the audited sections
  and 30 YAML fenced examples. This checks syntax, not semantic Action validation.
- A site build, generated-route/link check, and visual review are integration checks
  owned by the main documentation rewrite; this audit does not claim they passed.
