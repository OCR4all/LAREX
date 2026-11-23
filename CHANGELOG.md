# CHANGELOG

<!-- version list -->

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
