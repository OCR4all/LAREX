# Contributing

## Development setup

1. Install prerequisites: Docker Desktop, Java 25, Node.js 22.18+, pnpm 11+, and [Task](https://taskfile.dev/).
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

Write commit subjects as [Conventional Commits](https://www.conventionalcommits.org/),
for example `feat(editor): add table exports` or `fix(actions): reject uploads to failed runs`.
Releases derive their version and changelog from these subjects: `feat` starts a minor
release, `fix` and `perf` start a patch release, and a `!` after the type or a
`BREAKING CHANGE:` footer starts a major release. See
[CI/CD](https://docs.larex.kallimachos.de/deployment/cicd#versioning) for details.
