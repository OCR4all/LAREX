import type { CursorOptions } from 'prettier'
import { formatWithCursor } from 'prettier/standalone'
import xmlPlugin from '@prettier/plugin-xml'

type XmlPrettierOptions = CursorOptions & {
  xmlWhitespaceSensitivity: 'preserve'
}

export async function prettyPrintXml(xml: string, cursorOffset = 0) {
  const options: XmlPrettierOptions = {
    parser: 'xml',
    plugins: [xmlPlugin],
    cursorOffset,
    tabWidth: 2,
    useTabs: false,
    endOfLine: 'lf',
    xmlWhitespaceSensitivity: 'preserve'
  }

  return formatWithCursor(xml, options)
}
