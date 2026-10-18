# LAREX

**Layout Analysis and Recognition** — a collaborative platform for document annotation, OCR correction, and ground-truth datasets. Run extensible Actions for document processing, external integrations, model training, and evaluation.

[![CI](https://github.com/OCR4all/larex/actions/workflows/ci.yml/badge.svg)](https://github.com/OCR4all/larex/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Documentation](https://docs.larex.kallimachos.de) · [Releases](https://github.com/OCR4all/larex/releases) · [Report an issue](https://github.com/OCR4all/larex/issues)

## Get started

- **Deploy:** create a versioned deployment bundle with the [installation wizard](https://docs.larex.kallimachos.de/getting-started/installation).
- **Annotate:** follow the [user guide](docs/content/5.user-guide/1.workspace-library.md).
- **Automate:** run [LAREX Actions](docs/content/6.actions/1.overview.md) for processing, integration, [training](docs/content/6.actions/9.training.md), and [evaluation](docs/content/6.actions/10.evaluation.md).

> [!IMPORTANT]
> Pre-1.0 databases must be recreated for 1.0. See the [compatibility policy](docs/content/1.getting-started/2.installation.md#database-compatibility).

## Develop

With Docker and Docker Compose v2 installed:

```bash
git clone https://github.com/OCR4all/larex.git
cd larex
docker compose up -d
```

Open [larex.localhost](http://larex.localhost). See the [development guide](docs/content/2.development/2.docker-setup.md), [command reference](docs/content/2.development/3.command-reference.md), and [contributing guidelines](CONTRIBUTING.md).

## License

[MIT](LICENSE) · Centre for Philology and Digitality (ZPD), University of Würzburg.
