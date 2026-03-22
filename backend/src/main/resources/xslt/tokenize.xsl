<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:xs="http://www.w3.org/2001/XMLSchema"
   xmlns:tei="http://www.tei-c.org/ns/1.0"
   xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
   xmlns="http://www.tei-c.org/ns/1.0"
   exclude-result-prefixes="#all"
   version="3.0">
   <xd:doc scope="stylesheet">
      <xd:desc>
         <xd:p><xd:b>Created on:</xd:b> Jan 11, 2023</xd:p>
         <xd:p><xd:b>Author:</xd:b> Dario Kampkaspar (dario.kampkaspar@tu-darmstadt.de)</xd:p>
         <xd:p></xd:p>
      </xd:desc>
   </xd:doc>
   
   <xsl:variable name="hyphens" select="('=', '-', '¬', '⸗')" />
   <xsl:variable name="quotationMarks" select="('„', '“', '”', '‚', '‘', '’', '»', '«', '›', '‹')" />
   <xsl:variable name="punctuationCharacters"
      select="'[' || $hyphens => string-join() => replace('\-', '\\-') || string-join($quotationMarks) || '\.,;:–—\?!\[\]\(\)\*/〈〉¿…]'"/>
   
   <xd:doc>
      <xd:desc>Tokenize elements within a div if they contain text</xd:desc>
   </xd:doc>
   <xsl:template match="*" mode="tokenize">
      <xsl:copy>
         <xsl:sequence select="@*" />
         <xsl:variable name="content">
            <xsl:apply-templates mode="doTokenize" />
         </xsl:variable>
         
         <xsl:apply-templates select="$content" mode="combine-tokens"/>
      </xsl:copy>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>Basic white space tokenisation: it’s a word if it’s not a pc or a num</xd:desc>
   </xd:doc>
   <xsl:template match="text()[normalize-space() != '']" mode="doTokenize" priority="2">
      <xsl:analyze-string select="." regex="\s+">
         <xsl:matching-substring>
            <xsl:sequence select="." />
         </xsl:matching-substring>
         <xsl:non-matching-substring>
            <xsl:analyze-string select="." regex="{$punctuationCharacters}">
               <xsl:matching-substring>
                  <pc><xsl:sequence select="." /></pc>
               </xsl:matching-substring>
               <xsl:non-matching-substring>
                  <xsl:analyze-string select="." regex="\d+">
                     <xsl:matching-substring>
                        <num><xsl:sequence select="." /></num>
                     </xsl:matching-substring>
                     <xsl:non-matching-substring>
                        <w><xsl:sequence select="." /></w>
                     </xsl:non-matching-substring>
                  </xsl:analyze-string>
               </xsl:non-matching-substring>
            </xsl:analyze-string>
         </xsl:non-matching-substring>
      </xsl:analyze-string>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>evaluate tei:w and its surroundings to see whether there is hyphenation</xd:desc>
   </xd:doc>
   <xsl:template match="tei:w" mode="combine-tokens" priority="2">
      <!-- collect the nodes between this and the preceding word; will be used later to “restore” the content between
         words as the calling template only evaluates tei:w. -->
      <xsl:variable name="preceding">
         <xsl:choose>
            <xsl:when test="preceding-sibling::tei:w">
               <xsl:apply-templates select="preceding-sibling::node()
                     intersect preceding-sibling::tei:w[1]/following-sibling::node()" mode="combine-tokens-hi" />
            </xsl:when>
            <xsl:otherwise>
               <xsl:apply-templates select="preceding-sibling::node()" mode="combine-tokens-hi" />
            </xsl:otherwise>
         </xsl:choose>
      </xsl:variable>
      
      <xsl:choose>
         <!-- Hyphenation: exactly one hyphen follows immediately, and after the breaks, the next tei:w starts with a
            lower case letter; to avoid errors with a German speciality, this word must not be “und” or “oder” -->
         <xsl:when test="following-sibling::node()[1] = $hyphens
               and following-sibling::*[2][local-name() = ('pb', 'cb', 'lb')]
               and following-sibling::tei:w[1][matches(., '^[a-zäöüß]') and . != 'und' and . != 'oder']">
            <xsl:sequence select="$preceding" />
            <xsl:if test="preceding-sibling::node()[1][self::text()]">
               <xsl:text>
               </xsl:text>
            </xsl:if>
            <w>
               <xsl:sequence select="node()" />
               <xsl:apply-templates select="following-sibling::* intersect following-sibling::tei:w[1]/preceding-sibling::*" mode="break" />
               <xsl:sequence select="following-sibling::tei:w[1]/node()" />
            </w>
         </xsl:when>
         <!-- after the breaks, the next tei:w starts with an upper case letter. This is not hyphenation but a long word
            with hyphens (e.g. German “Durchkoppelung”). We handle this separately so we can add some formatting. -->
         <xsl:when test="following-sibling::node()[1] = $hyphens
               and following-sibling::*[2][local-name() = ('pb', 'cb', 'lb')]
               and following-sibling::tei:w[1][matches(., '^[A-ZÄÖÜ]')]">
            <xsl:sequence select="$preceding" />
            <xsl:if test="preceding-sibling::node()[1][self::text()]">
               <xsl:text>
               </xsl:text>
            </xsl:if>
            <xsl:sequence select="." />
            <xsl:sequence select="following-sibling::* intersect following-sibling::tei:w[1]/preceding-sibling::*" />
            <xsl:sequence select="following-sibling::tei:w[1]" />
         </xsl:when>
         <!-- after the breaks, the next tei:w is „und“ or „oder“ -->
         <xsl:when test="following-sibling::node()[1] = $hyphens
               and following-sibling::*[2][local-name() = ('pb', 'cb', 'lb')]
               and following-sibling::tei:w[1] = ('und', 'oder')">
            <xsl:sequence select="$preceding" />
            <xsl:sequence select="." />
            <xsl:sequence select="following-sibling::*[1]" />
            <xsl:text>
               </xsl:text>
            <xsl:sequence select="following-sibling::*[1]/following-sibling::* intersect following-sibling::tei:w[1]/preceding-sibling::*" />
            <xsl:sequence select="following-sibling::tei:w[1]" />
         </xsl:when>
         <!-- second part of a hyphenated word. If this is the last word in its parent, restore the following nodes, if
            any (so as to not lose punctuation) -->
         <xsl:when test="preceding-sibling::node()[1][self::tei:lb]
               and preceding-sibling::tei:w[1]/following-sibling::node()[1] = $hyphens
               and count(preceding-sibling::tei:pc intersect preceding-sibling::tei:w[1]/following-sibling::*) = 1">
            <xsl:if test="not(following-sibling::tei:w)">
               <xsl:sequence select="following-sibling::node()" />
            </xsl:if>
         </xsl:when>
         <xsl:otherwise>
            <xsl:sequence select="$preceding" />
            <xsl:sequence select="." />
            <xsl:if test="not(following-sibling::tei:w)">
               <xsl:sequence select="following-sibling::node()" />
            </xsl:if>
         </xsl:otherwise>
      </xsl:choose>
   </xsl:template>
   
   
   <xsl:template match="tei:hi" mode="combine-tokens-hi">
      <hi>
         <xsl:sequence select="@*" />
         <xsl:apply-templates mode="combine-tokens" />
      </hi>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>If a node has a sibling tei:w, it is handled by the previous template</xd:desc>
   </xd:doc>
   <xsl:template match="node()[../tei:w]" mode="combine-tokens" />
   
   <xd:doc>
      <xd:desc>add @break="no" to lb if there was hyphenation</xd:desc>
   </xd:doc>
   <xsl:template match="tei:lb" mode="break">
      <lb>
         <xsl:sequence select="@*" />
         <xsl:attribute name="break">no</xsl:attribute>
      </lb>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>add @break="no" to lb if there was hyphenation</xd:desc>
   </xd:doc>
   <xsl:template match="tei:cb" mode="break">
      <cb>
         <xsl:sequence select="@*" />
         <xsl:attribute name="break">no</xsl:attribute>
      </cb>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>Default</xd:desc>
   </xd:doc>
   <xsl:template match="@* | node()" mode="doTokenize combine-tokens combine-tokens-hi break">
      <xsl:copy>
         <xsl:apply-templates select="@* | node()" mode="#current" />
      </xsl:copy>
   </xsl:template>
</xsl:stylesheet>
