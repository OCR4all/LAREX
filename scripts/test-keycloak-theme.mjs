import assert from 'node:assert/strict'
import { execFileSync, spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { test } from 'node:test'
import { auditTheme, packageTheme } from './package-keycloak-theme.mts'

test('packages the real theme with matching metadata and checksums', async () => {
  const temporary = await mkdtemp(join(tmpdir(), 'larex-theme-'))
  try {
    await packageTheme('config/keycloak/theme.jar', temporary, '1.2.3', 'a'.repeat(40))
    const contents = await readFile(join(temporary, 'theme.jar'))
    const digest = createHash('sha256').update(contents).digest('hex')
    assert.equal(await readFile(join(temporary, 'theme.jar.sha256'), 'utf8'), `${digest}  theme.jar\n`)
    const metadata = JSON.parse(await readFile(join(temporary, 'theme.json'), 'utf8'))
    assert.equal(metadata.sha256, digest)
    assert.equal(metadata.source.commit, 'a'.repeat(40))
    assert.equal(metadata.version, '1.2.3')
  } finally {
    await rm(temporary, { recursive: true, force: true })
  }
})

test('rejects Java code, service registrations, nested JARs, and unexpected themes', async () => {
  const temporary = await mkdtemp(join(tmpdir(), 'larex-theme-audit-'))
  try {
    for (const [index, entry] of ['theme/keycloakify-larex/login/Evil.class', 'META-INF/services/evil',
      'theme/keycloakify-larex/login/evil.jar', 'theme/another/login/theme.properties'].entries()) {
      const source = join(temporary, `source-${index}`)
      await mkdir(join(source, entry, '..'), { recursive: true })
      await writeFile(join(source, entry), 'untrusted')
      const jar = join(temporary, `bad-${index}.jar`)
      execFileSync('zip', ['-q', jar, entry], { cwd: source })
      assert.throws(() => auditTheme(jar), /Unexpected theme archive entry/)
    }
  } finally {
    await rm(temporary, { recursive: true, force: true })
  }
})

test('verification restricts publisher and propagates attestation failure', async () => {
  const temporary = await mkdtemp(join(tmpdir(), 'larex-theme-verify-'))
  try {
    const argumentsPath = join(temporary, 'arguments')
    await writeFile(join(temporary, 'gh'), '#!/bin/sh\nprintf "%s\\n" "$@" > "$THEME_TEST_ARGUMENTS"\nexit "$THEME_TEST_EXIT"\n', { mode: 0o755 })
    for (const exitCode of ['0', '1']) {
      const result = spawnSync('bash', ['scripts/verify-keycloak-theme.sh'], {
        env: { ...process.env, PATH: `${temporary}:${process.env.PATH}`,
          THEME_TEST_ARGUMENTS: argumentsPath, THEME_TEST_EXIT: exitCode }
      })
      assert.equal(result.status, Number(exitCode))
      const argumentsText = await readFile(argumentsPath, 'utf8')
      assert.match(argumentsText, /--repo\nocr4all\/larex/)
      assert.match(argumentsText, /--signer-workflow\nocr4all\/larex\/\.github\/workflows\/release.yml/)
      assert.match(argumentsText, /--source-ref\nrefs\/heads\/main/)
    }
  } finally {
    await rm(temporary, { recursive: true, force: true })
  }
})
