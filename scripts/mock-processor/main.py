from __future__ import annotations

import asyncio
import json
import logging
import os
from collections.abc import Mapping
from typing import Any, Literal
from xml.etree import ElementTree

import httpx
from fastapi import BackgroundTasks, HTTPException, Request
from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    SecretStr,
    ValidationError,
    model_validator,
)
from larex_actions import (
    ActionCancelled,
    ActionClient,
    ActionContext,
    ActionFile,
    DispatchVerificationError,
)
try:
    from larex_actions import (
        ActionInput,
        EvaluationMetric,
        EvaluationReport,
        EvaluationSample,
        EvaluationTable,
        EvaluationTableColumn,
        EvaluationTableRow,
    )
    HAS_EVALUATION_SDK = True
except ImportError:  # pragma: no cover - exercised by the released pre-evaluation SDK image
    HAS_EVALUATION_SDK = False

if not HAS_EVALUATION_SDK:  # pragma: no cover - compatibility path only
    class EvaluationMetric(BaseModel):
        model_config = ConfigDict(extra="ignore", populate_by_name=True)
        key: str
        label: str
        value: int | float
        format: str = "NUMBER"
        unit: str | None = None
        direction: str = "NEUTRAL"

    class EvaluationTableColumn(BaseModel):
        model_config = ConfigDict(extra="ignore", populate_by_name=True)
        key: str
        label: str
        type: str = "STRING"

    class EvaluationTableRow(BaseModel):
        model_config = ConfigDict(extra="ignore", populate_by_name=True)
        values: dict[str, Any] = Field(default_factory=dict)

    class EvaluationTable(BaseModel):
        model_config = ConfigDict(extra="ignore", populate_by_name=True)
        key: str
        title: str
        columns: list[EvaluationTableColumn] = Field(default_factory=list)
        rows: list[EvaluationTableRow] = Field(default_factory=list)
        truncated: bool = False
        total_rows: int = Field(default=0, alias="totalRows")

    class EvaluationSample(BaseModel):
        model_config = ConfigDict(extra="ignore", populate_by_name=True)
        id: str
        input_id: str = Field(alias="inputId")
        target_id: str | None = Field(default=None, alias="targetId")
        label: str | None = None
        status: str = "OK"
        fields: dict[str, Any] = Field(default_factory=dict)
        metrics: list[EvaluationMetric] = Field(default_factory=list)

    class EvaluationReport(BaseModel):
        model_config = ConfigDict(extra="ignore", populate_by_name=True)
        schema_version: int = Field(default=1, alias="schemaVersion")
        profile: str
        profile_version: int = Field(alias="profileVersion")
        title: str
        summary: list[EvaluationMetric] = Field(default_factory=list)
        tables: list[EvaluationTable] = Field(default_factory=list)
        samples: list[EvaluationSample] = Field(default_factory=list)
        warnings: list[str] = Field(default_factory=list)
        metadata: dict[str, Any] = Field(default_factory=dict)
from larex_actions.fastapi import create_larex_action_app
from larex_actions.verifier import DispatchVerifier

PROCESSING_PROCESSOR_ID = os.getenv("LAREX_PROCESSOR_ID", "mock-image-copy")
TRAINING_PROCESSOR_ID = os.getenv("LAREX_TRAINING_PROCESSOR_ID", "mock-training")
OCR_EVALUATION_PROCESSOR_ID = os.getenv("LAREX_OCR_EVALUATION_PROCESSOR_ID", "mock-ocr-evaluation")
LAYOUT_EVALUATION_PROCESSOR_ID = os.getenv("LAREX_LAYOUT_EVALUATION_PROCESSOR_ID", "mock-layout-evaluation")
DISPATCH_SECRET_ENV = "LAREX_DISPATCH_HMAC_SECRET"
HEARTBEAT_COUNT = int(os.getenv("LAREX_HEARTBEAT_COUNT", "4"))
HEARTBEAT_DELAY_SECONDS = float(os.getenv("LAREX_HEARTBEAT_DELAY_SECONDS", "1"))
OUTPUT_IMAGE_VARIANT = os.getenv("LAREX_OUTPUT_IMAGE_VARIANT", "action-copy")
DEFAULT_TRAINING_DELAY_SECONDS = float(os.getenv("LAREX_TRAINING_DELAY_SECONDS", "5"))
MAX_TRAINING_DELAY_SECONDS = 30.0
DEFAULT_EVALUATION_DELAY_SECONDS = float(os.getenv("LAREX_EVALUATION_DELAY_SECONDS", "5"))
MAX_EVALUATION_DELAY_SECONDS = 30.0
logger = logging.getLogger(__name__)


class TrainingModel(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)


class TrainingDispatchPayload(TrainingModel):
    protocol_version: Literal[1] = Field(alias="protocolVersion")
    run_id: str = Field(alias="runId")
    processor_id: str = Field(alias="processorId")
    workspace_id: str = Field(alias="workspaceId")
    kind: Literal["TRAINING"]
    project_id: None = Field(default=None, alias="projectId")
    dataset_id: str = Field(alias="datasetId")
    dataset_item_ids: list[str] = Field(alias="datasetItemIds")
    parameters: dict[str, Any] = Field(default_factory=dict)
    secret: SecretStr
    pull_url: str = Field(alias="pullUrl")
    heartbeat_url: str = Field(alias="heartbeatUrl")
    result_url: str = Field(alias="resultUrl")

    @model_validator(mode="after")
    def validate_dataset_items(self) -> TrainingDispatchPayload:
        if not self.dataset_item_ids:
            raise ValueError("datasetItemIds must not be empty")
        if len(self.dataset_item_ids) != len(set(self.dataset_item_ids)):
            raise ValueError("datasetItemIds must not contain duplicates")
        return self


class TrainingPage(TrainingModel):
    id: str
    name: str
    source_page_id: str | None = Field(default=None, alias="sourcePageId")
    split: Literal["TRAIN", "VAL", "TEST"] | None = None
    images: list[ActionFile] = Field(default_factory=list)
    xml: list[ActionFile] = Field(default_factory=list)


class TrainingInput(TrainingModel):
    protocol_version: Literal[1] = Field(alias="protocolVersion")
    run_id: str = Field(alias="runId")
    processor_key: str = Field(alias="processorKey")
    kind: Literal["TRAINING"]
    project_id: None = Field(default=None, alias="projectId")
    dataset_id: str = Field(alias="datasetId")
    parameters: dict[str, Any] = Field(default_factory=dict)
    pages: list[TrainingPage] = Field(default_factory=list)
    cancel_requested: bool = Field(default=False, alias="cancelRequested")


class TrainingDispatchVerifier(DispatchVerifier):
    """Compatibility verifier until the released SDK contains training payload models."""

    def verify_training(
        self,
        *,
        method: str,
        path_and_query: str,
        headers: Mapping[str, str],
        body: bytes,
    ) -> TrainingDispatchPayload:
        raw_body, header_run, header_processor, timestamp, nonce = (
            self._verify_envelope(
                method=method,
                path_and_query=path_and_query,
                headers=headers,
                body=body,
            )
        )
        try:
            payload = TrainingDispatchPayload.model_validate_json(raw_body)
        except ValidationError as exc:
            raise DispatchVerificationError(
                f"Training dispatch payload shape is invalid: {exc}", status_code=400
            ) from exc
        if (
            header_processor != payload.processor_id
            or payload.processor_id != self.processor_id
        ):
            raise DispatchVerificationError("Processor id mismatch")
        if header_run != payload.run_id:
            raise DispatchVerificationError("Run id mismatch")
        self._accept(timestamp, nonce)
        return payload


class TrainingContext:
    def __init__(
        self,
        payload: TrainingDispatchPayload,
        client: ActionClient,
        pull_client: httpx.AsyncClient,
    ) -> None:
        self.payload = payload
        self.client = client
        self.pull_client = pull_client

    @property
    def run_id(self) -> str:
        return self.payload.run_id

    async def pull_input(self) -> TrainingInput:
        response = await self.pull_client.get(
            self.payload.pull_url,
            headers={
                "Authorization": f"Bearer {self.payload.secret.get_secret_value()}"
            },
        )
        response.raise_for_status()
        action_input = TrainingInput.model_validate(response.json())
        if action_input.cancel_requested:
            raise ActionCancelled("LAREX requested cancellation")
        return action_input

    async def heartbeat(
        self,
        progress_percent: int | None = None,
        status_message: str | None = None,
        *,
        raise_on_cancel: bool = False,
    ) -> object:
        return await self.client.heartbeat(
            progress_percent,
            status_message,
            raise_on_cancel=raise_on_cancel,
        )

    async def check_cancelled(self) -> None:
        await self.client.heartbeat(raise_on_cancel=True)

    async def download_bytes(self, file: ActionFile) -> bytes:
        return await self.client.download_bytes(file)

    async def complete(self, message: str | None = None) -> object:
        return await self.client.complete(message=message)


class EvaluationCapabilities(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    evaluation_reports: bool = Field(default=False, alias="evaluationReports")


class EvaluationDispatchPayload(BaseModel):
    """Small compatibility payload for images built with SDK < 0.15.

    Evaluation support is additive to the SDK. Keeping this parser in the dev
    image means an existing PyPI SDK layer can still start while the linked SDK
    is being released; once the new SDK is installed, ActionClient/ActionContext
    are used for the actual run.
    """

    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    protocol_version: Literal[1] = Field(alias="protocolVersion")
    run_id: str = Field(alias="runId")
    processor_id: str = Field(alias="processorId")
    workspace_id: str = Field(alias="workspaceId")
    kind: Literal["EVALUATION"]
    project_id: None = Field(default=None, alias="projectId")
    dataset_id: str = Field(alias="datasetId")
    dataset_item_ids: list[str] = Field(alias="datasetItemIds")
    parameters: dict[str, Any] = Field(default_factory=dict)
    secret: SecretStr
    pull_url: str = Field(alias="pullUrl")
    heartbeat_url: str = Field(alias="heartbeatUrl")
    result_url: str = Field(alias="resultUrl")
    capabilities: "EvaluationCapabilities" = Field(default_factory=lambda: EvaluationCapabilities())

    @model_validator(mode="after")
    def validate_dataset_items(self) -> "EvaluationDispatchPayload":
        if not self.dataset_item_ids:
            raise ValueError("datasetItemIds must not be empty")
        if len(self.dataset_item_ids) != len(set(self.dataset_item_ids)):
            raise ValueError("datasetItemIds must not contain duplicates")
        return self


class EvaluationFile(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    id: str
    file_name: str = Field(alias="fileName")
    variant: str | None = None
    mime_type: str | None = Field(default=None, alias="mimeType")
    file_size: int | None = Field(default=None, alias="fileSize")
    download_url: str = Field(alias="downloadUrl")


class EvaluationPage(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    id: str
    name: str
    source_page_id: str | None = Field(default=None, alias="sourcePageId")
    split: Literal["TRAIN", "VAL", "TEST"] | None = None
    images: list[EvaluationFile] = Field(default_factory=list)
    xml: list[EvaluationFile] = Field(default_factory=list)


class EvaluationInput(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    protocol_version: Literal[1] = Field(alias="protocolVersion")
    run_id: str = Field(alias="runId")
    processor_key: str = Field(alias="processorKey")
    kind: Literal["EVALUATION"]
    project_id: None = Field(default=None, alias="projectId")
    dataset_id: str = Field(alias="datasetId")
    parameters: dict[str, Any] = Field(default_factory=dict)
    pages: list[EvaluationPage] = Field(default_factory=list)
    cancel_requested: bool = Field(default=False, alias="cancelRequested")


class LegacyEvaluationContext:
    def __init__(
        self,
        payload: EvaluationDispatchPayload,
        client: ActionClient,
        pull_client: httpx.AsyncClient,
    ) -> None:
        self.payload = payload
        self.client = client
        self.pull_client = pull_client

    @property
    def run_id(self) -> str:
        return self.payload.run_id

    @property
    def capabilities(self) -> EvaluationCapabilities:
        return self.payload.capabilities

    async def pull_input(self) -> EvaluationInput:
        response = await self.pull_client.get(
            self.payload.pull_url,
            headers={"Authorization": f"Bearer {self.payload.secret.get_secret_value()}"},
        )
        response.raise_for_status()
        action_input = EvaluationInput.model_validate(response.json())
        if action_input.cancel_requested:
            raise ActionCancelled("LAREX requested cancellation")
        return action_input

    async def heartbeat(
        self,
        progress_percent: int | None = None,
        status_message: str | None = None,
        *,
        raise_on_cancel: bool = False,
    ) -> object:
        return await self.client.heartbeat(
            progress_percent,
            status_message,
            raise_on_cancel=raise_on_cancel,
        )

    async def check_cancelled(self) -> None:
        await self.client.heartbeat(raise_on_cancel=True)

    async def download_bytes(self, file: EvaluationFile) -> bytes:
        response = await self.pull_client.get(
            file.download_url,
            headers={"Authorization": f"Bearer {self.payload.secret.get_secret_value()}"},
        )
        response.raise_for_status()
        return response.content

    async def complete_evaluation(self, report: Any, message: str | None = None) -> object:
        manifest = {
            "protocolVersion": 1,
            "status": "completed",
            "message": message,
            "evaluationReport": report.model_dump(mode="json", by_alias=True),
        }
        response = await self.pull_client.post(
            self.payload.result_url,
            headers={"Authorization": f"Bearer {self.payload.secret.get_secret_value()}"},
            files={
                "manifest": (
                    "manifest.json",
                    json.dumps(manifest, separators=(",", ":")),
                    "application/json",
                )
            },
        )
        response.raise_for_status()
        return response.json()


class EvaluationDispatchVerifier(DispatchVerifier):
    def verify_evaluation(
        self,
        *,
        method: str,
        path_and_query: str,
        headers: Mapping[str, str],
        body: bytes,
    ) -> EvaluationDispatchPayload:
        raw_body, header_run, header_processor, timestamp, nonce = self._verify_envelope(
            method=method, path_and_query=path_and_query, headers=headers, body=body
        )
        try:
            payload = EvaluationDispatchPayload.model_validate_json(raw_body)
        except ValidationError as exc:
            raise DispatchVerificationError(
                f"Evaluation dispatch payload shape is invalid: {exc}", status_code=400
            ) from exc
        if header_processor != payload.processor_id or payload.processor_id != self.processor_id:
            raise DispatchVerificationError("Processor id mismatch")
        if header_run != payload.run_id:
            raise DispatchVerificationError("Run id mismatch")
        self._accept(timestamp, nonce)
        return payload


async def process_run(ctx: ActionContext) -> None:
    try:
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
                        file_name=image.file_name
                        or f"{page.name}-{OUTPUT_IMAGE_VARIANT}",
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
    except ActionCancelled:
        raise


async def train_run(ctx: TrainingContext) -> None:
    """Validate frozen training pairs and simulate a short, cancellable training run."""
    try:
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
                raise ValueError(
                    f"PAGE XML for dataset item {page.id} is invalid"
                ) from exc
            assert page.split is not None
            split_counts[page.split] += 1
            progress = 5 + int(((index + 1) / page_count) * 40)
            await ctx.heartbeat(
                progress,
                f"Validated frozen pair {index + 1}/{page_count}",
                raise_on_cancel=True,
            )

        delay_seconds = _training_delay_seconds(
            action_input.parameters.get("delaySeconds")
        )
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
    except ActionCancelled:
        raise


async def evaluate_run(ctx: ActionContext, *, profile: str) -> None:
    """Validate frozen evaluation pairs and return a deterministic report fixture."""
    action_input = await ctx.pull_input()
    if action_input.kind != "EVALUATION" or not action_input.dataset_id:
        raise ValueError("Mock evaluation requires a dataset-scoped EVALUATION input")
    if not action_input.pages:
        raise ValueError("Mock evaluation requires at least one frozen dataset item")
    for index, page in enumerate(action_input.pages):
        if not page.source_page_id or page.split is None or len(page.images) != 1 or len(page.xml) != 1:
            raise ValueError(f"Dataset item {page.id} must contain sourcePageId, split, one image and one XML")
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
        raise ValueError("delaySeconds must be a number")
    value = float(raw_value)
    if value < 1 or value > MAX_EVALUATION_DELAY_SECONDS:
        raise ValueError(f"delaySeconds must be between 1 and {int(MAX_EVALUATION_DELAY_SECONDS)}")
    return value


def _evaluation_quality(raw_value: object) -> float:
    if raw_value is None:
        return 0.95
    if isinstance(raw_value, bool) or not isinstance(raw_value, (int, float)):
        raise ValueError("quality must be a number")
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
        profile="larex.ocr-recognition", profileVersion=1, title="Mock recognition evaluation",
        summary=[
            EvaluationMetric(key="cer", label="Character error rate", value=cer, format="PERCENT", direction="LOWER_IS_BETTER"),
            EvaluationMetric(key="wer", label="Word error rate", value=wer, format="PERCENT", direction="LOWER_IS_BETTER"),
            EvaluationMetric(key="characters", label="Characters", value=characters, format="INTEGER", direction="NEUTRAL"),
        ],
        tables=[EvaluationTable(
            key="confusions", title="Character confusions",
            columns=[EvaluationTableColumn(key="reference", label="Reference", type="STRING"),
                     EvaluationTableColumn(key="prediction", label="Prediction", type="STRING"),
                     EvaluationTableColumn(key="count", label="Count", type="INTEGER")],
            rows=[EvaluationTableRow(values={"reference": "e", "prediction": "c", "count": int(cer * characters)})],
            totalRows=1,
        ), EvaluationTable(
            key="scripts", title="Script statistics",
            columns=[EvaluationTableColumn(key="script", label="Script", type="STRING"),
                     EvaluationTableColumn(key="accuracy", label="Accuracy", type="NUMBER")],
            rows=[EvaluationTableRow(values={"script": "Latin", "accuracy": quality})], totalRows=1,
        )],
        samples=[EvaluationSample(
            id=f"{page.id}:line-1", inputId=page.id, label=f"{page.name} / line 1",
            fields={"reference": "mock ground truth", "prediction": "mock prediction"},
        ) for page in action_input.pages],
        metadata={"engine": "mock", "model": "quality-fixture"},
    )


def _layout_report(action_input: ActionInput, quality: float) -> EvaluationReport:
    return EvaluationReport(
        profile="larex.layout-segmentation", profileVersion=1, title="Mock layout evaluation",
        summary=[
            EvaluationMetric(key="pixel_accuracy", label="Pixel accuracy", value=quality, format="PERCENT", direction="HIGHER_IS_BETTER"),
            EvaluationMetric(key="mean_iou", label="Mean IoU", value=round(quality * 0.92, 6), format="PERCENT", direction="HIGHER_IS_BETTER"),
        ],
        tables=[EvaluationTable(
            key="classes", title="Class IoU", columns=[
                EvaluationTableColumn(key="class", label="Class", type="STRING"),
                EvaluationTableColumn(key="iou", label="IoU", type="NUMBER")],
            rows=[EvaluationTableRow(values={"class": "text", "iou": quality})], totalRows=1,
        ), EvaluationTable(
            key="baselines", title="Baseline metrics", columns=[
                EvaluationTableColumn(key="class", label="Class", type="STRING"),
                EvaluationTableColumn(key="precision", label="Precision", type="NUMBER"),
                EvaluationTableColumn(key="recall", label="Recall", type="NUMBER"),
                EvaluationTableColumn(key="f1", label="F1", type="NUMBER")],
            rows=[EvaluationTableRow(values={"class": "baseline", "precision": quality, "recall": quality, "f1": quality})], totalRows=1,
        )],
        samples=[EvaluationSample(id=page.id, inputId=page.id, label=page.name, fields={"regions": 1}) for page in action_input.pages],
        metadata={"engine": "mock", "model": "quality-fixture"},
    )


def _validate_training_input(
    action_input: TrainingInput,
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
    if not action_input.dataset_id:
        raise ValueError("Mock training requires datasetId")
    if action_input.dataset_id != expected_dataset_id:
        raise ValueError("Dispatch and pulled input datasetId values do not match")
    if action_input.project_id is not None:
        raise ValueError("Mock training must not receive projectId")

    pages = action_input.pages
    if not pages:
        raise ValueError("Mock training requires at least one frozen dataset item")
    received_item_ids = [page.id for page in pages]
    if len(received_item_ids) != len(set(received_item_ids)):
        raise ValueError("Pulled training input contains duplicate dataset item IDs")
    if set(received_item_ids) != set(expected_item_ids):
        raise ValueError("Dispatch and pulled input dataset item IDs do not match")
    for page in pages:
        if not page.source_page_id:
            raise ValueError(f"Dataset item {page.id} is missing sourcePageId")
        if page.split not in {"TRAIN", "VAL"}:
            raise ValueError(
                f"Dataset item {page.id} has unsupported split {page.split}"
            )
        if len(page.images) != 1:
            raise ValueError(
                f"Dataset item {page.id} must contain exactly one frozen image"
            )
        if len(page.xml) != 1:
            raise ValueError(
                f"Dataset item {page.id} must contain exactly one frozen PAGE XML"
            )


def _training_delay_seconds(raw_value: object) -> float:
    if raw_value is None:
        return DEFAULT_TRAINING_DELAY_SECONDS
    if isinstance(raw_value, bool) or not isinstance(raw_value, (int, float)):
        raise ValueError("delaySeconds must be a number")
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


def _request_path_and_query(request: Request) -> str:
    raw_path = request.scope.get("raw_path")
    path = (
        raw_path.decode("ascii", errors="surrogateescape")
        if isinstance(raw_path, bytes)
        else request.url.path
    )
    return f"{path}?{request.url.query}" if request.url.query else path


async def _execute_training(payload: TrainingDispatchPayload) -> None:
    client = ActionClient(
        pull_url=payload.pull_url,
        heartbeat_url=payload.heartbeat_url,
        result_url=payload.result_url,
        secret=payload.secret.get_secret_value(),
        run_id=payload.run_id,
    )
    async with client, httpx.AsyncClient(timeout=120.0) as pull_client:
        context = TrainingContext(payload, client, pull_client)
        try:
            await train_run(context)
        except ActionCancelled:
            logger.info("Mock training run %s cancelled", payload.run_id)
            try:
                await client.cancelled()
            except Exception:
                logger.exception(
                    "Could not acknowledge cancelled run %s", payload.run_id
                )
        except Exception as exc:
            logger.exception("Mock training run %s failed", payload.run_id)
            try:
                await client.fail(
                    "Mock training failed", log=f"{type(exc).__name__}: {exc}"
                )
            except Exception:
                logger.exception("Could not report failed run %s", payload.run_id)


async def _execute_evaluation(payload: EvaluationDispatchPayload, profile: str) -> None:
    if HAS_EVALUATION_SDK:
        client = ActionClient.from_dispatch(payload)  # type: ignore[arg-type]
    else:
        client = ActionClient(
            pull_url=payload.pull_url,
            heartbeat_url=payload.heartbeat_url,
            result_url=payload.result_url,
            secret=payload.secret.get_secret_value(),
            run_id=payload.run_id,
        )
    async with client:
        if HAS_EVALUATION_SDK:
            context = ActionContext(payload=payload, client=client)  # type: ignore[arg-type]
        else:
            async with httpx.AsyncClient(timeout=120.0) as pull_client:
                context = LegacyEvaluationContext(payload, client, pull_client)
                try:
                    await evaluate_run(context, profile=profile)  # type: ignore[arg-type]
                except ActionCancelled:
                    logger.info("Mock evaluation run %s cancelled", payload.run_id)
                    try:
                        await client.cancelled()
                    except Exception:
                        logger.exception("Could not acknowledge cancelled evaluation run %s", payload.run_id)
                except Exception as exc:
                    logger.exception("Mock evaluation run %s failed", payload.run_id)
                    try:
                        await client.fail("Mock evaluation failed", log=f"{type(exc).__name__}: {exc}")
                    except Exception:
                        logger.exception("Could not report failed evaluation run %s", payload.run_id)
                return
        context = ActionContext(payload=payload, client=client)  # type: ignore[arg-type]
        try:
            await evaluate_run(context, profile=profile)
        except ActionCancelled:
            logger.info("Mock evaluation run %s cancelled", payload.run_id)
            try:
                await client.cancelled()
            except Exception:
                logger.exception("Could not acknowledge cancelled evaluation run %s", payload.run_id)
        except Exception as exc:
            logger.exception("Mock evaluation run %s failed", payload.run_id)
            try:
                await client.fail("Mock evaluation failed", log=f"{type(exc).__name__}: {exc}")
            except Exception:
                logger.exception("Could not report failed evaluation run %s", payload.run_id)


async def evaluation_dispatch(
    request: Request, background_tasks: BackgroundTasks, *, processor_id: str, profile: str
) -> dict[str, str]:
    body = await request.body()
    if len(body) > 1_048_576:
        raise HTTPException(status_code=413, detail="Dispatch body is too large")
    verifier = evaluation_verifiers[processor_id]
    try:
        payload = verifier.verify_evaluation(
            method=request.method,
            path_and_query=_request_path_and_query(request),
            headers=request.headers,
            body=body,
        )
        if payload.kind != "EVALUATION":
            raise DispatchVerificationError("Evaluation dispatch requires kind EVALUATION", status_code=400)
    except DispatchVerificationError as exc:
        logger.warning("Rejected mock evaluation dispatch: %s", exc)
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc
    background_tasks.add_task(_execute_evaluation, payload, profile)
    return {"status": "accepted", "runId": payload.run_id}


async def evaluation_preflight(request: Request, *, processor_id: str) -> dict[str, object]:
    body = await request.body()
    verifier = evaluation_verifiers[processor_id]
    try:
        payload = verifier.verify_preflight(
            method=request.method,
            path_and_query=_request_path_and_query(request),
            headers=request.headers,
            body=body,
        )
    except DispatchVerificationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc
    return {
        "status": "ok", "protocolVersion": 1, "requestId": payload.request_id,
        "processorId": processor_id,
        "capabilities": {"incrementalPageResults": False, "customFileResults": False, "evaluationReports": True},
    }


async def evaluation_ocr_dispatch(request: Request, background_tasks: BackgroundTasks) -> dict[str, str]:
    return await evaluation_dispatch(request, background_tasks, processor_id=OCR_EVALUATION_PROCESSOR_ID, profile="larex.ocr-recognition")


async def evaluation_layout_dispatch(request: Request, background_tasks: BackgroundTasks) -> dict[str, str]:
    return await evaluation_dispatch(request, background_tasks, processor_id=LAYOUT_EVALUATION_PROCESSOR_ID, profile="larex.layout-segmentation")


async def evaluation_ocr_preflight(request: Request) -> dict[str, object]:
    return await evaluation_preflight(request, processor_id=OCR_EVALUATION_PROCESSOR_ID)


async def evaluation_layout_preflight(request: Request) -> dict[str, object]:
    return await evaluation_preflight(request, processor_id=LAYOUT_EVALUATION_PROCESSOR_ID)


async def training_dispatch(
    request: Request, background_tasks: BackgroundTasks
) -> dict[str, str]:
    body = await request.body()
    if len(body) > 1_048_576:
        raise HTTPException(status_code=413, detail="Dispatch body is too large")
    try:
        payload = training_verifier.verify_training(
            method=request.method,
            path_and_query=_request_path_and_query(request),
            headers=request.headers,
            body=body,
        )
    except DispatchVerificationError as exc:
        logger.warning("Rejected mock training dispatch: %s", exc)
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc
    background_tasks.add_task(_execute_training, payload)
    return {"status": "accepted", "runId": payload.run_id}


async def training_preflight(request: Request) -> dict[str, object]:
    body = await request.body()
    try:
        payload = training_verifier.verify_preflight(
            method=request.method,
            path_and_query=_request_path_and_query(request),
            headers=request.headers,
            body=body,
        )
    except DispatchVerificationError as exc:
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc
    return {
        "status": "ok",
        "protocolVersion": 1,
        "requestId": payload.request_id,
        "processorId": TRAINING_PROCESSOR_ID,
        "capabilities": {
            "incrementalPageResults": False,
            "customFileResults": False,
            "parameterValueDiscovery": False,
        },
    }


async def training_health() -> dict[str, str]:
    return {"status": "ok"}


app = create_larex_action_app(
    processor_id=PROCESSING_PROCESSOR_ID,
    dispatch_secret_env=DISPATCH_SECRET_ENV,
    handler=process_run,
    route_prefixes=("", "/processing"),
)
training_secret = os.getenv(DISPATCH_SECRET_ENV)
if not training_secret:
    raise ValueError(f"Dispatch secret is not configured: {DISPATCH_SECRET_ENV}")
training_verifier = TrainingDispatchVerifier(
    processor_id=TRAINING_PROCESSOR_ID,
    dispatch_secret=training_secret,
)
evaluation_verifiers = {
    OCR_EVALUATION_PROCESSOR_ID: EvaluationDispatchVerifier(
        processor_id=OCR_EVALUATION_PROCESSOR_ID, dispatch_secret=training_secret
    ),
    LAYOUT_EVALUATION_PROCESSOR_ID: EvaluationDispatchVerifier(
        processor_id=LAYOUT_EVALUATION_PROCESSOR_ID, dispatch_secret=training_secret
    ),
}
app.add_api_route("/training/health", training_health, methods=["GET"])
app.add_api_route("/training/ready", training_health, methods=["GET"])
app.add_api_route("/training/preflight", training_preflight, methods=["POST"])
app.add_api_route("/training/dispatch", training_dispatch, methods=["POST"])
app.add_api_route(
    "/evaluation/ocr/dispatch",
    evaluation_ocr_dispatch, methods=["POST"],
)
app.add_api_route(
    "/evaluation/ocr/preflight",
    evaluation_ocr_preflight, methods=["POST"],
)
app.add_api_route(
    "/evaluation/ocr/health", training_health, methods=["GET"]
)
app.add_api_route(
    "/evaluation/layout/dispatch",
    evaluation_layout_dispatch, methods=["POST"],
)
app.add_api_route(
    "/evaluation/layout/preflight",
    evaluation_layout_preflight, methods=["POST"],
)
app.add_api_route(
    "/evaluation/layout/health", training_health, methods=["GET"]
)
