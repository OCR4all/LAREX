# CHANGELOG

<!-- version list -->

## v1.3.0 (2026-11-24)

### Bug Fixes

- **backend**: Keep files of locked pages unchanged in uploads, server imports and normalization
  ([`ff36a99`](https://github.com/OCR4all/LAREX/commit/ff36a9942914678f74db73569d71a1160ec7b4e5))

- **backend**: Keep IIIF images of locked pages unchanged in IIIF imports
  ([`dd3b51f`](https://github.com/OCR4all/LAREX/commit/dd3b51fc6bb110c4313fbdee13c14810a0d39c6c))

- **backend**: Keep the review permission of global admins in upload workers
  ([`ce446a8`](https://github.com/OCR4all/LAREX/commit/ce446a873a07d4cb978c0d71199ed9d561616716))

- **backend**: Require workspace and project access for upload conflicts
  ([`88aafbc`](https://github.com/OCR4all/LAREX/commit/88aafbc03a6f20c03da203b5090f1f2fe6772636))

- **editor**: Show the restore confirmation above the version comparison
  ([`4941309`](https://github.com/OCR4all/LAREX/commit/49413090757f66c37a0e5451edd9f9d66a5605e9))

- **frontend**: Import table cell components so page sort buttons render
  ([`237f7d6`](https://github.com/OCR4all/LAREX/commit/237f7d6eba473cd6d6d17fde2e7d79980e4d3b72))

- **import**: Cover more Transkribus PAGE XML deviations
  ([`0c878d4`](https://github.com/OCR4all/LAREX/commit/0c878d46ab7f2af0b0647ecc8052d12ee95a7986))

### Build System

- **frontend**: Pre-bundle codemirror commands, vue-flow and prettier
  ([`717a1ba`](https://github.com/OCR4all/LAREX/commit/717a1ba7434ececd3ea260bd3e453b869d386696))

### Documentation

- Correct outdated instructions and remove stale content
  ([`65a7e03`](https://github.com/OCR4all/LAREX/commit/65a7e039a031153fe638221bce9886d9f65f56fc))

- **actions**: Describe the download retry window of the SDK
  ([`ba527f0`](https://github.com/OCR4all/LAREX/commit/ba527f0403326dcd2375359db94c0e5d9a43c52c))

- **actions**: List available official and community Actions
  ([`a10144d`](https://github.com/OCR4all/LAREX/commit/a10144dd4884b681c5d88427c6fa4efe53ecc71c))

### Features

- **editor**: Add export, dataset and copy actions for the active page
  ([`c207d2d`](https://github.com/OCR4all/LAREX/commit/c207d2d2fec93a6fc8146c818c700a9303dcd2fb))


## v1.2.0 (2026-11-23)

### Chores

- Fix keycloak theme deps
  ([`2951962`](https://github.com/OCR4all/LAREX/commit/29519621fe56d65a14cf92633fbe4eae8d783e74))

- Update dependencies
  ([`ce18839`](https://github.com/OCR4all/LAREX/commit/ce18839dce43cf99ccfba1b49c52755930520886))

- **frontend**: Bump nuxt test-utils setup version
  ([`109eb94`](https://github.com/OCR4all/LAREX/commit/109eb9412be5d943cc1dcbb88b9fe69f21a55583))

### Continuous Integration

- Target dev branch for dependabot updates
  ([`1f73bf6`](https://github.com/OCR4all/LAREX/commit/1f73bf648c8fc2c76ab47c064702bdcec9b8fdd5))

- **deps**: Target dev, add cooldown and limit dependabot to direct deps
  ([`bd49aae`](https://github.com/OCR4all/LAREX/commit/bd49aaec6601a3cbdc0c7d0e315f7a4120bb25c5))

### Features

- **frontend**: Replace delete confirmations with undo toasts
  ([`b690430`](https://github.com/OCR4all/LAREX/commit/b6904303d7d22a70f08090bd5c18b0198c89106e))

- **import**: Normalize Transkribus PAGE XML exports on import
  ([`194879a`](https://github.com/OCR4all/LAREX/commit/194879a541c0e143d83d9428662d7c7c325173d1))

- **workspace**: Edit settings inline and allow an undefined GT index
  ([`6e4160d`](https://github.com/OCR4all/LAREX/commit/6e4160d017810935551471122e9492acbf5d1adf))

### Testing

- **db**: Replay upgrade seeds from 1.0.0 and check released migrations in CI
  ([`2491368`](https://github.com/OCR4all/LAREX/commit/24913683c66b4fe2a155519ddb4c82fa96000c6d))


## v1.1.1 (2026-11-23)

### Bug Fixes

- **upload**: Keep uploaded temp files unchanged when converting XML so finalize cannot fail the
  session
  ([`e0ff96b`](https://github.com/OCR4all/LAREX/commit/e0ff96b3d5482f24cd8f291f6598e673a4bd73f2))


## v1.1.0 (2026-11-23)

### Bug Fixes

- **datasets**: Keep linked items, task links and search lexicon in sync with source pages and
  projects
  ([`fe9f404`](https://github.com/OCR4all/LAREX/commit/fe9f404bdd679a39fad29a7f48f9da4d6e1384d0))

- **docs**: Add specific host for devServer
  ([`deec7a0`](https://github.com/OCR4all/LAREX/commit/deec7a0c5647344c670944cb8980086136e327c1))

- **editor**: Let entity ID use available status bar width before truncating
  ([`840c5e4`](https://github.com/OCR4all/LAREX/commit/840c5e40e086dda5d101c98c3b61c44f692a8346))

- **keycloak**: Animate dither shader by writing uniforms through the material
  ([`3b1f1c0`](https://github.com/OCR4all/LAREX/commit/3b1f1c01d582d4a1fdc4ecfdbdb18945ea91f19f))

### Build System

- **compose**: Wait for healthy backend and keycloak before starting the dev frontend
  ([`7da357d`](https://github.com/OCR4all/LAREX/commit/7da357dabcd3f7f92f302dc9e9a591516f9500e8))

- **keycloak**: Drop unused dependencies and upgrade to React 19, Vite 8 and Storybook 10
  ([`c52027d`](https://github.com/OCR4all/LAREX/commit/c52027d37b0628fd46ce338523a85c52e6d0009d))

### Chores

- **config**: Bump theme version
  ([`ddd9721`](https://github.com/OCR4all/LAREX/commit/ddd9721ee8d55c2e2efd5776be24bb5b46d14433))

### Continuous Integration

- **keycloak**: Pin bahmutov/npm-install to v1.12.1
  ([`28510f2`](https://github.com/OCR4all/LAREX/commit/28510f229927bcd01781000316696c958c5eaa61))

- **release**: Publish release candidates from dev and retire the next and staging branches
  ([`30e11db`](https://github.com/OCR4all/LAREX/commit/30e11db89c460f95b831f66c7949f1c9bbfabad7))

- **release**: Stamp release versions into the committed theme metadata
  ([`edb0437`](https://github.com/OCR4all/LAREX/commit/edb04373a24651ca41b8520eae866091878acedc))

### Documentation

- Streamline wording
  ([`7658bda`](https://github.com/OCR4all/LAREX/commit/7658bda6126af7a022bb499ae38dfde613ed11df))

### Features

- **annotations**: Record PAGE XML authors and make attribution configurable per workspace
  ([`b4c7cee`](https://github.com/OCR4all/LAREX/commit/b4c7ceed3067718f3848cac874bcb0ce9e337160))

- **editor**: Flag and reveal textline text hidden by index filters
  ([`a31495d`](https://github.com/OCR4all/LAREX/commit/a31495d92502fa46182349b2b55b02ccd3f9c4b6))

- **import**: Import and export FineReader XML and hOCR
  ([`cfda571`](https://github.com/OCR4all/LAREX/commit/cfda571ae48233f9861b0098560d90701dff8ede))

- **import**: Scale physical-unit ALTO to the page image and accept ALTO in all imports
  ([`d7a9642`](https://github.com/OCR4all/LAREX/commit/d7a964231de251adcd8c7720c6d2a69e362d8be7))

- **upload**: Import ALTO by converting it to PAGE XML
  ([`1eb3bf2`](https://github.com/OCR4all/LAREX/commit/1eb3bf2b5261283c34e62ea1b78f624e859539c2))

### Performance Improvements

- **deploy**: Gzip JSON and XML responses in the bundled nginx
  ([`9447e16`](https://github.com/OCR4all/LAREX/commit/9447e1658e94d511cc33986c3bff9e8b448de603))

- **pages**: Load project page lists read-only with tags in one query
  ([`741b38a`](https://github.com/OCR4all/LAREX/commit/741b38a686c48c69d822317e146e5636ff3bc1e8))

- **search**: Skip unused project groups and plan each query for its search term
  ([`8eaeb2a`](https://github.com/OCR4all/LAREX/commit/8eaeb2a269a7e330089a932dbfa223ab326c5146))

### Refactoring

- **keycloak**: Remove unused form and separator components
  ([`a1e4a17`](https://github.com/OCR4all/LAREX/commit/a1e4a17ad2f25ce7864ca79e56c0f98eb9d6a133))

### Testing

- **backend**: Verify upgrades from a seeded 1.0.0 database
  ([`e2b769f`](https://github.com/OCR4all/LAREX/commit/e2b769faeea8d5fcf651f22c9560b4fd7747c15b))


## v1.0.0 (2026-11-20)

- Initial Release
