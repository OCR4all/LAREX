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


def test_sdk_rejects_items_without_exact_image_xml_pair() -> None:
    data = training_input().model_dump(by_alias=True)
    data["pages"][0]["xml"] = []
    with pytest.raises(ValueError, match="exactly one image and one XML"):
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


@pytest.mark.parametrize("prefix,processor_id,kind,profile", PROCESSORS)
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
