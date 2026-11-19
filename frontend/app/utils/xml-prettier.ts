import type { Options } from 'prettier'
import { format } from 'prettier/standalone'
import xmlPlugin from '@prettier/plugin-xml'

type XmlPrettierOptions = Options & {
  xmlWhitespaceSensitivity: 'preserve'
}

export async function prettyPrintXml(xml: string) {
  const options: XmlPrettierOptions = {
    parser: 'xml',
    plugins: [xmlPlugin],
    tabWidth: 2,
    useTabs: false,
    endOfLine: 'lf',
    xmlWhitespaceSensitivity: 'preserve'
  }

  return format(xml, options)
}
