# Documentation verification

Verified on 18 October 2026 against the completed documentation change.

| Check | Result |
| --- | --- |
| Content validation (`pnpm --dir docs check`) | Passed: 58 Markdown pages and 505 internal source links; titles/descriptions, unique routes, heading anchors, local assets and Vue navigation targets. |
| URL compatibility against `git ls-files docs/content` baseline | All 47 existing published page routes retained; 11 new guides. Numeric source prefixes changed; published slugs did not. |
| Python fenced examples (`ast.parse`) | 19 Action examples passed syntax checks. |
| YAML fenced examples (`yaml.parse`) | 30 examples passed syntax checks. All content frontmatter is also parsed by the content checker/build. |
| Production build (`pnpm --dir docs build`) | Passed with Node 24.18.0 selected on PATH to match this checkout's SQLite native binding. Installer assets staged and content checks ran as prebuild gates. |
| Production server HTTP/SSR | All 58 routes returned 200, exactly one H1, and article content (or the landing page). |
| Rendered links and anchors | All 2,670 same-origin links across the rendered pages resolved to known routes and existing anchor IDs. |
| Missing route | Returned HTTP 404. |
| Desktop browser | Landing page, first-project tutorial and detailed YAML reference inspected. Sidebar order, active section, breadcrumbs, previous/next, code rendering and edit/report targets checked. No horizontal page overflow at 1440px. |
| Mobile browser | 390×844 emulation: dataset guide inspected, tables scroll within their container, no page overflow. Navigation opens and closes after link selection. Collapsible contents opens and all section targets exist. |
| Search | Explicit accessible dialog title/description, no duplicate H1 section records, no generated Shiki CSS in results. Searching “frozen copy” found the dataset section; selecting it closed search and opened the correct existing anchor. |
| Browser console | No warnings/errors on the checked dataset page. |
| Whitespace (`git diff --check`) | Passed. |

The production build emits dependency sourcemap and PURE-comment warnings; it completes successfully. The initial attempt used Node 26 with an existing Node 24 SQLite binary and failed its ABI check. Selecting the matching installed Node runtime resolved that environment mismatch without changing the application's runtime configuration.

Content validation is part of the regular build/CI path through `prebuild`. The HTTP/browser checks were performed against the local production server on port 3098. The checker's local links exclude remote URLs; no external link-availability claim is made.

The [feature/source map](feature-coverage.md) and [reference audit](../audit-reference.md) record the behavior checks. No fresh LAREX deployment, authenticated editing session, remote processor dispatch, training run, IIIF fetch, or backup restore was exercised. The docs site itself was built and interacted with; application procedures were validated against source rather than asserted as end-to-end tested.
