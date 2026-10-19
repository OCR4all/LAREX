# LAREX

**Layout Analysis and Recognition** — a collaborative platform for document annotation, OCR correction, and ground-truth datasets. Run extensible Actions for document processing, external integrations, model training, and evaluation.

[![CI](https://github.com/OCR4all/larex/actions/workflows/ci.yml/badge.svg)](https://github.com/OCR4all/larex/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Documentation](https://docs.larex.kallimachos.de) · [Releases](https://github.com/OCR4all/larex/releases) · [Report an issue](https://github.com/OCR4all/larex/issues)

## Get started

- **Deploy:** create a versioned deployment bundle with the [installation wizard](https://docs.larex.kallimachos.de/getting-started/installation).
- **Annotate:** start with [your first project](docs/content/01.getting-started/02.first-project.md), then follow the [user guide](docs/content/02.user-guide/01.overview.md).
- **Automate:** run [LAREX Actions](docs/content/03.actions/01.overview.md) for processing, integration, [training](docs/content/03.actions/09.training.md), and [evaluation](docs/content/03.actions/10.evaluation.md).

> [!IMPORTANT]
> Pre-1.0 databases must be recreated for 1.0. See the [compatibility policy](docs/content/01.getting-started/04.installation.md#database-compatibility).

## Develop

With Docker and Docker Compose v2 installed:

```bash
git clone https://github.com/OCR4all/larex.git
cd larex
docker compose up -d
```

Open [larex.localhost](http://larex.localhost). See the [development guide](docs/content/05.development/02.docker-setup.md), [command reference](docs/content/05.development/03.command-reference.md), and [contributing guidelines](CONTRIBUTING.md).

## License

[MIT](LICENSE) · Centre for Philology and Digitality (ZPD), University of Würzburg.
