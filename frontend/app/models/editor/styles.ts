/** Text and paragraph styles */
export class Styles {
  textStyles?: TextStyle[]
  paragraphStyles?: ParagraphStyle[]

  constructor(
    textStyles?: TextStyle[],
    paragraphStyles?: ParagraphStyle[]
  ) {
    this.textStyles = textStyles
    this.paragraphStyles = paragraphStyles
  }

  getTextStyle(id: string): TextStyle | undefined {
    return this.textStyles?.find(s => s.id === id)
  }

  getParagraphStyle(id: string): ParagraphStyle | undefined {
    return this.paragraphStyles?.find(s => s.id === id)
  }

  addTextStyle(style: TextStyle): Styles {
    return new Styles(
      [...(this.textStyles || []), style],
      this.paragraphStyles
    )
  }

  addParagraphStyle(style: ParagraphStyle): Styles {
    return new Styles(
      this.textStyles,
      [...(this.paragraphStyles || []), style]
    )
  }
}

export class TextStyle {
  id: string
  fontFamily?: string
  fontSize?: number
  fontStyle?: ('bold' | 'italic' | 'underline' | 'strikethrough')[]
  color?: string
  letterSpacing?: number
  wordSpacing?: number
  verticalScale?: number
  horizontalScale?: number

  constructor(
    id: string,
    fontFamily?: string,
    fontSize?: number,
    fontStyle?: ('bold' | 'italic' | 'underline' | 'strikethrough')[],
    color?: string,
    letterSpacing?: number,
    wordSpacing?: number,
    verticalScale?: number,
    horizontalScale?: number
  ) {
    this.id = id
    this.fontFamily = fontFamily
    this.fontSize = fontSize
    this.fontStyle = fontStyle
    this.color = color
    this.letterSpacing = letterSpacing
    this.wordSpacing = wordSpacing
    this.verticalScale = verticalScale
    this.horizontalScale = horizontalScale
  }

  hasFontStyle(style: 'bold' | 'italic' | 'underline' | 'strikethrough'): boolean {
    return this.fontStyle?.includes(style) || false
  }
}

export class ParagraphStyle {
  id: string
  align?: 'Left' | 'Right' | 'Center' | 'Block' | 'Justify'
  leftIndent?: number
  rightIndent?: number
  lineSpacing?: number
  firstLineIndent?: number

  constructor(
    id: string,
    align?: 'Left' | 'Right' | 'Center' | 'Block' | 'Justify',
    leftIndent?: number,
    rightIndent?: number,
    lineSpacing?: number,
    firstLineIndent?: number
  ) {
    this.id = id
    this.align = align
    this.leftIndent = leftIndent
    this.rightIndent = rightIndent
    this.lineSpacing = lineSpacing
    this.firstLineIndent = firstLineIndent
  }
}
