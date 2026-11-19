import { describe, expect, it } from 'vitest'
import { prettyPrintXml } from '@/utils/xml-prettier'

describe('prettyPrintXml', () => {
  it('formats compact PAGE XML with two-space indentation', async () => {
    const source = '<?xml version="1.0" encoding="UTF-8"?><PcGts xmlns="urn:page"><Page><TextRegion><TextEquiv><Unicode>Hello world</Unicode></TextEquiv></TextRegion></Page></PcGts>'

    const formatted = await prettyPrintXml(source)

    expect(formatted).toBe(`<?xml version="1.0" encoding="UTF-8" ?>
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

    const formatted = await prettyPrintXml(source)

    expect(formatted).toContain('<Unicode>  A   B  </Unicode>')
    expect(formatted).toContain('<value xml:space="preserve">  C   D  </value>')
  })

  it('rejects malformed XML', async () => {
    await expect(prettyPrintXml('<root><child></root>')).rejects.toThrow()
  })
})
