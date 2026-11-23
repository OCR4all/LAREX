# LAREX frontend

This is the LAREX web application, built with [Nuxt](https://nuxt.com) and [Nuxt UI](https://ui.nuxt.com). Its server routes form the BFF layer between the browser and the backend.

Run it as part of the [Docker development stack](../docs/content/05.development/02.docker-setup.md). To run it directly on the host, follow [Native Service Development](../docs/content/05.development/01.local-setup.md), which covers the required host networking.

```bash
pnpm install --frozen-lockfile
pnpm dev
```

## Checks

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

See [Testing](../docs/content/05.development/06.testing.md) for the Vitest projects, the Playwright journeys, and where a new test belongs.
