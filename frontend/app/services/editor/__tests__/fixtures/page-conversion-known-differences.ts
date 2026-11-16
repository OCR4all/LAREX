export const BACKEND_ONLY_FIELDS: string[] = [
  'GlyphDto.text',
  'PageDto.dimensions',
  'PageDto.regionCount',
  'PageDto.textLineCount',
  'RegionDto.text',
  'RegionDto.textRegion',
  'TextLineDto.text',
  'WordDto.text'
]

export const COMPLETE_FIXTURE_DIFFERENCES: string[] = [
  'formatVersion: "2024-07-15" -> absent'
]

export const BACKEND_GOLDEN_DIFFERENCES: Record<string, string[]> = {
  'coverage/2009-03-16': [
    'readingOrder.root.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex13"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2010-01-12': [
    'readingOrder.root.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex13"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2010-03-19': [
    'readingOrder.root.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex12"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2013-07-15': [
    'regions[0].textLines[0].bold: absent -> true',
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].underlined: absent -> true',
    'regions[0].textLines[0].subscript: absent -> true',
    'regions[0].textLines[0].superscript: absent -> true',
    'regions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].textLines[0].letterSpaced: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].bold: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].italic: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlined: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].subscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].superscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].letterSpaced: absent -> true',
    'readingOrder.root.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex12"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2016-07-15': [
    'regions[0].textLines[0].bold: absent -> true',
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].underlined: absent -> true',
    'regions[0].textLines[0].subscript: absent -> true',
    'regions[0].textLines[0].superscript: absent -> true',
    'regions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].textLines[0].letterSpaced: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].bold: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].italic: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlined: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].subscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].superscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].letterSpaced: absent -> true',
    'readingOrder.root.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex12"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2017-07-15': [
    'regions[0].textLines[0].bold: absent -> true',
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].underlined: absent -> true',
    'regions[0].textLines[0].subscript: absent -> true',
    'regions[0].textLines[0].superscript: absent -> true',
    'regions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].textLines[0].letterSpaced: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].bold: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].italic: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlined: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].subscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].superscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].letterSpaced: absent -> true',
    'readingOrder.root.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex12"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex12"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2018-07-15': [
    'regions[0].textLines[0].bold: absent -> true',
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].underlined: absent -> true',
    'regions[0].textLines[0].subscript: absent -> true',
    'regions[0].textLines[0].superscript: absent -> true',
    'regions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].textLines[0].letterSpaced: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].bold: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].italic: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlined: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].subscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].superscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].letterSpaced: absent -> true',
    'readingOrder.root.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex13"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2019-07-15': [
    'regions[0].textLines[0].bold: absent -> true',
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].underlined: absent -> true',
    'regions[0].textLines[0].underlineStyle: absent -> "singleLine"',
    'regions[0].textLines[0].subscript: absent -> true',
    'regions[0].textLines[0].superscript: absent -> true',
    'regions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].textLines[0].letterSpaced: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].bold: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].italic: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlined: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlineStyle: absent -> "singleLine"',
    'regions[0].nestedRegions[0].textLines[0].subscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].superscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].letterSpaced: absent -> true',
    'readingOrder.root.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex13"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'coverage/2024-07-15': [
    'regions[0].textLines[0].bold: absent -> true',
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].underlined: absent -> true',
    'regions[0].textLines[0].underlineStyle: absent -> "singleLine"',
    'regions[0].textLines[0].subscript: absent -> true',
    'regions[0].textLines[0].superscript: absent -> true',
    'regions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].textLines[0].letterSpaced: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].bold: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].italic: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlined: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].underlineStyle: absent -> "singleLine"',
    'regions[0].nestedRegions[0].textLines[0].subscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].superscript: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].strikethrough: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].smallCaps: absent -> true',
    'regions[0].nestedRegions[0].textLines[0].letterSpaced: absent -> true',
    'readingOrder.root.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[1].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[1].group.members[2].group.members[2].group.members[2].group.members[0].id: absent -> "ref-tex13"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-tex13"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/empty-plain-text': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/grapheme-group': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/labels-repeated': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/metadata-without-comments': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/namespace-prefix': [
    'readingOrder.root.members[0].id: absent -> "ref-r1"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/number-formatting': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/reading-order-index': [
    'readingOrder.root.members[0].id: absent -> "ref-r1"',
    'readingOrder.root.members[1].id: absent -> "ref-r2"',
    'readingOrder.root.members[2].group.members[0].id: absent -> "ref-r3"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/relation-to-grapheme': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/relation-without-type': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/relations-same-pair': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/timestamps': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'fidelity/whitespace-references': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/form-region-2024': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/nested-regions': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/reading-order-attributes': [
    'readingOrder.root.members[0].id: absent -> "ref-r1"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-r3"',
    'readingOrder.root.members[1].group.members[1].id: absent -> "ref-r2"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/relations-to-text-objects': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/sparse-larex-export': [
    'readingOrder.root.members[0].id: absent -> "ref-r_a1b2"',
    'readingOrder.root.members[1].id: absent -> "ref-r_c3d4"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/table': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/text-equivs': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/text-style': [
    'regions[0].textLines[0].italic: absent -> true',
    'regions[0].textLines[0].smallCaps: absent -> false',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'larex/whitespace-and-empty-values': [
    'formatVersion: "2024-07-15" -> absent'
  ],
  'real-world/page4j-document': [
    'readingOrder.root.members[0].id: absent -> "ref-r2"',
    'readingOrder.root.members[1].id: absent -> "ref-r1"',
    'readingOrder.root.members[2].id: absent -> "ref-r3"',
    'readingOrder.root.members[3].id: absent -> "ref-r5"',
    'formatVersion: "2024-07-15" -> absent'
  ],
  'real-world/page4j-golden-source-2024': [
    'readingOrder.root.members[0].id: absent -> "ref-r1"',
    'readingOrder.root.members[1].group.members[0].id: absent -> "ref-r3"',
    'readingOrder.root.members[1].group.members[1].group.members[0].id: absent -> "ref-r4"',
    'readingOrder.root.members[1].group.members[1].group.members[1].id: absent -> "ref-r5"',
    'readingOrder.root.members[2].id: absent -> "ref-r6"',
    'formatVersion: "2024-07-15" -> absent'
  ]
}

export const COLLABORATION_DIFFERENCES: Record<string, string[]> = {}
