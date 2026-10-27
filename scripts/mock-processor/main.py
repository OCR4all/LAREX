from __future__ import annotations

import asyncio
import json
import math
import os
import random
from datetime import UTC, datetime
from io import BytesIO
from xml.etree import ElementTree

from PIL import Image

from larex_actions import (
    ActionContext,
    ActionInput,
    EvaluationMetric,
    EvaluationReport,
    EvaluationSample,
    EvaluationTable,
    EvaluationTableColumn,
    EvaluationTableRow,
    PageResultRejected,
    RunReportsUnsupported,
)
from larex_actions.fastapi import create_larex_action_app

PROCESSING_PROCESSOR_ID = os.getenv("LAREX_PROCESSOR_ID", "mock-image-copy")
INTEGRATION_PROCESSOR_ID = os.getenv("LAREX_INTEGRATION_PROCESSOR_ID", "mock-integration")
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

PAGE_NS = "http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15"
ElementTree.register_namespace("", PAGE_NS)


def integer_parameter(parameters: dict, key: str, default: int, minimum: int, maximum: int) -> int:
    value = parameters.get(key, default)
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or int(value) != value or not minimum <= value <= maximum:
        raise ValueError(f"{key} must be an integer between {minimum} and {maximum}")
    return int(value)


def binarize(content: bytes, threshold: int) -> bytes:
    with Image.open(BytesIO(content)) as image:
        binary = image.convert("L").point(lambda value: 255 if value >= threshold else 0, mode="1")
        output = BytesIO()
        binary.save(output, format="PNG")
        return output.getvalue()


def page_document(content: bytes | None, image: bytes | None = None, filename: str = "page.png") -> tuple[ElementTree.Element, ElementTree.Element, str]:
    if content:
        root = ElementTree.fromstring(content)
        namespace = root.tag.split("}")[0][1:] if root.tag.startswith("{") else ""
        prefix = f"{{{namespace}}}" if namespace else ""
        page = root.find(f"{prefix}Page")
        if page is None:
            raise ValueError("PAGE XML must contain a Page element")
        return root, page, prefix
    if not image:
        raise ValueError("An image is required to create PAGE XML")
    with Image.open(BytesIO(image)) as source:
        width, height = source.size
    prefix = f"{{{PAGE_NS}}}"
    root = ElementTree.Element(f"{prefix}PcGts")
    metadata = ElementTree.SubElement(root, f"{prefix}Metadata")
    for key, value in (("Creator", "LAREX mock processors"), ("Created", datetime.now(UTC).isoformat()), ("LastChange", datetime.now(UTC).isoformat())):
        ElementTree.SubElement(metadata, f"{prefix}{key}").text = value
    page = ElementTree.SubElement(root, f"{prefix}Page", imageFilename=filename, imageWidth=str(width), imageHeight=str(height))
    return root, page, prefix


def transform_xml(operation: str, content: bytes | None, parameters: dict, *, image: bytes | None = None, filename: str = "page.png") -> bytes:
    root, page, ns = page_document(content, image, filename)
    width, height = int(page.get("imageWidth", "0")), int(page.get("imageHeight", "0"))
    if width < 2 or height < 2:
        raise ValueError("PAGE XML must have image dimensions of at least 2 × 2")
    if operation == "region":
        for child in list(page):
            if child.tag.endswith("Region") or child.tag == f"{ns}ReadingOrder":
                page.remove(child)
        region = ElementTree.SubElement(page, f"{ns}TextRegion", id="mock_region", type="paragraph")
        ElementTree.SubElement(region, f"{ns}Coords", points=f"0,0 {width - 1},0 {width - 1},{height - 1} 0,{height - 1}")
    elif operation == "line":
        region = page.find(f"{ns}TextRegion")
        if region is None:
            raise ValueError("No text region found. Run Mock Whole-page Region first.")
        coords = region.find(f"{ns}Coords")
        if coords is None:
            raise ValueError("The text region has no coordinates")
        points = [tuple(map(int, point.split(","))) for point in coords.get("points", "").split()]
        if len(points) < 3:
            raise ValueError("The text region needs at least three coordinates")
        left, right = min(x for x, _ in points), max(x for x, _ in points)
        top, bottom = min(y for _, y in points), max(y for _, y in points)
        if right <= left or bottom <= top:
            raise ValueError("The text region must have a nonzero size")
        for child in list(region):
            if child.tag in (f"{ns}TextLine", f"{ns}TextEquiv"):
                region.remove(child)
        y = min(bottom - 1, top + (bottom - top) // 2)
        line_bottom = min(bottom, y + max(1, (bottom - top) // 20))
        ids = {element.get("id") for element in root.iter()}
        line_id = "mock_line"
        suffix = 1
        while line_id in ids:
            line_id = f"mock_line_{suffix}"
            suffix += 1
        line = ElementTree.SubElement(region, f"{ns}TextLine", id=line_id)
        ElementTree.SubElement(line, f"{ns}Coords", points=f"{left},{y} {right},{y} {right},{line_bottom} {left},{line_bottom}")
        ElementTree.SubElement(line, f"{ns}Baseline", points=f"{left},{line_bottom} {right},{line_bottom}")
    elif operation == "ocr":
        lines = page.findall(f".//{ns}TextLine")
        if not lines:
            raise ValueError("No text line found. Run Mock Single Text Line first.")
        count = integer_parameter(parameters, "wordCount", 8, 1, 100)
        rng = random.Random(integer_parameter(parameters, "seed", 42, 0, 2147483647))
        words = "archive page history manuscript letter reading example document ink paper book text".split()
        # Region-level transcriptions would otherwise disagree with the new line text.
        for region in page.iter():
            if region.tag.endswith("Region"):
                for child in list(region):
                    if child.tag == f"{ns}TextEquiv":
                        region.remove(child)
        for line in lines:
            for child in list(line):
                if child.tag in (f"{ns}TextEquiv", f"{ns}Word"):
                    line.remove(child)
            equiv = ElementTree.SubElement(line, f"{ns}TextEquiv", index="0", conf="0.5")
            ElementTree.SubElement(equiv, f"{ns}Unicode").text = " ".join(rng.choices(words, k=count)).capitalize() + "."
    else:
        raise ValueError(f"Unknown mock operation: {operation}")
    metadata = root.find(f"{ns}Metadata")
    if metadata is not None:
        changed = metadata.find(f"{ns}LastChange")
        if changed is not None:
            changed.text = datetime.now(UTC).isoformat()
    return ElementTree.tostring(root, encoding="utf-8", xml_declaration=True)


async def workflow_mock_run(ctx: ActionContext, operation: str) -> None:
    action_input = await ctx.pull_input()
    for index, page in enumerate(action_input.pages):
        await ctx.check_cancelled()
        if not page.images:
            raise ValueError(f"Mock {operation} requires a page image")
        selected_image = page.images[0]
        image = await ctx.download_bytes(selected_image)
        if not image:
            raise ValueError(f"Empty image input for {page.name}")
        results = ctx.result_builder()
        if operation == "binarize":
            threshold = integer_parameter(action_input.parameters, "threshold", 128, 0, 255)
            results.add_image_bytes(page_id=page.id, content=binarize(image, threshold), file_name=f"{page.id}-binarized.png", variant="binarized", mime_type="image/png")
        else:
            xml = await ctx.download_bytes(page.xml[0]) if page.xml else None
            content = transform_xml(operation, xml, action_input.parameters, image=image, filename=selected_image.file_name)
            results.add_xml_bytes(page_id=page.id, content=content, file_name=f"{page.id}.xml")
        image_type = getattr(selected_image, "image_type", None) or "type unknown"
        await ctx.submit_page_results(page.id, results, f"Mock {operation}: {page.name} · {selected_image.variant or selected_image.file_name} · {image_type}")
        await ctx.heartbeat(int(95 * (index + 1) / len(action_input.pages)), f"Processed {index + 1}/{len(action_input.pages)} pages", raise_on_cancel=True)
    await ctx.complete(message=f"Mock {operation} completed for {len(action_input.pages)} page(s).")


def workflow_mock_handler(operation: str):
    async def handler(ctx: ActionContext) -> None:
        await workflow_mock_run(ctx, operation)
    return handler


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
    failed_count = 0
    fail_every = action_input.parameters.get("failEveryNthPage", 0)
    if isinstance(fail_every, bool) or not isinstance(fail_every, (int, float)) or fail_every < 0 or int(fail_every) != fail_every:
        raise ValueError("failEveryNthPage must be a nonnegative integer")
    if fail_every and not action_input.capabilities.run_reports:
        raise RunReportsUnsupported("Simulated page failures require runReports support")
    for index, page in enumerate(action_input.pages, start=1):
        if fail_every and index % int(fail_every) == 0:
            await ctx.submit_page_results(
                page.id, page_status="FAILED", reason="Simulated recoverable page failure"
            )
            failed_count += 1
            continue
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

            try:
                await ctx.submit_page_results(page.id, results, f"Copied {page.name}")
            except PageResultRejected:
                failed_count += 1

    message = result_message(image_count, xml_count, file_count)
    if failed_count:
        message += f" {failed_count} page(s) failed."
    await ctx.complete(message=message)


async def integration_run(ctx: ActionContext) -> None:
    """Exercise external-work callbacks without producing LAREX artifacts."""
    action_input = await ctx.pull_input()
    if action_input.kind != "INTEGRATION":
        raise ValueError("Mock integration requires INTEGRATION input")
    delay = action_input.parameters.get("delaySeconds", 5)
    if isinstance(delay, bool) or not isinstance(delay, (int, float)):
        raise ValueError("delaySeconds must be a number")
    if not math.isfinite(delay) or not 0 <= delay <= 30:
        raise ValueError("delaySeconds must be between 0 and 30")
    fail = action_input.parameters.get("simulateFailure", False)
    if not isinstance(fail, bool):
        raise ValueError("simulateFailure must be a boolean")
    image_count = xml_count = 0
    for index, page in enumerate(action_input.pages):
        await ctx.check_cancelled()
        for file in page.images:
            if not await ctx.download_bytes(file):
                raise ValueError(f"Empty image input for {page.name}")
            image_count += 1
        for file in page.xml:
            if not await ctx.download_bytes(file):
                raise ValueError(f"Empty XML input for {page.name}")
            xml_count += 1
        await ctx.heartbeat(
            5 + int(45 * (index + 1) / len(action_input.pages)),
            f"Received {index + 1}/{len(action_input.pages)} frozen inputs",
            raise_on_cancel=True,
        )
    ticks = max(1, math.ceil(delay))
    for index in range(ticks):
        await ctx.check_cancelled()
        await ctx.heartbeat(
            50 + int(45 * (index + 1) / ticks), "Simulating external work", raise_on_cancel=True
        )
        await asyncio.sleep(delay / ticks)
    if fail:
        raise RuntimeError("Simulated integration failure")
    await ctx.complete(message=(
        f"Mock integration completed: {len(action_input.pages)} input(s), "
        f"{image_count} image(s), {xml_count} XML file(s)."
    ))


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
for operation, processor_id in (("binarize", "mock-binarization"), ("region", "mock-whole-page-region"), ("line", "mock-single-text-line"), ("ocr", "mock-random-ocr")):
    app.mount(f"/workflow/{operation}", create_larex_action_app(
        processor_id=processor_id,
        dispatch_secret_env=DISPATCH_SECRET_ENV,
        handler=workflow_mock_handler(operation),
        route_prefixes=("",),
        processor_capabilities={"customFileResults": False},
    ))
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

app.mount(
    "/integration",
    create_larex_action_app(
        processor_id=INTEGRATION_PROCESSOR_ID,
        dispatch_secret_env=DISPATCH_SECRET_ENV,
        handler=integration_run,
        route_prefixes=("",),
        processor_capabilities={"incrementalPageResults": False, "customFileResults": False},
    ),
)
