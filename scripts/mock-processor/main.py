from __future__ import annotations

import asyncio
import json
import os
from xml.etree import ElementTree

from larex_actions import (
    ActionContext,
    ActionInput,
    EvaluationMetric,
    EvaluationReport,
    EvaluationSample,
    EvaluationTable,
    EvaluationTableColumn,
    EvaluationTableRow,
)
from larex_actions.fastapi import create_larex_action_app

PROCESSING_PROCESSOR_ID = os.getenv("LAREX_PROCESSOR_ID", "mock-image-copy")
TRAINING_PROCESSOR_ID = os.getenv("LAREX_TRAINING_PROCESSOR_ID", "mock-training")
OCR_EVALUATION_PROCESSOR_ID = os.getenv(
    "LAREX_OCR_EVALUATION_PROCESSOR_ID", "mock-ocr-evaluation"
)
LAYOUT_EVALUATION_PROCESSOR_ID = os.getenv(
    "LAREX_LAYOUT_EVALUATION_PROCESSOR_ID", "mock-layout-evaluation"
)
DISPATCH_SECRET_ENV = "LAREX_DISPATCH_HMAC_SECRET"
HEARTBEAT_COUNT = int(os.getenv("LAREX_HEARTBEAT_COUNT", "4"))
HEARTBEAT_DELAY_SECONDS = float(os.getenv("LAREX_HEARTBEAT_DELAY_SECONDS", "1"))
OUTPUT_IMAGE_VARIANT = os.getenv("LAREX_OUTPUT_IMAGE_VARIANT", "action-copy")
DEFAULT_TRAINING_DELAY_SECONDS = float(os.getenv("LAREX_TRAINING_DELAY_SECONDS", "5"))
MAX_TRAINING_DELAY_SECONDS = 30.0
DEFAULT_EVALUATION_DELAY_SECONDS = float(
    os.getenv("LAREX_EVALUATION_DELAY_SECONDS", "5")
)
MAX_EVALUATION_DELAY_SECONDS = 30.0


async def process_run(ctx: ActionContext) -> None:
    action_input = await ctx.pull_input()

    for index in range(HEARTBEAT_COUNT):
        progress = int(((index + 1) / (HEARTBEAT_COUNT + 1)) * 90)
        await ctx.heartbeat(
            progress,
            f"Mock processing heartbeat {index + 1}/{HEARTBEAT_COUNT}",
            raise_on_cancel=True,
        )
        await ctx.check_cancelled()
        await asyncio.sleep(HEARTBEAT_DELAY_SECONDS)

    image_count = 0
    xml_count = 0
    file_count = 0
    for page in action_input.pages:
        async with ctx.step(f"Copying {page.name}"):
            await ctx.check_cancelled()
            results = ctx.result_builder()
            if page.images:
                image = page.images[0]
                image_bytes = await ctx.download_bytes(image)
                results.add_image_bytes(
                    page_id=page.id,
                    content=image_bytes,
                    file_name=image.file_name or f"{page.name}-{OUTPUT_IMAGE_VARIANT}",
                    variant=OUTPUT_IMAGE_VARIANT,
                    mime_type=image.mime_type or "application/octet-stream",
                )
                image_count += 1

            if page.xml:
                xml = page.xml[0]
                xml_bytes = await ctx.download_bytes(xml)
                results.add_xml_bytes(
                    page_id=page.id,
                    content=xml_bytes,
                    file_name=xml.file_name or f"{page.name}.xml",
                )
                xml_count += 1

            if ctx.capabilities.custom_file_results:
                results.add_file_bytes(
                    content=json.dumps(
                        {"pageId": page.id, "pageName": page.name},
                        separators=(",", ":"),
                    ).encode("utf-8"),
                    file_name=f"{page.name}-metadata.json",
                    mime_type="application/json",
                    page_id=page.id,
                )
                file_count += 1

            await ctx.submit_page_results(page.id, results, f"Copied {page.name}")

    await ctx.complete(message=result_message(image_count, xml_count, file_count))


async def train_run(ctx: ActionContext) -> None:
    """Validate frozen training pairs and simulate a short, cancellable training run."""
    await ctx.heartbeat(2, "Pulling frozen training input", raise_on_cancel=True)
    action_input = await ctx.pull_input()
    _validate_training_input(
        action_input,
        expected_run_id=ctx.run_id,
        expected_dataset_id=ctx.payload.dataset_id,
        expected_item_ids=ctx.payload.dataset_item_ids,
    )

    page_count = len(action_input.pages)
    split_counts: dict[str, int] = {"TRAIN": 0, "VAL": 0, "TEST": 0}
    for index, page in enumerate(action_input.pages):
        await ctx.check_cancelled()
        image_bytes, xml_bytes = await asyncio.gather(
            ctx.download_bytes(page.images[0]),
            ctx.download_bytes(page.xml[0]),
        )
        if not image_bytes:
            raise ValueError(f"Training image for dataset item {page.id} is empty")
        if not xml_bytes:
            raise ValueError(f"PAGE XML for dataset item {page.id} is empty")
        try:
            ElementTree.fromstring(xml_bytes)
        except ElementTree.ParseError as exc:
            raise ValueError(f"PAGE XML for dataset item {page.id} is invalid") from exc
        assert page.split is not None
        split_counts[page.split] += 1
        progress = 5 + int(((index + 1) / page_count) * 40)
        await ctx.heartbeat(
            progress,
            f"Validated frozen pair {index + 1}/{page_count}",
            raise_on_cancel=True,
        )

    delay_seconds = _training_delay_seconds(action_input.parameters.get("delaySeconds"))
    ticks = max(1, int(delay_seconds + 0.999))
    sleep_seconds = delay_seconds / ticks
    for index in range(ticks):
        await ctx.heartbeat(
            45 + int(((index + 1) / ticks) * 50),
            f"Mock training {index + 1}/{ticks}",
            raise_on_cancel=True,
        )
        await ctx.check_cancelled()
        await asyncio.sleep(sleep_seconds)

    await ctx.complete(
        message=(
            f"Mock training validated {page_count} frozen pair(s) "
            f"(TRAIN={split_counts['TRAIN']}, VAL={split_counts['VAL']}) and completed."
        )
    )


async def evaluate_run(ctx: ActionContext, *, profile: str) -> None:
    """Validate frozen evaluation pairs and return a deterministic report fixture."""
    action_input = await ctx.pull_input()
    if action_input.kind != "EVALUATION" or not action_input.dataset_id:
        raise ValueError("Mock evaluation requires a dataset-scoped EVALUATION input")
    if not action_input.pages:
        raise ValueError("Mock evaluation requires at least one frozen dataset item")
    for index, page in enumerate(action_input.pages):
        image_bytes, xml_bytes = await asyncio.gather(
            ctx.download_bytes(page.images[0]), ctx.download_bytes(page.xml[0])
        )
        if not image_bytes or not xml_bytes:
            raise ValueError(f"Frozen evaluation pair for {page.id} is empty")
        try:
            ElementTree.fromstring(xml_bytes)
        except ElementTree.ParseError as exc:
            raise ValueError(f"PAGE XML for dataset item {page.id} is invalid") from exc
        await ctx.heartbeat(
            5 + int(((index + 1) / len(action_input.pages)) * 45),
            f"Validated evaluation pair {index + 1}/{len(action_input.pages)}",
            raise_on_cancel=True,
        )

    delay = _evaluation_delay_seconds(action_input.parameters.get("delaySeconds"))
    quality = _evaluation_quality(action_input.parameters.get("quality"))
    ticks = max(1, int(delay + 0.999))
    for index in range(ticks):
        await ctx.heartbeat(
            50 + int(((index + 1) / ticks) * 45),
            f"Mock evaluation {index + 1}/{ticks}",
            raise_on_cancel=True,
        )
        await ctx.check_cancelled()
        await asyncio.sleep(delay / ticks)

    if profile == "larex.ocr-recognition":
        report = _ocr_report(action_input, quality)
    else:
        report = _layout_report(action_input, quality)
    await ctx.heartbeat(98, "Publishing evaluation report", raise_on_cancel=True)
    await ctx.complete_evaluation(report, message="Mock evaluation completed")


def _evaluation_delay_seconds(raw_value: object) -> float:
    if raw_value is None:
        return DEFAULT_EVALUATION_DELAY_SECONDS
    if isinstance(raw_value, bool) or not isinstance(raw_value, (int, float)):
        raise TypeError("delaySeconds must be a number")
    value = float(raw_value)
    if value < 1 or value > MAX_EVALUATION_DELAY_SECONDS:
        raise ValueError(
            f"delaySeconds must be between 1 and {int(MAX_EVALUATION_DELAY_SECONDS)}"
        )
    return value


def _evaluation_quality(raw_value: object) -> float:
    if raw_value is None:
        return 0.95
    if isinstance(raw_value, bool) or not isinstance(raw_value, (int, float)):
        raise TypeError("quality must be a number")
    value = float(raw_value)
    if value < 0 or value > 1:
        raise ValueError("quality must be between 0 and 1")
    return value


def _ocr_report(action_input: ActionInput, quality: float) -> EvaluationReport:
    count = len(action_input.pages)
    characters = count * 100
    cer = round(1.0 - quality, 6)
    wer = round(min(1.0, cer * 1.4), 6)
    return EvaluationReport(
        profile="larex.ocr-recognition",
        profileVersion=1,
        title="Mock recognition evaluation",
        summary=[
            EvaluationMetric(
                key="cer",
                label="Character error rate",
                value=cer,
                format="PERCENT",
                direction="LOWER_IS_BETTER",
            ),
            EvaluationMetric(
                key="wer",
                label="Word error rate",
                value=wer,
                format="PERCENT",
                direction="LOWER_IS_BETTER",
            ),
            EvaluationMetric(
                key="characters",
                label="Characters",
                value=characters,
                format="INTEGER",
                direction="NEUTRAL",
            ),
        ],
        tables=[
            EvaluationTable(
                key="confusions",
                title="Character confusions",
                columns=[
                    EvaluationTableColumn(
                        key="reference", label="Reference", type="STRING"
                    ),
                    EvaluationTableColumn(
                        key="prediction", label="Prediction", type="STRING"
                    ),
                    EvaluationTableColumn(key="count", label="Count", type="INTEGER"),
                ],
                rows=[
                    EvaluationTableRow(
                        values={
                            "reference": "e",
                            "prediction": "c",
                            "count": int(cer * characters),
                        }
                    )
                ],
                totalRows=1,
            ),
            EvaluationTable(
                key="scripts",
                title="Script statistics",
                columns=[
                    EvaluationTableColumn(key="script", label="Script", type="STRING"),
                    EvaluationTableColumn(
                        key="accuracy", label="Accuracy", type="NUMBER"
                    ),
                ],
                rows=[
                    EvaluationTableRow(values={"script": "Latin", "accuracy": quality})
                ],
                totalRows=1,
            ),
        ],
        samples=[
            EvaluationSample(
                id=f"{page.id}:line-1",
                inputId=page.id,
                label=f"{page.name} / line 1",
                fields={
                    "reference": "mock ground truth",
                    "prediction": "mock prediction",
                },
            )
            for page in action_input.pages
        ],
        metadata={"engine": "mock", "model": "quality-fixture"},
    )


def _layout_report(action_input: ActionInput, quality: float) -> EvaluationReport:
    return EvaluationReport(
        profile="larex.layout-segmentation",
        profileVersion=1,
        title="Mock layout evaluation",
        summary=[
            EvaluationMetric(
                key="pixel_accuracy",
                label="Pixel accuracy",
                value=quality,
                format="PERCENT",
                direction="HIGHER_IS_BETTER",
            ),
            EvaluationMetric(
                key="mean_iou",
                label="Mean IoU",
                value=round(quality * 0.92, 6),
                format="PERCENT",
                direction="HIGHER_IS_BETTER",
            ),
        ],
        tables=[
            EvaluationTable(
                key="classes",
                title="Class IoU",
                columns=[
                    EvaluationTableColumn(key="class", label="Class", type="STRING"),
                    EvaluationTableColumn(key="iou", label="IoU", type="NUMBER"),
                ],
                rows=[EvaluationTableRow(values={"class": "text", "iou": quality})],
                totalRows=1,
            ),
            EvaluationTable(
                key="baselines",
                title="Baseline metrics",
                columns=[
                    EvaluationTableColumn(key="class", label="Class", type="STRING"),
                    EvaluationTableColumn(
                        key="precision", label="Precision", type="NUMBER"
                    ),
                    EvaluationTableColumn(key="recall", label="Recall", type="NUMBER"),
                    EvaluationTableColumn(key="f1", label="F1", type="NUMBER"),
                ],
                rows=[
                    EvaluationTableRow(
                        values={
                            "class": "baseline",
                            "precision": quality,
                            "recall": quality,
                            "f1": quality,
                        }
                    )
                ],
                totalRows=1,
            ),
        ],
        samples=[
            EvaluationSample(
                id=page.id, inputId=page.id, label=page.name, fields={"regions": 1}
            )
            for page in action_input.pages
        ],
        metadata={"engine": "mock", "model": "quality-fixture"},
    )


def _validate_training_input(
    action_input: ActionInput,
    *,
    expected_run_id: str,
    expected_dataset_id: str | None,
    expected_item_ids: list[str],
) -> None:
    if action_input.kind != "TRAINING":
        raise ValueError("Mock training requires a TRAINING Action input")
    if action_input.run_id != expected_run_id:
        raise ValueError("Pulled training input has an unexpected runId")
    if action_input.processor_key != TRAINING_PROCESSOR_ID:
        raise ValueError("Pulled training input has an unexpected processorKey")
    if action_input.dataset_id != expected_dataset_id:
        raise ValueError("Dispatch and pulled input datasetId values do not match")

    pages = action_input.pages
    if not pages:
        raise ValueError("Mock training requires at least one frozen dataset item")
    received_item_ids = [page.id for page in pages]
    if set(received_item_ids) != set(expected_item_ids):
        raise ValueError("Dispatch and pulled input dataset item IDs do not match")
    for page in pages:
        if page.split not in {"TRAIN", "VAL"}:
            raise ValueError(
                f"Dataset item {page.id} has unsupported split {page.split}"
            )


def _training_delay_seconds(raw_value: object) -> float:
    if raw_value is None:
        return DEFAULT_TRAINING_DELAY_SECONDS
    if isinstance(raw_value, bool) or not isinstance(raw_value, (int, float)):
        raise TypeError("delaySeconds must be a number")
    value = float(raw_value)
    if value < 1 or value > MAX_TRAINING_DELAY_SECONDS:
        raise ValueError(
            f"delaySeconds must be between 1 and {int(MAX_TRAINING_DELAY_SECONDS)}"
        )
    return value


def result_message(image_count: int, xml_count: int, file_count: int) -> str:
    return (
        f"Mock processor copied {image_count} image(s), {xml_count} XML file(s), "
        f"and created {file_count} custom file(s)."
    )


async def evaluate_ocr_run(ctx: ActionContext) -> None:
    await evaluate_run(ctx, profile="larex.ocr-recognition")


async def evaluate_layout_run(ctx: ActionContext) -> None:
    await evaluate_run(ctx, profile="larex.layout-segmentation")


app = create_larex_action_app(
    processor_id=PROCESSING_PROCESSOR_ID,
    dispatch_secret_env=DISPATCH_SECRET_ENV,
    handler=process_run,
    route_prefixes=("", "/processing"),
)
app.mount(
    "/training",
    create_larex_action_app(
        processor_id=TRAINING_PROCESSOR_ID,
        dispatch_secret_env=DISPATCH_SECRET_ENV,
        handler=train_run,
        route_prefixes=("",),
        processor_capabilities={
            "incrementalPageResults": False,
            "customFileResults": False,
        },
    ),
)
app.mount(
    "/evaluation/ocr",
    create_larex_action_app(
        processor_id=OCR_EVALUATION_PROCESSOR_ID,
        dispatch_secret_env=DISPATCH_SECRET_ENV,
        handler=evaluate_ocr_run,
        route_prefixes=("",),
        processor_capabilities={"evaluationReports": True},
    ),
)
app.mount(
    "/evaluation/layout",
    create_larex_action_app(
        processor_id=LAYOUT_EVALUATION_PROCESSOR_ID,
        dispatch_secret_env=DISPATCH_SECRET_ENV,
        handler=evaluate_layout_run,
        route_prefixes=("",),
        processor_capabilities={"evaluationReports": True},
    ),
)
