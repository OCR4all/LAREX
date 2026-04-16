#!/usr/bin/env python3
"""
Small PAT CLI for LAREX public PAT endpoints.

Requirements:
  pip install click

Environment variables:
  LAREX_BASE_URL      e.g. https://larex.example.org
  LAREX_PAT           private access token value
  LAREX_PAT_API_BASE  optional full PAT API root, e.g. https://larex.example.org/api/public/pat

Examples:
  python scripts/pat_cli.py projects
  python scripts/pat_cli.py project <project-id>
  python scripts/pat_cli.py download-project <project-id> --out-dir ./downloads
  python scripts/pat_cli.py download-xml <project-id> <xml-id> --output out.xml
  python scripts/pat_cli.py download-project-xmls <project-id> --out-dir ./downloads
"""

from __future__ import annotations

import json
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    import click
except ModuleNotFoundError as exc:  # pragma: no cover - import guard for user machines
    sys.stderr.write("Missing dependency: click. Install it with `pip install click`.\n")
    raise SystemExit(1) from exc


def _sanitize_segment(value: str | None, fallback: str) -> str:
    if not value:
        return fallback
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", value.strip())
    cleaned = cleaned.strip("._-")
    return cleaned or fallback


def _default_api_base(base_url: str) -> str:
    normalized = base_url.strip().rstrip("/")
    if normalized.endswith("/api/public/pat") or normalized.endswith("/api/v1/public/pat"):
        return normalized
    return f"{normalized}/api/public/pat"


class ApiError(RuntimeError):
    def __init__(self, status: int, message: str, payload: Any | None = None):
        super().__init__(message)
        self.status = status
        self.message = message
        self.payload = payload


@dataclass
class ApiClient:
    api_base: str
    pat: str
    timeout_seconds: int = 60
    insecure_tls: bool = False

    def _ssl_context(self) -> ssl.SSLContext | None:
        if self.insecure_tls:
            return ssl._create_unverified_context()  # noqa: SLF001
        return None

    def _make_url(self, path: str, query: dict[str, Any] | None = None) -> str:
        path = path if path.startswith("/") else f"/{path}"
        url = f"{self.api_base}{path}"
        if query:
            query_items: list[tuple[str, str]] = []
            for key, value in query.items():
                if value is None:
                    continue
                if isinstance(value, (list, tuple)):
                    query_items.extend((key, str(item)) for item in value if item is not None)
                else:
                    query_items.append((key, str(value)))
            if query_items:
                url = f"{url}?{urllib.parse.urlencode(query_items, doseq=True)}"
        return url

    def _request(
        self,
        method: str,
        path: str,
        query: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
        accept: str = "application/json",
    ) -> tuple[int, bytes, str]:
        url = self._make_url(path, query)
        body_bytes = None
        headers = {
            "Authorization": f"Bearer {self.pat}",
            "Accept": accept,
            "User-Agent": "larex-pat-cli/1.0",
        }

        if json_body is not None:
            body_bytes = json.dumps(json_body).encode("utf-8")
            headers["Content-Type"] = "application/json"

        request = urllib.request.Request(
            url=url,
            data=body_bytes,
            headers=headers,
            method=method.upper(),
        )

        try:
            with urllib.request.urlopen(
                request,
                timeout=self.timeout_seconds,
                context=self._ssl_context(),
            ) as response:
                payload = response.read()
                return response.status, payload, response.headers.get_content_type()
        except urllib.error.HTTPError as exc:
            payload = exc.read()
            content_type = exc.headers.get_content_type() if exc.headers else "application/json"
            return exc.code, payload, content_type
        except urllib.error.URLError as exc:
            reason = getattr(exc, "reason", exc)
            message = f"Network error while connecting to {url}: {reason}."
            if self.api_base.startswith("https://larex.localhost"):
                message += " Try --base-url http://larex.localhost if your local setup does not use TLS."
            raise ApiError(0, message) from exc

    def request_json(
        self,
        method: str,
        path: str,
        query: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
    ) -> Any:
        status, payload, _ = self._request(method, path, query=query, json_body=json_body, accept="application/json")
        parsed: Any
        try:
            parsed = json.loads(payload.decode("utf-8")) if payload else None
        except json.JSONDecodeError:
            parsed = payload.decode("utf-8", errors="replace") if payload else None

        if status >= 400:
            message = f"HTTP {status}"
            if isinstance(parsed, dict) and parsed.get("message"):
                message = f"{message}: {parsed['message']}"
            raise ApiError(status, message, parsed)

        return parsed

    def download_file(self, path: str, destination: Path, query: dict[str, Any] | None = None) -> int:
        status, payload, _ = self._request("GET", path, query=query, accept="application/octet-stream")
        if status >= 400:
            parsed: Any
            try:
                parsed = json.loads(payload.decode("utf-8")) if payload else None
            except json.JSONDecodeError:
                parsed = payload.decode("utf-8", errors="replace") if payload else None
            message = f"HTTP {status}"
            if isinstance(parsed, dict) and parsed.get("message"):
                message = f"{message}: {parsed['message']}"
            raise ApiError(status, message, parsed)

        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(payload)
        return len(payload)


def _require_client(ctx: click.Context) -> ApiClient:
    client = ctx.obj
    if not isinstance(client, ApiClient):
        raise click.ClickException("CLI client context not initialized")
    return client


@click.group()
@click.option("--base-url", envvar="LAREX_BASE_URL", help="Base URL of your LAREX instance.")
@click.option(
    "--api-base",
    envvar="LAREX_PAT_API_BASE",
    help="Full PAT API root. Overrides --base-url. Example: https://host/api/public/pat",
)
@click.option("--pat", envvar="LAREX_PAT", required=True, help="Private access token value.")
@click.option("--timeout", default=120, show_default=True, type=int, help="HTTP timeout in seconds.")
@click.option("--insecure", is_flag=True, help="Disable TLS certificate verification.")
@click.pass_context
def cli(ctx: click.Context, base_url: str | None, api_base: str | None, pat: str, timeout: int, insecure: bool) -> None:
    if api_base:
        resolved_api_base = api_base.strip().rstrip("/")
    elif base_url:
        resolved_api_base = _default_api_base(base_url)
    else:
        raise click.UsageError("Provide either --api-base or --base-url (or set LAREX_PAT_API_BASE / LAREX_BASE_URL).")

    ctx.obj = ApiClient(
        api_base=resolved_api_base,
        pat=pat.strip(),
        timeout_seconds=timeout,
        insecure_tls=insecure,
    )


@cli.command("projects")
@click.option("--workspace-id", help="Optional workspace ID filter. Must match token workspace.")
@click.option("--json-output", is_flag=True, help="Print raw JSON.")
@click.pass_context
def list_projects(ctx: click.Context, workspace_id: str | None, json_output: bool) -> None:
    """List projects visible to the PAT workspace."""
    client = _require_client(ctx)
    query = {"workspaceId": workspace_id} if workspace_id else None

    try:
        projects = client.request_json("GET", "/projects", query=query)
    except ApiError as exc:
        raise click.ClickException(exc.message) from exc

    if json_output:
        click.echo(json.dumps(projects, indent=2))
        return

    projects = projects or []
    if not projects:
        click.echo("No projects found for token workspace.")
        return

    click.echo(f"Found {len(projects)} project(s):")
    for project in projects:
        click.echo(
            f"- {project.get('id')} | {project.get('name')} | pages={project.get('pageCount', 0)}"
        )


@cli.command("project")
@click.argument("project_id")
@click.option("--json-output", is_flag=True, help="Print raw JSON.")
@click.pass_context
def project_details(ctx: click.Context, project_id: str, json_output: bool) -> None:
    """Show project detail with pages and available variants."""
    client = _require_client(ctx)

    try:
        project = client.request_json("GET", f"/projects/{project_id}")
    except ApiError as exc:
        raise click.ClickException(exc.message) from exc

    if json_output:
        click.echo(json.dumps(project, indent=2))
        return

    click.echo(f"Project: {project.get('name')} ({project.get('id')})")
    click.echo(f"Workspace: {project.get('workspaceId')}")
    click.echo(f"Pages: {project.get('pageCount', 0)}")
    tags = project.get("tags") or []
    if tags:
        click.echo(f"Tags: {', '.join(tags)}")

    pages = project.get("pages") or []
    if not pages:
        click.echo("No pages in this project.")
        return

    click.echo("")
    click.echo("Pages:")
    for page in pages:
        image_variants = ", ".join(page.get("imageVariants") or [])
        xml_variants = ", ".join(page.get("xmlVariants") or [])
        click.echo(
            f"- {page.get('id')} | {page.get('name')} | "
            f"images={len(page.get('images') or [])} [{image_variants}] | "
            f"xml={len(page.get('xmlFiles') or [])} [{xml_variants}]"
        )


@cli.command("download-xml")
@click.argument("project_id")
@click.argument("xml_id")
@click.option("--output", "output_path", type=click.Path(path_type=Path), help="Output file path.")
@click.option("--overwrite", is_flag=True, help="Overwrite destination if it exists.")
@click.pass_context
def download_xml(ctx: click.Context, project_id: str, xml_id: str, output_path: Path | None, overwrite: bool) -> None:
    """Download one XML file via PAT export endpoint."""
    client = _require_client(ctx)

    if output_path is None:
        output_path = Path("downloads") / _sanitize_segment(project_id, "project") / f"{_sanitize_segment(xml_id, 'xml')}.xml"

    if output_path.exists() and not overwrite:
        raise click.ClickException(f"File already exists: {output_path} (use --overwrite)")

    try:
        size = client.download_file(
            path=f"/projects/{project_id}/pages/xml/{xml_id}/export",
            destination=output_path,
        )
    except ApiError as exc:
        raise click.ClickException(exc.message) from exc

    click.echo(f"Downloaded {xml_id} -> {output_path} ({size} bytes)")


@cli.command("download-image")
@click.argument("project_id")
@click.argument("image_id")
@click.option("--output", "output_path", type=click.Path(path_type=Path), help="Output file path.")
@click.option("--overwrite", is_flag=True, help="Overwrite destination if it exists.")
@click.pass_context
def download_image(ctx: click.Context, project_id: str, image_id: str, output_path: Path | None, overwrite: bool) -> None:
    """Download one image file via PAT export endpoint."""
    client = _require_client(ctx)

    if output_path is None:
        output_path = Path("downloads") / _sanitize_segment(project_id, "project") / f"{_sanitize_segment(image_id, 'image')}.bin"

    if output_path.exists() and not overwrite:
        raise click.ClickException(f"File already exists: {output_path} (use --overwrite)")

    try:
        size = client.download_file(
            path=f"/projects/{project_id}/pages/images/{image_id}/export",
            destination=output_path,
        )
    except ApiError as exc:
        raise click.ClickException(exc.message) from exc

    click.echo(f"Downloaded {image_id} -> {output_path} ({size} bytes)")


@cli.command("download-project")
@click.argument("project_id")
@click.option("--out-dir", default="downloads", show_default=True, type=click.Path(path_type=Path))
@click.option("--page-id", "page_ids", multiple=True, help="Restrict to specific page IDs. Repeatable.")
@click.option("--xml-variant", "xml_variants", multiple=True, help="Restrict XML variants. Repeatable.")
@click.option("--image-variant", "image_variants", multiple=True, help="Restrict image variants. Repeatable.")
@click.option("--include-xml/--no-xml", default=True, show_default=True, help="Include XML files.")
@click.option("--include-images/--no-images", default=True, show_default=True, help="Include image files.")
@click.option("--max-files", type=int, help="Stop after N files.")
@click.option("--overwrite", is_flag=True, help="Overwrite existing files.")
@click.option("--dry-run", is_flag=True, help="Only print what would be downloaded.")
@click.pass_context
def download_project(
    ctx: click.Context,
    project_id: str,
    out_dir: Path,
    page_ids: tuple[str, ...],
    xml_variants: tuple[str, ...],
    image_variants: tuple[str, ...],
    include_xml: bool,
    include_images: bool,
    max_files: int | None,
    overwrite: bool,
    dry_run: bool,
) -> None:
    """Download a project subset (images + XML) using PAT endpoints."""
    if not include_xml and not include_images:
        raise click.ClickException("At least one of --include-xml or --include-images must be enabled.")

    client = _require_client(ctx)

    try:
        project = client.request_json("GET", f"/projects/{project_id}")
    except ApiError as exc:
        raise click.ClickException(exc.message) from exc

    selected_pages = set(page_ids) if page_ids else None
    selected_xml_variants = {variant.strip() for variant in xml_variants if variant.strip()} if xml_variants else None
    selected_image_variants = {variant.strip() for variant in image_variants if variant.strip()} if image_variants else None

    plan: list[tuple[str, str, str, str, Path]] = []
    project_name = _sanitize_segment(project.get("name"), project_id)
    pages = project.get("pages") or []

    for page in pages:
        page_id = page.get("id")
        if not page_id:
            continue
        if selected_pages is not None and page_id not in selected_pages:
            continue

        page_name = _sanitize_segment(page.get("name"), page_id)
        page_dir = out_dir / project_name / page_name

        if include_images:
            for image_file in page.get("images") or []:
                image_id = image_file.get("id")
                if not image_id:
                    continue

                variant = (image_file.get("variant") or "").strip()
                if selected_image_variants is not None and variant not in selected_image_variants:
                    continue

                file_name = _sanitize_segment(image_file.get("fileName"), f"{image_id}.bin")
                destination = page_dir / "images" / file_name
                plan.append(("image", page_id, image_id, variant, destination))

        if include_xml:
            for xml_file in page.get("xmlFiles") or []:
                xml_id = xml_file.get("id")
                if not xml_id:
                    continue

                variant = (xml_file.get("variant") or "").strip()
                if selected_xml_variants is not None and variant not in selected_xml_variants:
                    continue

                file_name = _sanitize_segment(xml_file.get("fileName"), f"{xml_id}.xml")
                destination = page_dir / "xml" / file_name
                plan.append(("xml", page_id, xml_id, variant, destination))

    if max_files is not None and max_files > 0:
        plan = plan[:max_files]

    if not plan:
        click.echo("No files matched the given filters.")
        return

    image_count = sum(1 for kind, *_ in plan if kind == "image")
    xml_count = len(plan) - image_count
    click.echo(f"Planned downloads: {len(plan)} file(s) [images={image_count}, xml={xml_count}]")
    for kind, page_id, file_id, variant, destination in plan:
        click.echo(f"- {kind} page={page_id} id={file_id} variant={variant or 'n/a'} -> {destination}")

    if dry_run:
        return

    downloaded = 0
    skipped = 0
    for kind, _, file_id, _, destination in plan:
        if destination.exists() and not overwrite:
            skipped += 1
            click.echo(f"Skipping existing file: {destination}")
            continue

        endpoint = (
            f"/projects/{project_id}/pages/images/{file_id}/export"
            if kind == "image"
            else f"/projects/{project_id}/pages/xml/{file_id}/export"
        )

        try:
            client.download_file(path=endpoint, destination=destination)
            downloaded += 1
        except ApiError as exc:
            raise click.ClickException(f"Failed on {kind}={file_id}: {exc.message}") from exc

    click.echo(f"Done. Downloaded={downloaded}, skipped={skipped}")


@cli.command("download-project-xmls")
@click.argument("project_id")
@click.option("--out-dir", default="downloads", show_default=True, type=click.Path(path_type=Path))
@click.option("--page-id", "page_ids", multiple=True, help="Restrict to specific page IDs. Repeatable.")
@click.option("--xml-variant", "xml_variants", multiple=True, help="Restrict to XML variants. Repeatable.")
@click.option("--max-files", type=int, help="Stop after N files.")
@click.option("--overwrite", is_flag=True, help="Overwrite existing files.")
@click.option("--dry-run", is_flag=True, help="Only print what would be downloaded.")
@click.pass_context
def download_project_xmls(
    ctx: click.Context,
    project_id: str,
    out_dir: Path,
    page_ids: tuple[str, ...],
    xml_variants: tuple[str, ...],
    max_files: int | None,
    overwrite: bool,
    dry_run: bool,
) -> None:
    """Explore project metadata and download XML exports in batch."""
    client = _require_client(ctx)

    try:
        project = client.request_json("GET", f"/projects/{project_id}")
    except ApiError as exc:
        raise click.ClickException(exc.message) from exc

    selected_pages = set(page_ids) if page_ids else None
    selected_variants = {variant.strip() for variant in xml_variants if variant.strip()} if xml_variants else None

    plan: list[tuple[str, str, str, Path]] = []
    project_name = _sanitize_segment(project.get("name"), project_id)
    pages = project.get("pages") or []

    for page in pages:
        page_id = page.get("id")
        if not page_id:
            continue
        if selected_pages is not None and page_id not in selected_pages:
            continue

        page_name = _sanitize_segment(page.get("name"), page_id)
        for xml_file in page.get("xmlFiles") or []:
            xml_id = xml_file.get("id")
            if not xml_id:
                continue
            variant = (xml_file.get("variant") or "").strip()
            if selected_variants is not None and variant not in selected_variants:
                continue

            file_name = _sanitize_segment(xml_file.get("fileName"), f"{xml_id}.xml")
            destination = out_dir / project_name / page_name / file_name
            plan.append((page_id, xml_id, variant, destination))

    if max_files is not None and max_files > 0:
        plan = plan[:max_files]

    if not plan:
        click.echo("No XML files matched the given filters.")
        return

    click.echo(f"Planned downloads: {len(plan)} file(s)")
    for page_id, xml_id, variant, destination in plan:
        click.echo(f"- page={page_id} xml={xml_id} variant={variant or 'n/a'} -> {destination}")

    if dry_run:
        return

    downloaded = 0
    skipped = 0
    for _, xml_id, _, destination in plan:
        if destination.exists() and not overwrite:
            skipped += 1
            click.echo(f"Skipping existing file: {destination}")
            continue

        try:
            client.download_file(
                path=f"/projects/{project_id}/pages/xml/{xml_id}/export",
                destination=destination,
            )
            downloaded += 1
        except ApiError as exc:
            raise click.ClickException(f"Failed on xml={xml_id}: {exc.message}") from exc

    click.echo(f"Done. Downloaded={downloaded}, skipped={skipped}")


def main() -> None:
    try:
        cli(standalone_mode=True)
    except KeyboardInterrupt:
        click.echo("Interrupted.", err=True)
        sys.exit(130)


if __name__ == "__main__":
    main()
