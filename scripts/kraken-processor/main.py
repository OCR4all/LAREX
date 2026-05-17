from __future__ import annotations

import asyncio
import os
import re
import tempfile
from pathlib import Path

from larex_actions import ActionContext
from larex_actions.fastapi import create_larex_action_app

PROCESSOR_ID = os.getenv("LAREX_PROCESSOR_ID", "kraken-segmentation")
DISPATCH_SECRET_ENV = "LAREX_DISPATCH_HMAC_SECRET"
DEVICE = os.getenv("KRAKEN_DEVICE", "cpu")
PRECISION = os.getenv("KRAKEN_PRECISION", "32-true")
TEXT_DIRECTION = os.getenv("KRAKEN_TEXT_DIRECTION", "horizontal-lr")
SEGMENTATION_MODEL = os.getenv("KRAKEN_SEGMENTATION_MODEL", "").strip()
MAX_PROCESS_SECONDS = int(os.getenv("KRAKEN_MAX_PROCESS_SECONDS", "900"))

IMAGE_EXTENSIONS = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/tiff": ".tif",
    "image/tif": ".tif",
    "image/webp": ".webp",
    "image/bmp": ".bmp",
}


async def process_run(ctx: ActionContext) -> None:
    action_input = await ctx.pull_input()
    if not action_input.pages:
        await ctx.complete(ctx.result_builder(), "Kraken segmentation received no pages.")
        return

    results = ctx.result_builder()
    total = len(action_input.pages)

    with tempfile.TemporaryDirectory(prefix="larex-kraken-") as temp_dir:
        work_dir = Path(temp_dir)
        for index, page in enumerate(action_input.pages, start=1):
            if not page.images:
                raise ValueError(f"Page {page.id} does not expose an image input.")

            progress = int(((index - 1) / total) * 95)
            await ctx.heartbeat(progress, f"Segmenting page {index}/{total}: {page.name}", raise_on_cancel=True)

            async with ctx.step(f"Kraken segmentation for {page.name}"):
                image = page.images[0]
                image_path = work_dir / safe_file_name(image.file_name, page.name, image.mime_type)
                output_path = work_dir / f"{safe_stem(page.name or page.id)}.xml"
                image_path.write_bytes(await ctx.download_bytes(image))

                await run_kraken(image_path, output_path)

                if action_input.target_selection and action_input.target_selection.type == "REGION":
                    results.add_layout_xml_bytes(
                        page_id=page.id,
                        content=output_path.read_bytes(),
                        file_name=f"{safe_stem(page.name or page.id)}.xml",
                    )
                else:
                    results.add_xml_bytes(
                        page_id=page.id,
                        content=output_path.read_bytes(),
                        file_name=f"{safe_stem(page.name or page.id)}.xml",
                    )

            progress = int((index / total) * 95)
            await ctx.heartbeat(progress, f"Finished segmentation for page {index}/{total}", raise_on_cancel=True)

    await ctx.complete(results, result_message(results))


async def run_kraken(image_path: Path, output_path: Path) -> None:
    command = [
        "kraken",
        "-x",
        "-i",
        str(image_path),
        str(output_path),
        "--device",
        DEVICE,
        "--precision",
        PRECISION,
        "segment",
        "-bl",
        "-d",
        TEXT_DIRECTION,
    ]
    if SEGMENTATION_MODEL:
        command.extend(["-i", SEGMENTATION_MODEL])

    process = await asyncio.create_subprocess_exec(
        *command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        stdout, stderr = await asyncio.wait_for(process.communicate(), timeout=MAX_PROCESS_SECONDS)
    except asyncio.TimeoutError as exc:
        process.kill()
        await process.communicate()
        raise TimeoutError(f"Kraken segmentation exceeded {MAX_PROCESS_SECONDS} seconds") from exc

    if process.returncode != 0:
        output = (stderr or stdout).decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"Kraken segmentation failed with exit code {process.returncode}: {output}")

    if not output_path.exists() or output_path.stat().st_size == 0:
        raise RuntimeError("Kraken segmentation did not produce PAGE XML output.")


def safe_file_name(file_name: str | None, page_name: str | None, mime_type: str | None) -> str:
    source = file_name or page_name or "page"
    name = Path(source).name
    stem = safe_stem(Path(name).stem or "page")
    suffix = Path(name).suffix.lower()
    if not suffix:
        suffix = IMAGE_EXTENSIONS.get((mime_type or "").lower(), ".png")
    return f"{stem}{suffix}"


def safe_stem(value: str) -> str:
    stem = re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("._-")
    return stem[:96] or "page"


def result_message(results) -> str:
    xml_count = sum(1 for file in results.files if file.type == "xml")
    patch_count = len(results.patches)
    return f"Kraken segmentation produced {xml_count} PAGE XML file(s) and {patch_count} layout patch(es)."


app = create_larex_action_app(
    processor_id=PROCESSOR_ID,
    dispatch_secret_env=DISPATCH_SECRET_ENV,
    handler=process_run,
)
