# LAREX deployment bundle

This directory is the supported, repository-free installation artifact for LAREX
administrators. It contains every supported production Compose fragment so the same
download can be used with bundled or external Keycloak, an existing reverse proxy or
the bundled Nginx proxy, self-hosted documentation, and the official Kraken Action
processor.

For a bundle containing only the selected files and a preconfigured environment
template, use the deployment wizard in the LAREX documentation.

## Verify the release artifacts

GitHub releases publish this ZIP together with a `.zip.sha256` checksum and an
immutable `larex-release-<version>.json` manifest. Verify the downloaded archive
before extracting it:

```bash
sha256sum --check larex-deployment-<version>.zip.sha256
# macOS:
shasum -a 256 --check larex-deployment-<version>.zip.sha256
```

After extraction, verify every file shipped inside the bundle:

```bash
cd larex-deployment-<version>
sha256sum --check SHA256SUMS
# macOS:
shasum -a 256 --check SHA256SUMS
```

The release manifest records the source commit and immutable backend, frontend,
and documentation image digests. Release images include SPDX SBOM and maximum-mode
provenance attestations and are signed keylessly with Sigstore by the release
workflow.

## Start the recommended deployment

Review `deployment/env/.env.prod.example`, then generate local secrets:

```bash
bash scripts/bootstrap-env-secrets.sh prod
```

Start LAREX with bundled Keycloak behind an existing reverse proxy:

```bash
docker compose --env-file .env.prod \
  -f compose.prod.base.yaml \
  -f compose.prod.auth.bundled-keycloak.yaml \
  -f compose.prod.publish.localhost.yaml \
  up -d --wait --wait-timeout 300
```

The frontend listens on `127.0.0.1:3000` and Keycloak on `127.0.0.1:8090` by
default. See the production deployment documentation for the other supported
combinations and operational procedures.

## Validate before starting

Replace `up ...` in the selected command with `config --quiet`. Pin
`LAREX_IMAGE_TAG` to a released version for production.

## Runtime containment

The shipped production definitions run the backend, frontend, and documentation
containers with read-only root filesystems and `no-new-privileges`. Frontend and
documentation containers drop every Linux capability. The backend keeps only the
small startup capability set required to prepare its persistent data directories
and then runs the Java process as its unprivileged application user. Writable
temporary space is provided through size-limited `/tmp` tmpfs mounts; the optional
documentation service rebuilds its Nuxt Content database there on startup.

## Secrets

The bundle never contains generated credentials. The bootstrap script creates
`.env.prod` and, when Kraken is selected, `.env.actions` locally. Keep those files
out of source control and in secret-grade storage.
