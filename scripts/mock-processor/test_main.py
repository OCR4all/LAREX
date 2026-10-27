from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import uuid
from datetime import UTC, datetime
from types import SimpleNamespace
from typing import Any

import httpx
import pytest
from larex_actions import ActionClient, ActionFile, ActionInput, ActionPage
from larex_actions.verifier import canonical_dispatch_request

os.environ.setdefault("LAREX_DISPATCH_HMAC_SECRET", "test-dispatch-secret")

import main


class FakeContext:
    def __init__(self, action_input: ActionInput) -> None:
        self.action_input = action_input
        self.run_id = action_input.run_id
        self.payload = SimpleNamespace(
            dataset_id=action_input.dataset_id,
            dataset_item_ids=[page.id for page in action_input.pages],
        )
        self.heartbeats: list[tuple[int | None, str | None]] = []
        self.completed_message: str | None = None

    async def heartbeat(
        self,
        progress_percent: int | None = None,
        status_message: str | None = None,
        **_: Any,
    ) -> None:
        self.heartbeats.append((progress_percent, status_message))

    async def pull_input(self) -> ActionInput:
        return self.action_input

    async def check_cancelled(self) -> None:
        return None

    async def download_bytes(self, file: ActionFile) -> bytes:
        if file.file_name.endswith(".xml"):
            return b'<PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15"/>'
        return b"image-data"

    async def complete(self, message: str | None = None) -> None:
        self.completed_message = message


def training_input(**overrides: Any) -> ActionInput:
    data: dict[str, Any] = {
        "protocolVersion": 1,
        "runId": "run-1",
        "processorKey": "mock-training",
        "kind": "TRAINING",
        "datasetId": "dataset-1",
        "parameters": {"delaySeconds": 1},
        "pages": [
            ActionPage(
                id="item-1",
                name="page-1",
                sourcePageId="source-page-1",
                split="TRAIN",
                images=[
                    ActionFile(
                        id="image-1",
                        fileName="page-1.png",
                        downloadUrl="http://app:8080/image-1",
                    )
                ],
                xml=[
                    ActionFile(
                        id="xml-1",
                        fileName="page-1.xml",
                        downloadUrl="http://app:8080/xml-1",
                    )
                ],
            )
        ],
    }
    data.update(overrides)
    return ActionInput.model_validate(data)


@pytest.mark.asyncio
async def test_processing_failure_submits_page_id_once(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class ProcessingContext(FakeContext):
        def __init__(self, action_input: ActionInput) -> None:
            super().__init__(action_input)
            self.failures: list[tuple[str, str | None, str | None]] = []

        async def submit_page_results(
            self,
            page_id: str,
            results: Any = None,
            message: str | None = None,
            *,
            page_status: str | None = None,
            reason: str | None = None,
            warnings: list[str] | None = None,
        ) -> None:
            assert results is None
            self.failures.append((page_id, page_status, reason))

    monkeypatch.setattr(main, "HEARTBEAT_COUNT", 0)
    context = ProcessingContext(ActionInput.model_validate({
        "protocolVersion": 1,
        "runId": "run-1",
        "processorKey": "mock-image-copy",
        "projectId": "project-1",
        "parameters": {"failEveryNthPage": 1},
        "capabilities": {"runReports": True},
        "pages": [{"id": "page-1", "name": "Page 1"}, {"id": "page-2", "name": "Page 2"}],
    }))

    await main.process_run(context)  # type: ignore[arg-type]

    assert context.failures == [
        ("page-1", "FAILED", "Simulated recoverable page failure"),
        ("page-2", "FAILED", "Simulated recoverable page failure"),
    ]
    assert context.completed_message is not None
    assert "2 page(s) failed" in context.completed_message


@pytest.mark.asyncio
async def test_training_validates_frozen_pairs_and_completes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def no_sleep(_: float) -> None:
        return None

    monkeypatch.setattr(main.asyncio, "sleep", no_sleep)
    context = FakeContext(training_input())

    await main.train_run(context)  # type: ignore[arg-type]

    assert context.completed_message is not None
    assert "TRAIN=1, VAL=0" in context.completed_message
    assert any(
        message == "Validated frozen pair 1/1" for _, message in context.heartbeats
    )
    assert any(message == "Mock training 1/1" for _, message in context.heartbeats)


def test_sdk_rejects_items_without_xml() -> None:
    data = training_input().model_dump(by_alias=True)
    data["pages"][0]["xml"] = []
    with pytest.raises(ValueError, match="one XML"):
        ActionInput.model_validate(data)


def test_training_delay_is_bounded() -> None:
    with pytest.raises(ValueError, match="between 1 and 30"):
        main._training_delay_seconds(31)


def signed_request(payload: dict[str, Any], path: str) -> tuple[bytes, dict[str, str]]:
    body = json.dumps(payload, separators=(",", ":")).encode()
    timestamp = datetime.now(UTC).isoformat().replace("+00:00", "Z")
    nonce = uuid.uuid4().hex
    request_id = payload.get("runId", payload.get("requestId"))
    body_hash = _b64url(hashlib.sha256(body).digest())
    canonical = canonical_dispatch_request(
        method="POST",
        path_and_query=path,
        run_id=request_id,
        processor_id=payload["processorId"],
        timestamp=timestamp,
        nonce=nonce,
        body_hash=body_hash,
    )
    signature = "v1=" + _b64url(
        hmac.new(b"test-dispatch-secret", canonical.encode(), hashlib.sha256).digest()
    )
    return body, {
        "X-LAREX-Action-Auth": "hmac-sha256;v=1",
        "X-LAREX-Action-Processor": payload["processorId"],
        "X-LAREX-Action-Run-Id": request_id,
        "X-LAREX-Action-Timestamp": timestamp,
        "X-LAREX-Action-Nonce": nonce,
        "X-LAREX-Action-Body-SHA256": body_hash,
        "X-LAREX-Action-Signature": signature,
    }


PROCESSORS = [
    ("/training", "mock-training", "TRAINING", None),
    ("/evaluation/ocr", "mock-ocr-evaluation", "EVALUATION", "larex.ocr-recognition"),
    (
        "/evaluation/layout",
        "mock-layout-evaluation",
        "EVALUATION",
        "larex.layout-segmentation",
    ),
]


@pytest.mark.parametrize("prefix,processor_id,kind,profile", PROCESSORS)
@pytest.mark.parametrize("scenario", ["success", "cancelled", "invalid_xml"])
@pytest.mark.asyncio
async def test_sdk_routes_pull_frozen_inputs_and_complete(
    monkeypatch: pytest.MonkeyPatch,
    prefix: str,
    processor_id: str,
    kind: str,
    profile: str | None,
    scenario: str,
) -> None:
    from email import policy
    from email.parser import BytesParser

    async def no_sleep(_: float) -> None:
        return None

    monkeypatch.setattr(main.asyncio, "sleep", no_sleep)
    inputs = training_input().model_dump(mode="json", by_alias=True)
    inputs.update(
        kind=kind,
        processorKey=processor_id,
        capabilities={"evaluationReports": kind == "EVALUATION"},
    )
    downloads: list[str] = []
    manifests: list[dict[str, Any]] = []
    heartbeats: list[dict[str, Any]] = []

    def server(request: httpx.Request) -> httpx.Response:
        assert request.headers["authorization"] == "Bearer run-secret"
        if request.url.path.endswith("/input"):
            return httpx.Response(200, json=inputs)
        if request.url.path.endswith("/heartbeat"):
            heartbeats.append(json.loads(request.content))
            return httpx.Response(
                200, json={"cancelRequested": scenario == "cancelled"}
            )
        if request.url.path == "/image-1":
            downloads.append("image")
            return httpx.Response(200, content=b"image-data")
        if request.url.path == "/xml-1":
            downloads.append("xml")
            return httpx.Response(
                200, content=b"not-xml" if scenario == "invalid_xml" else b"<PcGts/>"
            )
        if request.url.path.endswith("/results"):
            message = BytesParser(policy=policy.default).parsebytes(
                f"Content-Type: {request.headers['content-type']}\r\n\r\n".encode()
                + request.content
            )
            parts = list(message.iter_parts())
            assert len(parts) == 1
            manifests.append(json.loads(parts[0].get_content()))
            return httpx.Response(200, json={"status": "COMPLETED"})
        raise AssertionError(f"Unexpected URL: {request.url}")

    original_factory = ActionClient.from_dispatch
    async with httpx.AsyncClient(
        transport=httpx.MockTransport(server)
    ) as callback_client:
        monkeypatch.setattr(
            ActionClient,
            "from_dispatch",
            classmethod(
                lambda cls, payload, **kwargs: original_factory(
                    payload, client=callback_client
                )
            ),
        )
        payload = {
            "protocolVersion": 1,
            "runId": "run-1",
            "processorId": processor_id,
            "workspaceId": "workspace-1",
            "kind": kind,
            "datasetId": "dataset-1",
            "datasetItemIds": ["item-1"],
            "secret": "run-secret",
            "pullUrl": "http://app:8080/api/v1/actions/runs/run-1/input",
            "heartbeatUrl": "http://app:8080/api/v1/actions/runs/run-1/heartbeat",
            "resultUrl": "http://app:8080/api/v1/actions/runs/run-1/results",
        }
        body, headers = signed_request(payload, prefix + "/dispatch")
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=main.app), base_url="http://test"
        ) as client:
            response = await client.post(
                prefix + "/dispatch", content=body, headers=headers
            )

    assert response.status_code == 200
    assert response.json() == {"status": "accepted", "runId": "run-1"}
    if scenario != "success":
        assert not manifests
        assert heartbeats[-1]["status"] == (
            "cancelled" if scenario == "cancelled" else "failed"
        )
        return
    assert sorted(downloads) == ["image", "xml"]
    assert heartbeats and all(h["status"] == "running" for h in heartbeats)
    assert len(manifests) == 1
    assert manifests[0]["files"] == []
    assert manifests[0]["status"] == "completed"
    if profile:
        assert manifests[0]["evaluationReport"]["profile"] == profile
        assert manifests[0]["evaluationReport"]["samples"][0]["inputId"] == "item-1"
    else:
        assert "evaluationReport" not in manifests[0]


@pytest.mark.parametrize("prefix,processor_id,kind,profile", PROCESSORS + [("/integration", "mock-integration", "INTEGRATION", None)])
@pytest.mark.asyncio
async def test_sdk_preflight_advertises_dataset_capabilities(
    prefix: str,
    processor_id: str,
    kind: str,
    profile: str | None,
) -> None:
    path = prefix + "/preflight"
    body, headers = signed_request(
        {
            "protocolVersion": 1,
            "requestId": "preflight-1",
            "processorId": processor_id,
        },
        path,
    )
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=main.app), base_url="http://test"
    ) as client:
        response = await client.post(path, content=body, headers=headers)
        assert (await client.get(prefix + "/health")).status_code == 200
        assert (await client.get(prefix + "/ready")).status_code == 200
    assert response.status_code == 200
    capabilities = response.json()["capabilities"]
    assert capabilities["incrementalPageResults"] is False
    assert capabilities["customFileResults"] is False
    assert capabilities.get("evaluationReports", False) == (kind == "EVALUATION")


def _b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


@pytest.mark.parametrize("prefix", ["", "/processing"])
@pytest.mark.asyncio
async def test_existing_processing_routes_retain_capabilities(prefix: str) -> None:
    path = prefix + "/preflight"
    body, headers = signed_request(
        {
            "protocolVersion": 1,
            "requestId": "processing-preflight",
            "processorId": main.PROCESSING_PROCESSOR_ID,
        },
        path,
    )
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=main.app), base_url="http://test"
    ) as client:
        response = await client.post(path, content=body, headers=headers)
        assert (await client.get(prefix + "/health")).status_code == 200
    assert response.status_code == 200
    assert response.json()["capabilities"]["incrementalPageResults"] is True
    assert response.json()["capabilities"]["customFileResults"] is True


@pytest.mark.parametrize("scope", ["project", "region", "textline", "dataset"])
@pytest.mark.parametrize("files", ["images", "xml", "both"])
@pytest.mark.parametrize("scenario", ["success", "failed", "cancelled"])
@pytest.mark.asyncio
async def test_integration_signed_lifecycle(
    monkeypatch: pytest.MonkeyPatch, scope: str, files: str, scenario: str
) -> None:
    from email import policy
    from email.parser import BytesParser

    inputs = training_input().model_dump(mode="json", by_alias=True)
    inputs.update(
        kind="INTEGRATION", processorKey="mock-integration",
        projectId=None if scope == "dataset" else "project-1",
        datasetId="dataset-1" if scope == "dataset" else None,
        parameters={"delaySeconds": 0, "simulateFailure": scenario == "failed"},
        inputRequirements={
            "images": {"level": "OPTIONAL" if files != "xml" else "NONE"},
            "xml": {"level": "OPTIONAL" if files != "images" else "NONE"},
        },
    )
    if files == "images":
        inputs["pages"][0]["xml"] = []
    if files == "xml":
        inputs["pages"][0]["images"] = []
    if scope in {"region", "textline"}:
        inputs["targetSelection"] = {
            "type": "REGION" if scope == "region" else "TEXT_LINE",
            "pages": [{"pageId": "item-1", "regionIds": ["r1"], "textLineIds": ["l1"]}],
        }
    manifests: list[dict[str, Any]] = []
    heartbeats: list[dict[str, Any]] = []
    downloads: list[str] = []

    def server(request: httpx.Request) -> httpx.Response:
        assert request.headers["authorization"] == "Bearer run-secret"
        if request.url.path.endswith("/input"):
            return httpx.Response(200, json=inputs)
        if request.url.path.endswith("/heartbeat"):
            heartbeats.append(json.loads(request.content))
            return httpx.Response(200, json={"cancelRequested": scenario == "cancelled"})
        if request.url.path in {"/image-1", "/xml-1"}:
            downloads.append(request.url.path)
            return httpx.Response(200, content=b"frozen input bytes")
        if request.url.path.endswith("/results"):
            message = BytesParser(policy=policy.default).parsebytes(
                f"Content-Type: {request.headers['content-type']}\r\n\r\n".encode() + request.content
            )
            parts = list(message.iter_parts())
            assert len(parts) == 1
            manifests.append(json.loads(parts[0].get_content()))
            return httpx.Response(200, json={"status": "COMPLETED"})
        raise AssertionError(f"Unexpected callback: {request.url}")

    original = ActionClient.from_dispatch
    async with httpx.AsyncClient(transport=httpx.MockTransport(server)) as callbacks:
        monkeypatch.setattr(ActionClient, "from_dispatch", classmethod(
            lambda cls, payload, **kwargs: original(payload, client=callbacks)
        ))
        payload = {
            "protocolVersion": 1, "runId": "run-1", "processorId": "mock-integration",
            "workspaceId": "workspace-1", "kind": "INTEGRATION",
            "projectId": inputs["projectId"], "datasetId": inputs["datasetId"],
            "pageIds": [] if scope == "dataset" else ["item-1"],
            "datasetItemIds": ["item-1"] if scope == "dataset" else [],
            "secret": "run-secret", "pullUrl": "http://app:8080/input",
            "heartbeatUrl": "http://app:8080/heartbeat", "resultUrl": "http://app:8080/results",
        }
        body, headers = signed_request(payload, "/integration/dispatch")
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=main.app), base_url="http://test") as client:
            response = await client.post("/integration/dispatch", content=body, headers=headers)
    assert response.status_code == 200
    assert len(downloads) == (0 if scenario == "cancelled" else 2 if files == "both" else 1)
    if scenario == "success":
        assert len(manifests) == 1
        assert manifests[0]["files"] == []
        assert "1 input(s)" in manifests[0]["message"]
    else:
        assert not manifests
        assert heartbeats[-1]["status"] == ("failed" if scenario == "failed" else "cancelled")


def sample_image() -> bytes:
    from io import BytesIO
    from PIL import Image

    image = Image.new("L", (200, 300), 255)
    image.putpixel((0, 0), 50)
    image.putpixel((1, 0), 180)
    output = BytesIO()
    image.save(output, format="PNG")
    return output.getvalue()


def test_workflow_mock_chain_creates_region_baseline_and_recognition() -> None:
    from io import BytesIO
    from xml.etree import ElementTree as ET
    from PIL import Image

    binary = main.binarize(sample_image(), 128)
    with Image.open(BytesIO(binary)) as image:
        assert image.size == (200, 300)
        assert image.getpixel((0, 0)) == 0
        assert image.getpixel((1, 0)) == 255
    region_xml = main.transform_xml("region", None, {}, image=binary, filename="binarized.png")
    ns = {"p": main.PAGE_NS}
    region = ET.fromstring(region_xml).find("p:Page/p:TextRegion", ns)
    assert region is not None
    assert region.find("p:Coords", ns).get("points") == "0,0 199,0 199,299 0,299"
    line_xml = main.transform_xml("line", region_xml, {})
    line = ET.fromstring(line_xml).find("p:Page/p:TextRegion/p:TextLine", ns)
    assert line is not None
    assert line.find("p:Baseline", ns) is not None
    ocr_xml = main.transform_xml("ocr", line_xml, {"wordCount": 5, "seed": 11})
    text = ET.fromstring(ocr_xml).find(".//p:Unicode", ns).text
    assert len(text.split()) == 5
    assert text == ET.fromstring(main.transform_xml("ocr", line_xml, {"wordCount": 5, "seed": 11})).find(".//p:Unicode", ns).text
    # Re-running steps replaces their own output instead of duplicating IDs or text alternatives.
    rerun = main.transform_xml("ocr", ocr_xml, {"wordCount": 3, "seed": 12})
    assert len(ET.fromstring(rerun).findall(".//p:TextEquiv", ns)) == 1
    assert len(ET.fromstring(rerun).find(".//p:Unicode", ns).text.split()) == 3
    reset = ET.fromstring(main.transform_xml("region", rerun, {}))
    assert len(reset.findall("p:Page/p:TextRegion", ns)) == 1
    assert not reset.findall(".//p:TextLine", ns)


def test_workflow_mocks_reject_missing_prerequisites_and_invalid_parameters() -> None:
    region_xml = main.transform_xml("region", None, {}, image=sample_image())
    empty_xml = region_xml.replace(b"TextRegion", b"ImageRegion")
    with pytest.raises(ValueError, match="No text region"):
        main.transform_xml("line", empty_xml, {})
    with pytest.raises(ValueError, match="No text line"):
        main.transform_xml("ocr", region_xml, {})
    with pytest.raises(ValueError, match="contain a Page"):
        main.transform_xml("region", b"<PcGts/>", {})
    for invalid in (True, -1, 256, 0.5, float("nan"), "128"):
        with pytest.raises(ValueError, match="threshold must be an integer"):
            main.integer_parameter({"threshold": invalid}, "threshold", 128, 0, 255)


@pytest.mark.asyncio
@pytest.mark.parametrize("operation", ["binarize", "region", "line", "ocr"])
async def test_workflow_mock_routes_publish_matching_processor_ids(operation: str) -> None:
    processor_id = {
        "binarize": "mock-binarization", "region": "mock-whole-page-region",
        "line": "mock-single-text-line", "ocr": "mock-random-ocr",
    }[operation]
    path = f"/workflow/{operation}/preflight"
    body, headers = signed_request({"protocolVersion": 1, "requestId": "workflow-test", "processorId": processor_id}, path)
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=main.app), base_url="http://mock") as client:
        assert (await client.get(f"/workflow/{operation}/health")).status_code == 200
        response = await client.post(path, content=body, headers=headers)
    assert response.status_code == 200
    assert response.json()["processorId"] == processor_id


@pytest.mark.asyncio
async def test_workflow_mocks_chain_through_signed_dispatch_and_incremental_result_uploads(monkeypatch: pytest.MonkeyPatch) -> None:
    from email import policy
    from email.parser import BytesParser
    from xml.etree import ElementTree as ET

    # Start with a color encoding even though its pixels look grayscale.
    from io import BytesIO
    from PIL import Image
    output = BytesIO()
    with Image.open(BytesIO(sample_image())) as source:
        source.convert("RGB").save(output, format="PNG")
    image = output.getvalue()
    xml: bytes | None = None
    operation = ""
    run_id = ""
    statuses: list[str] = []
    uploaded_variants: list[str] = []
    downloaded_images: list[str] = []
    selected_images: list[str] = []

    def server(request: httpx.Request) -> httpx.Response:
        nonlocal image, xml
        assert request.headers["authorization"] == "Bearer run-secret"
        if request.url.path.endswith("/input"):
            return httpx.Response(200, json={
                "protocolVersion": 1, "runId": run_id, "processorKey": processor_id,
                "projectId": "project-1", "parameters": {"threshold": 150, "wordCount": 6},
                "capabilities": {"incrementalPageResults": True},
                "inputRequirements": {
                    "images": {"level": "REQUIRED", "imageTypes": ["COLOR", "GRAYSCALE"] if operation == "binarize" else ["BINARY"]},
                    "xml": {"level": "REQUIRED" if operation in {"line", "ocr"} else "OPTIONAL" if operation == "region" else "NONE"},
                },
                "pages": [{"id": "page-1", "name": "Page 1",
                           "images": [{"id": "image", "fileName": "page.png", "variant": "original" if operation == "binarize" else "binarized", "imageType": "COLOR" if operation == "binarize" else "BINARY", "downloadUrl": "http://app:8080/image"}],
                           "xml": [{"id": "xml", "fileName": "page.xml", "downloadUrl": "http://app:8080/xml"}] if xml and operation != "binarize" else []}],
            })
        if request.url.path == "/image":
            downloaded_images.append(operation)
            return httpx.Response(200, content=image)
        if request.url.path == "/xml":
            return httpx.Response(200, content=xml)
        if request.url.path.endswith("/heartbeat"):
            return httpx.Response(200, json={"cancelRequested": False})
        if request.url.path.endswith("/results"):
            message = BytesParser(policy=policy.default).parsebytes(
                f"Content-Type: {request.headers['content-type']}\r\n\r\n".encode() + request.content)
            parts = {part.get_param("name", header="content-disposition"): part for part in message.iter_parts()}
            manifest = json.loads(parts["manifest"].get_content())
            statuses.append(manifest["status"])
            if manifest.get("pageId"):
                selected_images.append(manifest["message"])
            for file in manifest.get("files", []):
                content = parts[file["fieldName"]].get_payload(decode=True)
                assert file["pageId"] == "page-1"
                if file["type"] == "image":
                    image = content
                    uploaded_variants.append(file["variant"])
                else:
                    xml = content
            return httpx.Response(200, json={"status": "RUNNING"})
        raise AssertionError(f"Unexpected URL {request.url}")

    original = ActionClient.from_dispatch
    async with httpx.AsyncClient(transport=httpx.MockTransport(server)) as callbacks:
        monkeypatch.setattr(ActionClient, "from_dispatch", classmethod(lambda cls, payload, **kwargs: original(payload, client=callbacks)))
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=main.app), base_url="http://mock") as client:
            for index, (operation, processor_id) in enumerate((
                ("binarize", "mock-binarization"), ("region", "mock-whole-page-region"),
                ("line", "mock-single-text-line"), ("ocr", "mock-random-ocr"),
            )):
                run_id = f"workflow-step-{index}"
                path = f"/workflow/{operation}/dispatch"
                base = f"http://app:8080/api/v1/actions/runs/{run_id}"
                body, headers = signed_request({
                    "protocolVersion": 1, "runId": run_id, "processorId": processor_id,
                    "workspaceId": "workspace-1", "projectId": "project-1", "pageIds": ["page-1"],
                    "secret": "run-secret", "pullUrl": f"{base}/input", "heartbeatUrl": f"{base}/heartbeat", "resultUrl": f"{base}/results",
                }, path)
                response = await client.post(path, content=body, headers=headers)
                assert response.status_code == 200
                assert statuses[-1] == "completed", statuses
    assert uploaded_variants == ["binarized"]
    assert downloaded_images == ["binarize", "region", "line", "ocr"]
    assert "original · COLOR" in selected_images[0]
    assert all("binarized · BINARY" in message for message in selected_images[1:])
    with Image.open(BytesIO(image)) as binary:
        assert set(binary.convert("L").tobytes()) == {0, 255}
    root = ET.fromstring(xml)
    ns = {"p": main.PAGE_NS}
    assert len(root.findall(".//p:TextRegion", ns)) == 1
    assert len(root.findall(".//p:TextLine/p:Baseline", ns)) == 1
    assert len(root.find(".//p:Unicode", ns).text.split()) == 6


@pytest.mark.asyncio
@pytest.mark.parametrize("operation", ["binarize", "region", "line", "ocr"])
async def test_workflow_mocks_require_selected_images(operation: str) -> None:
    context = FakeContext(ActionInput.model_validate({
        "protocolVersion": 1, "runId": "run-1", "processorKey": "mock",
        "projectId": "project-1", "pages": [{"id": "page-1", "name": "Page 1"}],
    }))
    with pytest.raises(ValueError, match=f"Mock {operation} requires a page image"):
        await main.workflow_mock_run(context, operation)  # type: ignore[arg-type]
