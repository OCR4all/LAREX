from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
from datetime import UTC, datetime
from types import SimpleNamespace
from typing import Any

import httpx
import pytest
from larex_actions import ActionFile
from larex_actions.verifier import canonical_dispatch_request

os.environ.setdefault("LAREX_DISPATCH_HMAC_SECRET", "test-dispatch-secret")

import main  # noqa: E402


class FakeContext:
    def __init__(self, action_input: main.TrainingInput) -> None:
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

    async def pull_input(self) -> main.TrainingInput:
        return self.action_input

    async def check_cancelled(self) -> None:
        return None

    async def download_bytes(self, file: ActionFile) -> bytes:
        if file.file_name.endswith(".xml"):
            return b'<PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15"/>'
        return b"image-data"

    async def complete(self, message: str | None = None) -> None:
        self.completed_message = message


def training_input(**overrides: Any) -> main.TrainingInput:
    data: dict[str, Any] = {
        "protocolVersion": 1,
        "runId": "run-1",
        "processorKey": "mock-training",
        "kind": "TRAINING",
        "datasetId": "dataset-1",
        "parameters": {"delaySeconds": 1},
        "pages": [
            main.TrainingPage(
                id="item-1",
                name="page-1",
                sourcePageId="source-page-1",
                split="TRAIN",
                images=[
                    ActionFile(
                        id="image-1",
                        fileName="page-1.png",
                        downloadUrl="http://larex.test/image-1",
                    )
                ],
                xml=[
                    ActionFile(
                        id="xml-1",
                        fileName="page-1.xml",
                        downloadUrl="http://larex.test/xml-1",
                    )
                ],
            )
        ],
    }
    data.update(overrides)
    return main.TrainingInput.model_validate(data)


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


@pytest.mark.asyncio
async def test_training_rejects_items_without_exact_image_xml_pair() -> None:
    action_input = training_input()
    action_input.pages[0].xml = []
    context = FakeContext(action_input)

    with pytest.raises(ValueError, match="exactly one frozen PAGE XML"):
        await main.train_run(context)  # type: ignore[arg-type]


def test_training_delay_is_bounded() -> None:
    with pytest.raises(ValueError, match="between 1 and 30"):
        main._training_delay_seconds(31)


@pytest.mark.asyncio
async def test_training_route_accepts_dataset_dispatch(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    calls: list[str] = []

    async def execute(payload: main.TrainingDispatchPayload) -> None:
        calls.append(payload.run_id)

    monkeypatch.setattr(main, "_execute_training", execute)
    payload = {
        "protocolVersion": 1,
        "runId": "run-1",
        "processorId": "mock-training",
        "workspaceId": "workspace-1",
        "kind": "TRAINING",
        "datasetId": "dataset-1",
        "datasetItemIds": ["item-1"],
        "parameters": {"delaySeconds": 1},
        "secret": "run-secret",
        "pullUrl": "http://app:8080/api/v1/actions/runs/run-1/input",
        "heartbeatUrl": "http://app:8080/api/v1/actions/runs/run-1/heartbeat",
        "resultUrl": "http://app:8080/api/v1/actions/runs/run-1/results",
    }
    body = json.dumps(payload, separators=(",", ":")).encode()
    timestamp = datetime.now(UTC).isoformat().replace("+00:00", "Z")
    nonce = "training-dispatch-nonce"
    body_hash = _b64url(hashlib.sha256(body).digest())
    canonical = canonical_dispatch_request(
        method="POST",
        path_and_query="/training/dispatch",
        run_id="run-1",
        processor_id="mock-training",
        timestamp=timestamp,
        nonce=nonce,
        body_hash=body_hash,
    )
    signature = "v1=" + _b64url(
        hmac.new(b"test-dispatch-secret", canonical.encode(), hashlib.sha256).digest()
    )
    headers = {
        "X-LAREX-Action-Auth": "hmac-sha256;v=1",
        "X-LAREX-Action-Processor": "mock-training",
        "X-LAREX-Action-Run-Id": "run-1",
        "X-LAREX-Action-Timestamp": timestamp,
        "X-LAREX-Action-Nonce": nonce,
        "X-LAREX-Action-Body-SHA256": body_hash,
        "X-LAREX-Action-Signature": signature,
    }

    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=main.app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/training/dispatch", content=body, headers=headers
        )

    assert response.status_code == 200
    assert response.json() == {"status": "accepted", "runId": "run-1"}
    assert calls == ["run-1"]


def _b64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()
