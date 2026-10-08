# Install the LAREX Keycloak theme

Use the official Keycloak image. The LAREX theme is installed separately as a
read-only JAR; no custom Keycloak image or running download service is required.
The package provides the `keycloakify-larex` login and email themes and is tested
with Keycloak **26.7.4**. Other versions need compatibility testing before use.

## Obtain and verify

Both authentication modes of the docs wizard include `config/keycloak/theme.jar`,
its checksum, source metadata, and the verification script. The GitHub deployment
ZIP includes the same files. Alternatively, download just the theme from a stable
release, replacing `1.2.3` below with the intended LAREX version:

```bash
version=1.2.3
gh release download "v$version" --repo ocr4all/larex \
  --pattern "larex-keycloak-theme-$version.jar*"
sha256sum --check "larex-keycloak-theme-$version.jar.sha256"
gh attestation verify "larex-keycloak-theme-$version.jar" \
  --repo ocr4all/larex \
  --signer-workflow ocr4all/larex/.github/workflows/release.yml \
  --source-ref refs/heads/main
mkdir -p config/keycloak
cp "larex-keycloak-theme-$version.jar" config/keycloak/theme.jar
chmod 644 config/keycloak/theme.jar
```

For a deployment or wizard bundle, run from the extracted bundle root:

```bash
(cd config/keycloak && sha256sum --check theme.jar.sha256)
bash scripts/verify-keycloak-theme.sh
```

On macOS use `shasum -a 256 --check` instead of `sha256sum --check`.
Provenance verification requires a current [GitHub CLI](https://cli.github.com/)
and access to the GitHub attestations API. Verification must succeed before
installation. For an exact source pin, add `--source-digest <release-commit>` to
the `gh attestation verify` command, using the commit from the intended GitHub
release. Compare it with `config/keycloak/theme.json` when using a bundle.

Checksums detect corruption; they do not prove publisher identity. The wizard
checks downloaded bytes against its asset manifest, which comes from the same
docs host. The separate GitHub attestation verifies the JAR's release origin.
Development builds have no release attestation and must be rebuilt/reviewed
locally rather than treated as verified releases.

## Bundled Keycloak

The bundled Compose overlay already mounts `config/keycloak/theme.jar` read-only
and selects both themes through the bootstrap realm. Verify the artifact before
starting the stack. A missing JAR fails the bind mount instead of creating an
empty directory.

For an existing realm, choose `keycloakify-larex` under **Realm settings → Themes**
for **Login theme** and **Email theme**. Realm import does not update existing
realms. Leave the account and admin themes at their current settings.

## External Keycloak container

Copy the verified JAR onto the machine running your existing Keycloak container.
Add this mount to **that container's** Compose service; the LAREX external auth
overlay cannot install files into a separately managed Keycloak:

```yaml
services:
  keycloak: # Use the service name from your existing Keycloak Compose file.
    volumes:
      - type: bind
        source: ./config/keycloak/theme.jar
        target: /opt/keycloak/providers/larex_theme.jar
        read_only: true
        bind:
          create_host_path: false
          selinux: Z
```

The source path is relative to your Keycloak Compose project's base file.
Recreate the Keycloak service with your usual Compose files and environment:

```bash
docker compose up -d --force-recreate keycloak
```

With the official image's `start` command, Keycloak performs any required build
work at startup. An existing `start --optimized` deployment must incorporate the
JAR into its build step before optimized startup; simply adding a provider mount
may invalidate the existing build. Coordinate that with the Keycloak operator,
or use ordinary `start` with the appropriate build configuration. Do not discard
the institution's existing settings when changing startup mode.

Select `keycloakify-larex` for the realm's login and email themes. A client-specific
login-theme override takes precedence; update it too if applicable. For a
non-container installation, put the JAR in `$KEYCLOAK_HOME/providers` and restart
with the same build considerations.

## Upgrades and rollback

Verify the new release JAR, keep the previous version, replace the host file,
and recreate every Keycloak container using it. Recreating is necessary when a
single-file bind mount's host file is replaced. Test login, password reset,
verification email, and required actions before rolling out a Keycloak upgrade.
To roll back the theme, restore the previous JAR and recreate the containers.
To remove it, first select built-in realm/client themes, then remove the mount.

The archive audit excludes Java providers, but templates and login JavaScript
still execute and must be trusted. Provenance establishes origin, not absence of
vulnerabilities. Restrict host write access to trusted operators.

See [Keycloak themes](https://www.keycloak.org/ui-customization/themes),
[Keycloak containers](https://www.keycloak.org/server/containers), and
[GitHub attestation verification](https://cli.github.com/manual/gh_attestation_verify).
