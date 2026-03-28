export const NORMALIZATION_PRESET_RULE_HELP = {
  unicodeNormalization: {
    title: 'Unicode normalization',
    description: 'Applies the selected Unicode normalization form before the other normalization steps run.',
    details: [
      'NFC and NFD use canonical normalization.',
      'NFKC and NFKD also fold compatibility characters.',
      'Set the profile to NONE to skip this step completely.'
    ]
  },
  collapseWhitespace: {
    title: 'Collapse whitespace',
    description: 'Replaces every run of whitespace, including tabs and line breaks, with a single space.',
    details: [
      'Example: `foo   bar` becomes `foo bar`.',
      'Example: `foo\\nbar` becomes `foo bar`.'
    ]
  },
  trimText: {
    title: 'Trim text',
    description: 'Removes leading and trailing whitespace after the other normalization steps have finished.',
    details: [
      'Example: `  text  ` becomes `text`.'
    ]
  },
  dehyphenateLineBreaks: {
    title: 'Dehyphenate line breaks',
    description: 'Removes a hyphen or dash followed by a line break when it joins letters or digits across lines.',
    details: [
      'Example: `Line-\\nbreak` becomes `Linebreak`.',
      'This only applies when the break is between alphanumeric characters.'
    ]
  },
  mapLongSToS: {
    title: 'Map long s to s',
    description: 'Replaces the historical long-s forms used in early print with a regular `s`.',
    details: [
      'Characters covered: `ſ`, `ẜ`.'
    ]
  },
  expandCommonLigatures: {
    title: 'Expand common ligatures',
    description: 'Expands common typographic ligature characters into their plain-letter equivalents.',
    details: [
      'Characters covered: `ﬀ`, `ﬁ`, `ﬂ`, `ﬃ`, `ﬄ`, `ﬅ`, `ﬆ`.'
    ]
  },
  normalizeQuotes: {
    title: 'Normalize quotes',
    description: 'Converts curly and angled quotation marks to straight single or double quotes.',
    details: [
      'Double quotes: `“ ” „ ‟ « »` -> `"`.',
      'Single quotes: `‘ ’ ‚ ‛` -> `\'`.'
    ]
  },
  normalizeDashes: {
    title: 'Normalize dashes',
    description: 'Converts dash variants to a regular hyphen-minus.',
    details: [
      'Characters covered: `‐`, `‑`, `‒`, `–`, `—`.'
    ]
  },
  normalizeEllipsis: {
    title: 'Normalize ellipsis',
    description: 'Converts the ellipsis character to three literal periods.',
    details: [
      'Example: `…` becomes `...`.'
    ]
  }
} as const

export type NormalizationPresetRuleKey = keyof typeof NORMALIZATION_PRESET_RULE_HELP
