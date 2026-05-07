from __future__ import annotations

import asyncio
import os

from larex_actions import ActionContext, ResultFile
from larex_actions.fastapi import create_larex_action_app

PROCESSOR_ID = os.getenv("LAREX_PROCESSOR_ID", "mock-image-copy")
DISPATCH_SECRET_ENV = "LAREX_DISPATCH_HMAC_SECRET"
HEARTBEAT_COUNT = int(os.getenv("LAREX_HEARTBEAT_COUNT", "4"))
HEARTBEAT_DELAY_SECONDS = float(os.getenv("LAREX_HEARTBEAT_DELAY_SECONDS", "1"))
OUTPUT_IMAGE_VARIANT = os.getenv("LAREX_OUTPUT_IMAGE_VARIANT", "action-copy")
OUTPUT_XML_VARIANT = os.getenv("LAREX_OUTPUT_XML_VARIANT", "action-copy")


async def process_run(ctx: ActionContext) -> None:
    action_input = await ctx.pull_input()

    for index in range(HEARTBEAT_COUNT):
        progress = int(((index + 1) / (HEARTBEAT_COUNT + 1)) * 90)
        await ctx.heartbeat(
            progress,
            f"Mock processing heartbeat {index + 1}/{HEARTBEAT_COUNT}",
            raise_on_cancel=True,
        )
        await asyncio.sleep(HEARTBEAT_DELAY_SECONDS)

    results = ctx.result_builder()
    for page in action_input.pages:
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

        if page.xml:
            xml = page.xml[0]
            xml_bytes = await ctx.download_bytes(xml)
            results.add_xml_bytes(
                page_id=page.id,
                content=xml_bytes,
                file_name=xml.file_name or f"{page.name}-{OUTPUT_XML_VARIANT}.xml",
                variant=OUTPUT_XML_VARIANT,
            )

    await ctx.complete(results, result_message(results.files))


def result_message(files: list[ResultFile]) -> str:
    image_count = sum(1 for file in files if file.type == "image")
    xml_count = sum(1 for file in files if file.type == "xml")
    return f"Mock processor copied {image_count} image(s) and {xml_count} XML file(s)."


app = create_larex_action_app(
    processor_id=PROCESSOR_ID,
    dispatch_secret_env=DISPATCH_SECRET_ENV,
    handler=process_run,
)
