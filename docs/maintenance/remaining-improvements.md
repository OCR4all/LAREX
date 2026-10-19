# Remaining documentation improvements

Evaluation date: 18 October 2026, against the revised 58-page documentation set and current working source. This is a follow-up assessment, not another implementation pass.

The first pass repaired coverage and the broad reading sequence. The next investment should establish that readers can complete real tasks without filling in missing steps. Page-family coverage, valid links, and a successful site build do not establish that level of usability.

## Priorities

| Priority | Area | Remaining issue | Recommended outcome |
| --- | --- | --- | --- |
| High | First project and transcription review | Essential Add/Create GT and index-selection steps are absent. | A novice can start with either an image-only page or recognition-only XML, create editable ground truth, save it, and export the intended text. |
| High | Copyable setup/release procedures | Fresh-directory SDK instructions omit project initialization; processor registration omits workspace activation; release verification loses track of its working directory. | Copyable instructions that reach a successful test run from their stated prerequisites. |
| High | Training and evaluation | Reference contracts dominate; the user journey and decision-making remain underexplained. | A worked dataset-to-run-to-result guide, with blockers, required parameters, and model/result handoff. |
| High | Evidence for procedures | Site rendering was tested; application workflows were source-reviewed only. | Small downloadable fixtures and recorded execution of representative workflows, including failure/recovery paths. |
| Medium | Reference accuracy and export semantics | Baseline-comparison language overstates diagnostic comparison; text export's index default is easy to misunderstand. | Explicit current behavior and a worked example with deliberately different GT/recognition text. |
| Medium | Toolkit guides | Some pages still describe purpose without teaching the actual builder. | One complete label-set and virtual-keyboard example, including assignment and verification in the editor. |
| Medium | Navigation and page boundaries | Global previous/next still crosses audiences; several pages mix many tasks and duplicate rules. | Contextual reading paths with one primary task per procedural page and one authoritative home for changing behavior. |
| Medium | Release applicability and maintenance | Readers see no docs release/commit context; important verification remains a one-off report. | Visible applicability, versioned contracts, and repeatable example/browser checks in CI. |
| Lower | Mobile discovery and accessibility proof | Mobile contents excludes nested headings, and accessibility/search quality has only limited evidence. | Nested contents where useful, keyboard/screen-reader checks, and a small set of realistic search tasks. |

## Concrete findings

### 1. The beginner path still skips text-alternative creation

**Confirmed procedural omission.** [Your first project](../content/01.getting-started/02.first-project.md), lines 50–55, says to switch to List and enter text. [Text review](../content/02.user-guide/06.text-review.md), lines 24–28, similarly assumes editable GT already exists.

A new line is constructed with geometry but no text alternatives in [CreatePolygonCommand](../../frontend/app/commands/editor/create-polygon-command.ts), lines 62–75; [TextLine](../../frontend/app/models/editor/text.ts), line 62, leaves alternatives undefined when omitted. The list renders text inputs per existing alternative, and provides **Add** and **Create GT** controls in [line/item.vue](../../frontend/app/components/editor/text/line/item.vue), lines 270–271 and 1133–1154. Recognition is read-only and non-assigned alternatives are disabled at lines 1536–1537.

Explain: Add for an empty line; Create GT for recognition-only input; where indices are shown/changed; which alternative is editable; what to do when project GT is not index 0. Include the index-swap behavior rather than leaving users to infer it from a popover.

**Acceptance:** follow the tutorial with an image-only fixture and recognition-only PAGE XML, without editing raw XML or relying on undocumented controls. Reopen the saved page and verify its transcription.

### 2. Setup and release instructions still have copy/paste gaps

**Confirmed setup gaps.** [Processor SDK](../content/03.actions/05.processor-sdk.md), lines 117–124, starts with saving a file and `uv add`, without establishing a uv project. An isolated empty-directory reproduction of that add operation fails with “No pyproject.toml found.” State that an existing project is required or include initialization and a complete small project layout.

[Official Action Services](../content/04.deployment/06.official-action-services.md), lines 119–124, ends its registration sequence at enabled definition/global or workspace availability. [Admin Setup](../content/03.actions/03.admin-setup.md), lines 80–89, correctly explains that availability does not activate a processor: **Workspace Settings → Actions → Enable** is still required. The deployment procedure should include that step and finish with a small successful run.

**Confirmed command-sequence defect:** [CI/CD release verification](../content/04.deployment/04.cicd.md), lines 154–165, changes into the extracted bundle to verify `SHA256SUMS`, then references the ZIP and release JSON by bare filenames. Those assets live alongside the extracted directory, as established by [the bundle builder](../../scripts/build-deployment-package.sh), lines 33–35, and [manifest builder](../../scripts/build-release-manifest.sh), lines 46–49. Keep explicit download/bundle paths or isolate the directory change in a subshell. State how to obtain and verify the JSON manifest as well as the ZIP before using its image references.

**Acceptance:** a clean processor directory, a newly provisioned workspace, and a release download directory can follow their respective procedure without guessing the missing step. Do not treat a health response alone as proof of signed dispatch and callback success.

### 3. Training/evaluation need user procedures, separate from author contracts

**Confirmed coverage-depth gap.** [Training](../content/03.actions/09.training.md) is 66 lines, mostly definition YAML, snapshot lifecycle and model ownership. [Evaluation](../content/03.actions/10.evaluation.md) is 342 lines, largely wire fields, a JSON example and SDK submission. Their actual interfaces are [training/new.vue](../../frontend/app/pages/training/new.vue), [evaluation/new.vue](../../frontend/app/pages/evaluation/new.vue), and [evaluation results](../../frontend/app/pages/evaluation/[runId].vue).

Explain dataset/subset selection, eligible processor choice, required splits, image selection, parameter discovery, queued status, run details, and how to proceed from completion. For official Kraken training, include the required `modelName` from the [shipped definition](../../deployment/actions/kraken-layout-training.yaml), processor-specific input restrictions, and the handoff to a model usable by segmentation. Distinguish a generic Action contract from an individual processor's extra requirements.

For evaluation, show how to interpret metric direction and percentage-point deltas, inspect samples, decide whether a baseline is eligible, and handle file-output mode. Keep the report JSON/schema as a linked author reference.

**Acceptance:** readers can train/run a configured example and locate its actual model or result, and can explain what a displayed comparison does and does not establish.

### 4. Baseline comparison and text export need more precise promises

**Confirmed overstatement:** [Evaluation](../content/03.actions/10.evaluation.md), lines 329–335, says tables and samples remain side-by-side data. [The results page](../../frontend/app/pages/evaluation/[runId].vue), lines 326–354, provides a Baseline data card with sample counts and table titles/row counts for each report. The main searchable diagnostics are the current report; this is not a full side-by-side viewer of baseline table rows and sample contents. Document the actual summary boundary and how to inspect each full report.

**Confirmed missing decision guidance:** [Exports and sharing](../content/02.user-guide/10.exports-and-sharing.md), line 31, refers generically to index options. The [export dialog](../../frontend/app/components/project/slideover/export-target.vue), lines 134 and 148, defaults direct and embedded TXT indices to 0. [The dialog caller](../../frontend/app/composables/use-project-export-dialog.ts), lines 29–50, does not initialize that value from the project's GT preference. A project with another GT index needs an explicit export choice. Also explain the existing legacy PAGE-version warning and demonstrate a conversion's loss boundary rather than only saying that formats differ.

**Acceptance:** an example containing distinct text at indices 0 and 1 produces the explicitly chosen output. Baseline documentation distinguishes metric deltas, diagnostic summaries and full report inspection.

### 5. Provide fixtures and task-focused visuals

**Observed gap, not an assertion of a runtime defect.** None of the 58 content pages currently contains Markdown images or image components. The tutorial asks readers to supply their own image/XML. [The verification record](verification.md) explicitly excludes live editing, processing, training, IIIF and restore execution.

Add a small owned/synthetic sample pack: page image, image-only exercise, recognition-only XML, and an already reviewed example. Show expected artifacts, not only “inspect that it worked.” Add annotated images where spatial understanding matters: editor areas, drawing a line, Add/Create GT, variants, reading order, and result review. Keep essential instructions as text with accessible image descriptions.

Run a compact acceptance suite using those inputs: image → annotation → save/reopen → export; OCR → GT correction → correct-index export; version restore; dataset LINK/COPY → release; processor activation → run → result. For operators, include a clean install and an isolated restore rehearsal. The [restore guide](../content/04.deployment/01.production-deploy.md), lines 293–307, does not state the target database startup prerequisites: [the backup script](../../scripts/larex-instance-backup.sh), lines 594–606, requires application PostgreSQL and, for bundled authentication, Keycloak PostgreSQL to be running before preflight. Show the isolated target/volume setup, archived version, secrets and custom Compose configuration, separate model restore, and verification of a known saved page/dataset/user. Record which steps ran and which remain source-only.

### 6. Finish the toolkit procedures

**Confirmed editorial gaps.** [Virtual Keyboard](../content/02.user-guide/13.toolkit/04.virtual-keyboard.md), lines 24–37, says to add characters/groups without explaining the actual grid builder, key placement, key width, shifted characters, or preview. Those controls exist in [the builder](../../frontend/app/components/virtual-keyboard/builder/index.vue), [sidebar](../../frontend/app/components/virtual-keyboard/builder/sidebar.vue), and [key editor](../../frontend/app/components/virtual-keyboard/slideover/edit-key.vue).

[Label Sets](../content/02.user-guide/13.toolkit/03.label-sets.md), lines 26–33, says to add labels without an example mapping a display name to PAGE Region Type and Subtype. [The builder](../../frontend/app/components/label/builder/editor.vue), lines 88–139, exposes those choices and validation conditions.

**Acceptance:** build a tiny keyboard and a two-label set from scratch, assign each to a project, and verify inserted characters/exported PAGE types. Use a concrete example rather than more general advice.

### 7. Refine audience paths and remove redundant behavioral explanations

**Observed architecture issue.** [Page surroundings](../../docs/app/pages/[...slug].vue), lines 44–51 and 106–108, use global content order. In the rendered site, Core concepts has Installation as its next page; Start here then continues through repository structure/configuration before the user guide. That sequence suits an operator more than an annotator.

[Projects](../content/02.user-guide/03.projects.md) combines setup, defaults, page movement, jobs, four import paths, XML access, Actions, export and package internals. [Exports](../content/02.user-guide/10.exports-and-sharing.md) repeats package/history/PDF rules. Edit-access details are repeated in [Editor Interface](../content/02.user-guide/04.editor-interface.md) and [Collaboration/history](../content/02.user-guide/07.history-and-collaboration.md); running Actions has both a [user guide](../content/02.user-guide/12.larex-actions.md) and a substantially overlapping [reference](../content/03.actions/02.running-actions.md).

Provide explicit “continue as an annotator/curator/operator/developer” handoffs. Split long procedural pages by user outcome when that reduces searching. Preserve route compatibility with landing summaries or redirects. Put changing rules in one reference location and link to it from procedures; retain brief reminders where they help avoid mistakes.

**Acceptance:** a first-time annotator can finish the intended path without passing through infrastructure pages, and a factual change has a clearly identified authoritative page.

### 8. Make applicability and verification durable

**Observed maintenance gaps.** The current source [VERSION](../../VERSION) is `1.0.0-SNAPSHOT`, but [the docs shell](../../docs/app/components/DocsShell.vue) displays no release or commit context. Published compatibility currently depends on prose in individual pages and the installer artifact. Add a visible applicability label and maintain a compact LAREX/SDK/processor compatibility record, especially when protocol version remains 1 while capabilities change.

[CI](../../.github/workflows/ci.yml), lines 166–170, builds the docs. [The checker](../../docs/scripts/check-content.mjs), lines 85–108 and 120–134, checks local links but excludes remote URLs and code examples; it parses headings/links with regular expressions. Python/YAML example checks and the production browser/route sweep from the previous pass are recorded as one-off results rather than repeatable committed checks.

Promote the valuable checks into maintained commands: example syntax, selected semantic Action-definition validation, built-site routes/anchors, search-to-section navigation, mobile contents and core sample procedures. Prefer parser/rendered output over a second independently maintained Markdown approximation where practical. Audit representative external links with a separate bounded/manual or scheduled process; do not make every transient external failure block ordinary authoring.

A narrow lower-priority grammar inconsistency remains in [Training](../content/03.actions/09.training.md), line 41: “cannot declare outputs” is stricter than [the parser](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionDefinitionService.java), lines 457–469, which rejects enabled managed outputs. Omitting the block is safe guidance, but a schema reference should distinguish that recommendation from the exact accepted grammar.

**Acceptance:** CI catches a broken example or search/anchor regression introduced in a future edit, and a reader can tell whether the documentation applies to their deployment.

### 9. Improve small-screen discovery and accessibility evidence

**Confirmed limited contents behavior; broader usability remains unmeasured.** [The mobile contents](../../docs/app/pages/[...slug].vue), lines 91–99, iterates only top-level `tocLinks`, while desktop uses the tree. Nested sections such as evaluation field definitions therefore have weaker mobile discovery.

Add nested headings when they materially help, and test keyboard-only navigation, visible focus, dialog announcements, long table reading and zoom. Run realistic search tasks using reader language (for example “correct OCR”, “add transcription”, “processor missing”, “get trained model”) rather than proving one known phrase is indexed. These are evaluation targets, not claims that the current site fails each task.

## Suggested next sequence

1. Correct the concrete missing steps and overstated promises in findings 1, 2 and 4.
2. Build the sample-backed transcription tutorial and execute save/reopen/export checks.
3. Add the training/evaluation user guides and verify official processor prerequisites.
4. Add the toolkit examples and selective annotated visuals; refine the audience handoffs.
5. Turn the useful one-off checks into maintained regression coverage and show version applicability.

Success should be measured by independent readers completing representative tasks and recovering from expected errors, not by adding another target number of pages. No new user study or fresh authenticated application session was performed in this evaluation.
