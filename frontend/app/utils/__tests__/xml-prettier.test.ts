import { describe, expect, it } from 'vitest'
import { prettyPrintXml } from '@/utils/xml-prettier'

describe('prettyPrintXml', () => {
  it('formats compact PAGE XML with two-space indentation', async () => {
    const source = '<?xml version="1.0" encoding="UTF-8"?><PcGts xmlns="urn:page"><Page><TextRegion><TextEquiv><Unicode>Hello world</Unicode></TextEquiv></TextRegion></Page></PcGts>'

    const result = await prettyPrintXml(source)

    expect(result.formatted).toBe(`<?xml version="1.0" encoding="UTF-8" ?>
<PcGts xmlns="urn:page">
  <Page>
    <TextRegion>
      <TextEquiv>
        <Unicode>Hello world</Unicode>
      </TextEquiv>
    </TextRegion>
  </Page>
</PcGts>
`)
  })

  it('preserves significant text whitespace', async () => {
    const source = '<root><Unicode>  A   B  </Unicode><value xml:space="preserve">  C   D  </value></root>'

    const result = await prettyPrintXml(source)

    expect(result.formatted).toContain('<Unicode>  A   B  </Unicode>')
    expect(result.formatted).toContain('<value xml:space="preserve">  C   D  </value>')
  })

  it('maps the cursor into the formatted document', async () => {
    const source = '<root><child>value</child></root>'
    const cursorOffset = source.indexOf('value') + 2

    const result = await prettyPrintXml(source, cursorOffset)

    expect(result.formatted.slice(result.cursorOffset - 2, result.cursorOffset + 3)).toBe('value')
  })

  it('rejects malformed XML', async () => {
    await expect(prettyPrintXml('<root><child></root>')).rejects.toThrow()
  })
})
