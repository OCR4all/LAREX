# LAREX

**Layout Analysis and Recognition** — a collaborative platform for document annotation, OCR correction, and ground-truth datasets.

[![CI](https://github.com/OCR4all/LAREX/actions/workflows/ci.yml/badge.svg)](https://github.com/OCR4all/LAREX/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/OCR4all/LAREX)](https://github.com/OCR4all/LAREX/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Documentation](https://docs.larex.kallimachos.de) · [Releases](https://github.com/OCR4all/LAREX/releases) · [Report an issue](https://github.com/OCR4all/LAREX/issues)

LAREX helps teams turn document images into reviewed layout annotations and transcriptions, then reuse that work in exports, datasets, training, and evaluation.

## Features

- **Annotate layout and text:** draw regions, lines, and tables, set the reading order, and correct transcriptions in a PAGE XML editor.
- **Work as a team:** organize workspaces and projects, assign page Tasks, review pages, and restore earlier versions.
- **Import and export:** bring in images, PAGE XML, PDFs, IIIF sources, or LAREX packages, and export PAGE XML, text, PDF, ALTO, TEI, DOCX, CSV, or XLSX.
- **Build datasets:** collect pages, assign splits, and freeze versioned dataset releases.
- **Automate with Actions:** run external processors for layout analysis and recognition, integrations, model training, and evaluation, alone or as workflows.

## Install

LAREX runs with Docker Compose. Create a versioned deployment bundle with the [installation wizard](https://docs.larex.kallimachos.de/getting-started/installation), or download `larex-deployment-<version>.zip` from the [latest release](https://github.com/OCR4all/LAREX/releases/latest) and follow its `INSTALL.md`. See [production deployment](https://docs.larex.kallimachos.de/deployment/production-deploy) for reverse proxies, Keycloak, and backups.

## Use

Start with [your first project](https://docs.larex.kallimachos.de/getting-started/first-project), then follow the [user guide](https://docs.larex.kallimachos.de/user-guide/overview). To process documents automatically, see [LAREX Actions](https://docs.larex.kallimachos.de/actions/overview), including [training](https://docs.larex.kallimachos.de/actions/training) and [evaluation](https://docs.larex.kallimachos.de/actions/evaluation).

## Develop

With Docker and Docker Compose v2 installed:

```bash
git clone https://github.com/OCR4all/LAREX.git
cd LAREX
docker compose up -d
```

Open [larex.localhost](http://larex.localhost). See the [development guide](docs/content/05.development/02.docker-setup.md), [command reference](docs/content/05.development/03.command-reference.md), and [contributing guidelines](CONTRIBUTING.md).

## LAREX 0.7

LAREX 1.0 is a new application. The previous LAREX 0.7 releases remain available on the [`legacy`](https://github.com/OCR4all/LAREX/tree/legacy) branch.

## License

[MIT](LICENSE) · Centre for Philology and Digitality (ZPD), University of Würzburg.
