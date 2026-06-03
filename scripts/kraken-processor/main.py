from __future__ import annotations

import asyncio
import copy
import os
import re
import tempfile
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from larex_actions import ActionCancelled, ActionContext
from larex_actions.fastapi import create_larex_action_app
from lxml import etree
from PIL import Image

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
    try:
        action_input = await ctx.pull_input()
        if not action_input.pages:
            await ctx.complete(ctx.result_builder(), "Kraken segmentation received no pages.")
            return

        results = ctx.result_builder()
        total = len(action_input.pages)

        with tempfile.TemporaryDirectory(prefix="larex-kraken-") as temp_dir:
            work_dir = Path(temp_dir)
            for index, page in enumerate(action_input.pages, start=1):
                await ctx.check_cancelled()
                if not page.images:
                    raise ValueError(f"Page {page.id} does not expose an image input.")

                progress = int(((index - 1) / total) * 95)
                await ctx.heartbeat(progress, f"Segmenting page {index}/{total}: {page.name}", raise_on_cancel=True)

                async with ctx.step(f"Kraken segmentation for {page.name}"):
                    image = page.images[0]
                    image_path = work_dir / safe_file_name(image.file_name, page.name, image.mime_type)
                    output_path = work_dir / f"{safe_stem(page.name or page.id)}.xml"
                    image_bytes = await ctx.download_bytes(image)
                    image_path.write_bytes(image_bytes)

                    if action_input.target_selection and action_input.target_selection.type == "REGION":
                        if not page.xml:
                            raise ValueError(f"Page {page.id} does not expose PAGE XML for scoped region import.")
                        xml_bytes = await ctx.download_bytes(page.xml[0])
                        page_image_size = page_xml_image_size(xml_bytes)
                        target_pages = [
                            target_page
                            for target_page in action_input.target_selection.pages
                            if target_page.page_id == page.id
                        ]
                        for target_page in target_pages:
                            region_ids = list(target_page.region_ids)
                            if not region_ids:
                                raise ValueError(f"Region-targeted run for page {page.id} does not contain region ids.")
                            for region_id in region_ids:
                                await ctx.check_cancelled()
                                region_points = page_xml_region_points(xml_bytes, region_id)
                                crop = crop_target_image(
                                    image_bytes,
                                    region_points,
                                    source_size=page_image_size,
                                )
                                crop_path = work_dir / f"{safe_stem(region_id)}.png"
                                crop_output_path = work_dir / f"{safe_stem(region_id)}.xml"
                                crop_path.write_bytes(crop.content)
                                await run_kraken(ctx, crop_path, crop_output_path)
                                xml_bytes = merge_region_layout_xml(
                                    xml_bytes,
                                    crop_output_path.read_bytes(),
                                    region_id,
                                    crop.offset_x,
                                    crop.offset_y,
                                    crop.scale_x,
                                    crop.scale_y,
                                )
                        results.add_xml_bytes(
                            page_id=page.id,
                            content=xml_bytes,
                            file_name=f"{safe_stem(page.name or page.id)}.xml",
                        )
                        continue

                    await run_kraken(ctx, image_path, output_path)

                    results.add_xml_bytes(
                        page_id=page.id,
                        content=output_path.read_bytes(),
                        file_name=f"{safe_stem(page.name or page.id)}.xml",
                    )

                progress = int((index / total) * 95)
                await ctx.heartbeat(progress, f"Finished segmentation for page {index}/{total}", raise_on_cancel=True)

        await ctx.complete(results, result_message(results))
    except ActionCancelled:
        raise


async def run_kraken(ctx: ActionContext, image_path: Path, output_path: Path) -> None:
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

    result = await ctx.run_subprocess(
        command,
        timeout=MAX_PROCESS_SECONDS,
        terminate_grace_seconds=5.0,
        capture_output=True,
    )

    if result.returncode != 0:
        stderr = result.stderr.decode("utf-8", errors="replace") if result.stderr else ""
        stdout = result.stdout.decode("utf-8", errors="replace") if result.stdout else ""
        output = (stderr or stdout).strip()
        raise RuntimeError(f"Kraken segmentation failed with exit code {result.returncode}: {output}")

    if not output_path.exists() or output_path.stat().st_size == 0:
        raise RuntimeError("Kraken segmentation did not produce PAGE XML output.")


@dataclass(frozen=True)
class TargetCrop:
    content: bytes
    offset_x: float
    offset_y: float
    scale_x: float
    scale_y: float


def crop_target_image(
    image_bytes: bytes,
    points: list[tuple[float, float]],
    *,
    source_size: tuple[int, int] | None = None,
) -> TargetCrop:
    if not points:
        raise ValueError("Target region does not contain polygon coordinates.")

    with Image.open(BytesIO(image_bytes)) as image:
        source_width, source_height = source_size or image.size
        if source_width <= 0 or source_height <= 0:
            raise ValueError("PAGE XML image dimensions are invalid.")

        scale_x = image.width / source_width
        scale_y = image.height / source_height
        source_left = max(0.0, min(point[0] for point in points))
        source_top = max(0.0, min(point[1] for point in points))
        source_right = min(float(source_width), max(point[0] for point in points))
        source_bottom = min(float(source_height), max(point[1] for point in points))

        left = max(0, min(image.width, int(source_left * scale_x)))
        top = max(0, min(image.height, int(source_top * scale_y)))
        right = max(0, min(image.width, int(source_right * scale_x + 0.999)))
        bottom = max(0, min(image.height, int(source_bottom * scale_y + 0.999)))
        if right <= left or bottom <= top:
            raise ValueError(
                "Target region produces an empty crop "
                f"(image={image.width}x{image.height}, source={source_width}x{source_height}, "
                f"bbox={source_left},{source_top},{source_right},{source_bottom})."
            )
        crop = image.crop((left, top, right, bottom))
        output = BytesIO()
        crop.save(output, format="PNG")
        return TargetCrop(
            content=output.getvalue(),
            offset_x=source_left,
            offset_y=source_top,
            scale_x=scale_x,
            scale_y=scale_y,
        )


def page_xml_image_size(xml_bytes: bytes) -> tuple[int, int] | None:
    root = etree.fromstring(xml_bytes)
    page = next((element for element in root.iter() if local_name(element.tag) == "Page"), None)
    if page is None:
        return None
    width = page.get("imageWidth")
    height = page.get("imageHeight")
    if not width or not height:
        return None
    return int(width), int(height)


def page_xml_region_points(xml_bytes: bytes, region_id: str) -> list[tuple[float, float]]:
    root = etree.fromstring(xml_bytes)
    region = find_by_local_name_and_id(root, "TextRegion", region_id)
    if region is None:
        raise ValueError(f"Selected region {region_id} is missing from PAGE XML.")
    coords = next((child for child in region if local_name(child.tag) == "Coords"), None)
    points = coords.get("points") if coords is not None else None
    if not points:
        raise ValueError(f"Selected region {region_id} has no PAGE XML coordinates.")
    return [parse_point_pair(pair) for pair in points.split() if pair.strip()]


def parse_point_pair(value: str) -> tuple[float, float]:
    x_value, y_value = value.split(",", 1)
    return float(x_value), float(y_value)


def merge_region_layout_xml(
    original_xml: bytes,
    layout_xml: bytes,
    region_id: str,
    offset_x: float,
    offset_y: float,
    scale_x: float,
    scale_y: float,
) -> bytes:
    original_root = etree.fromstring(original_xml)
    layout_root = etree.fromstring(layout_xml)
    target_namespace = namespace_uri(original_root.tag)

    target_region = find_by_local_name_and_id(original_root, "TextRegion", region_id)
    if target_region is None:
        raise ValueError(f"Selected region {region_id} is missing from original PAGE XML.")

    existing_text_lines = [child for child in list(target_region) if local_name(child.tag) == "TextLine"]
    for text_line in existing_text_lines:
        target_region.remove(text_line)

    layout_text_lines = [element for element in layout_root.iter() if local_name(element.tag) == "TextLine"]
    insert_at = text_line_insert_index(target_region)
    for index, text_line in enumerate(layout_text_lines, start=1):
        copied = copy.deepcopy(text_line)
        normalize_namespace(copied, target_namespace)
        copied.set("id", f"{region_id}-kraken-line-{index}")
        translate_page_xml_geometry(copied, offset_x, offset_y, scale_x, scale_y)
        target_region.insert(insert_at, copied)
        insert_at += 1

    return etree.tostring(original_root, encoding="utf-8", xml_declaration=True)


def text_line_insert_index(region: etree._Element) -> int:
    for index, child in enumerate(region):
        if local_name(child.tag) in {"TextEquiv", "TextStyle"}:
            return index
    return len(region)


def normalize_namespace(element: etree._Element, namespace: str | None) -> None:
    if namespace:
        for child in element.iter():
            child.tag = f"{{{namespace}}}{local_name(child.tag)}"


def translate_page_xml_geometry(
    element: etree._Element,
    offset_x: float,
    offset_y: float,
    scale_x: float,
    scale_y: float,
) -> None:
    for child in element.iter():
        if local_name(child.tag) not in {"Coords", "Baseline"}:
            continue
        points = child.get("points")
        if not points:
            continue
        child.set(
            "points",
            " ".join(translate_point_pair(pair, offset_x, offset_y, scale_x, scale_y) for pair in points.split()),
        )


def translate_point_pair(
    value: str,
    offset_x: float,
    offset_y: float,
    scale_x: float,
    scale_y: float,
) -> str:
    x_value, y_value = value.split(",", 1)
    return f"{int(round(float(x_value) / scale_x + offset_x))},{int(round(float(y_value) / scale_y + offset_y))}"


def find_by_local_name_and_id(root: etree._Element, name: str, element_id: str) -> etree._Element | None:
    for element in root.iter():
        if local_name(element.tag) == name and element.get("id") == element_id:
            return element
    return None


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if tag.startswith("{") else tag


def namespace_uri(tag: str) -> str | None:
    return tag[1:].split("}", 1)[0] if tag.startswith("{") else None


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
    return f"Kraken segmentation produced {xml_count} PAGE XML file(s)."


app = create_larex_action_app(
    processor_id=PROCESSOR_ID,
    dispatch_secret_env=DISPATCH_SECRET_ENV,
    handler=process_run,
)
