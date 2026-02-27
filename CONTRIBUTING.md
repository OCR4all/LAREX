# Contributing

## Development setup

1. Install prerequisites: Docker Desktop, Java 21, Node.js 20+, pnpm 10+, and [Task](https://taskfile.dev/).
2. Install dependencies:
   - `task install`
3. Start local services:
   - `task docker:up`

## Build and test

Before opening a pull request, run:

- Backend tests: `task backend:test`
- Frontend checks: `task frontend:lint && task frontend:typecheck`
- Frontend build: `task frontend:build`

## Pull requests

- Keep changes scoped and explain user-visible impact.
- Include test coverage for behavioral changes.
- Update docs when behavior, API, configuration, or workflows change.

## Commit messages

Use clear, imperative commit messages that describe what changed and why.
