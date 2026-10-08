# Action annotation overwrite warnings

Investigated 2026-10-08 against the local LAREX, Python SDK, Kraken, and PAGE XML NER sources.

## Finding

A conservative warning is possible with existing metadata: XML-producing processing actions may replace existing annotations in their selected target scope. An accurate warning naming affected annotation layers requires additional YAML metadata. Static declarations need no SDK or dispatch/result protocol change; parameter-dependent effect discovery would require a new pre-run contract.

## Existing contract and behavior

- YAML declares output types and XML `enabled`/`mode`, but no annotation effects. Input requirements describe file availability, and targets describe selection scope. Unknown YAML fields are rejected. Sources: [definition document](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/dto/action/ActionDefinitionDocument.java), [YAML reference](../content/6.actions/4.yaml-reference.md).
- The run dialog already receives `outputsXml`, target selection, and per-page `xmlFileCount`. It currently warns about missing inputs and image variants, not annotation replacement. XML presence is only a proxy for nonempty annotations. Sources: [action types](../../frontend/app/types/action.ts), [run dialog](../../frontend/app/components/action/slideover/run.vue), [project action summaries](../../frontend/app/composables/use-project-actions.ts).
- PAGE results replace the page's existing XML and first create a version snapshot. REGION results replace selected regions' textlines and nested regions while retaining their own geometry, text variants, and other properties. TEXT_LINE results replace entire selected textlines. Sources: [XML result import](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionRunService.java), [scoped merge](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionResultPageMergeService.java). Scoped saves also create a snapshot through [annotation processing](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/annotation/application/AnnotationProcessingService.java).
- Caveat: XML `mode: append` is accepted by [definition validation](../../backend/src/main/java/de/uniwue/zpd/dachs/larex/backend/service/action/ActionDefinitionService.java), but `storeXmlResult` and `storeScopedXmlResult` do not consult it. Existing XML is still replaced/merged. Do not treat this flag as a preservation guarantee; the [result-import documentation](../content/6.actions/6.result-import.md) currently suggests more than the implementation provides.
- SDK capabilities describe transport, file outputs, parameter discovery, and evaluation, with no mutation contract. Result messages are post-run, so cannot drive a warning before execution. Source: [SDK models](/Users/maximiliannoth/PycharmProjects/larex-action-sdk/src/larex_actions/models.py:47).
- Kraken declares PAGE/REGION targets and XML upsert. PAGE segmentation generates fresh XML; REGION segmentation removes existing textlines and inserts new ones, also removing old line baselines/text. Sources: [Kraken YAML](/Users/maximiliannoth/PycharmProjects/larex-action-kraken/action/kraken-segmentation.yaml:6), [PAGE processing](/Users/maximiliannoth/PycharmProjects/larex-action-kraken/src/larex_action_kraken/main.py:189), [REGION processing](/Users/maximiliannoth/PycharmProjects/larex-action-kraken/src/larex_action_kraken/main.py:469).
- NER export declares XML/images disabled and custom files enabled, so no annotation replacement warning is needed. Source: [NER YAML](/Users/maximiliannoth/PycharmProjects/larex-action-pagexml-ner/action/pagexml-text-ner-export.yaml:31).

## Recommended implementation direction

1. Add an initial warning for XML output on eligible pages with existing XML. Say annotations **may** be replaced, and identify PAGE, REGION, or TEXT_LINE scope. This needs only LAREX changes. Retry should also present the warning; reliable page counts should use the actual eligible run scope.
2. For precise warnings, introduce an optional, validated declaration of annotation layers that may be replaced/deleted, preferably per target. Define whether replacing a parent includes descendant annotations, and include text at different hierarchy levels. Expose it through definition DTOs and frontend types. Missing declarations should fall back to the generic warning, rather than mean safe.
3. Determine which declared affected layers are actually present in the selected scope. XML file counts alone cannot establish regions, lines, baselines, or text; use annotation summaries or a server-side run-impact preview for that precision.
4. Static YAML effects describe potential changes; they do not enforce preservation. If guarantees are needed, import validation/merge logic must enforce them. If effects vary with parameters, start conservatively with the union of potential effects; add processor-side pre-run discovery only if that precision is required. The current authenticated preflight checks identity/protocol/capabilities for endpoint testing, not run impact.

No runtime code was changed or tests run; these findings come from source inspection.

## Follow-up after committing the overwrite-warning implementation

Remove the unsupported `mode: append` option for XML and image outputs. Update YAML validation, documentation, examples, and tests together; choose a compatibility policy for existing definitions using `append` before removing support. Keep this cleanup separate from the current overwrite-warning commit.
