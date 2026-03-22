<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:xs="http://www.w3.org/2001/XMLSchema"
   xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
   xmlns:tei="http://www.tei-c.org/ns/1.0"
   xmlns:local="local"
   xmlns="http://www.tei-c.org/ns/1.0"
   exclude-result-prefixes="#all"
   version="3.0">
   <xd:doc scope="stylesheet">
      <xd:desc>
         <xd:p><xd:b>Created on:</xd:b> 2022-08-03</xd:p>
         <xd:p><xd:b>Author:</xd:b> dario.kampkaspar@tu-darmstadt.de</xd:p>
         <xd:p>combine neighbouring tei:rs[@continued] separated by a tei:lb</xd:p>
      </xd:desc>
   </xd:doc>
   
   <!--<xsl:template match="/">
      <xsl:apply-templates mode="continued" />
   </xsl:template>-->
   
   <xd:doc>
      <xd:desc>
         <xd:p>Combine continued elements (e.g. rs)</xd:p>
         <xd:p>This works on an element that contains (more than one) continued element (there may be just one element
            if the continuation happens across region borders).</xd:p>
      </xd:desc>
   </xd:doc>
   <xsl:template match="tei:*[count(tei:*[@continued = 'true']) gt 1]" mode="continued">
      <xsl:copy>
         <xsl:apply-templates select="@*" mode="continued" />
         <xsl:for-each-group select="node()"
               group-starting-with="tei:*[
                  @continued eq 'true'
                  and normalize-space() != ''
                  and (
                        normalize-space(preceding::text()[1]) != ''
                     or preceding::text()[1][not(preceding-sibling::*)]
                     or preceding-sibling::*[1][not(@continued = 'true') and not(self::tei:lb)]
                     or preceding-sibling::*[not(local-name() = ('pb', 'cb', 'lb'))][1]/local-name() != local-name()
                  )
               ]">
            
            <xsl:choose>
               <xsl:when test="current-group()[1][@continued eq 'true' and tei:abbr]">
                  <!-- we assume there is exactly 2 choice with one lb in between, so no multi-line abbreviations:
                     1=choice, 2=text(), 3=lb, 4=text(), 5=choice -->
                  <choice>
                     <expan>
                        <xsl:sequence select="current-group()[1]/tei:expan/node()" />
                     </expan>
                     <abbr>
                        <xsl:sequence select="current-group()[1]/tei:abbr/node()" />
                        <xsl:sequence select="current-group()[3]" />
                        <xsl:sequence select="current-group()[4]/tei:abbr/node()" />
                     </abbr>
                  </choice>
                  <xsl:apply-templates select="current-group()[position() gt 4]" mode="continued" />
               </xsl:when>
               <xsl:when test="current-group()[1][@continued eq 'true'] and count(current-group()[@continued = 'true']) gt 1">
                  <xsl:variable
                     name="final"
                     select="
                        (
                           current-group()[
                                 position() gt 1
                                 and @continued = 'true'
                                 and node()
                              ][last()],
                           current-group()[4]
                        )[1]"
                  />
                  <xsl:try>
                     <xsl:variable name="last" select="index-of(current-group(), $final)[last()]"/>
                     
                  <xsl:element name="{local-name()}">
                     <xsl:apply-templates select="@*" mode="continued" />
                     <xsl:apply-templates select="current-group()[position() le $last]" mode="rs-continued" />
                  </xsl:element>
                  <xsl:apply-templates select="current-group()[position() gt $last]" mode="continued" />
                     <xsl:catch>
                        <xsl:message select="current-group()" />
                     </xsl:catch>
                  </xsl:try>
               </xsl:when>
               <xsl:otherwise>
                  <xsl:apply-templates select="current-group()" mode="continued" />
               </xsl:otherwise>
            </xsl:choose>
         </xsl:for-each-group>
      </xsl:copy>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>lb will be returned unaltered</xd:desc>
   </xd:doc>
   <xsl:template match="tei:lb" mode="rs-continued">
      <lb>
         <xsl:sequence select="@*" />
      </lb>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>For continued rs, only return the content</xd:desc>
   </xd:doc>
   <xsl:template match="*" mode="rs-continued">
      <xsl:apply-templates select="node()" mode="continued" />
   </xsl:template>
   
   <xd:doc>
      <xd:desc>Remove @continued</xd:desc>
   </xd:doc>
   <xsl:template match="@continued" mode="continued"/>
   
   <xd:doc>
      <xd:desc>Default</xd:desc>
   </xd:doc>
   <xsl:template match="@* | node()" mode="continued">
      <xsl:copy>
         <xsl:apply-templates select="@* | node()" mode="#current" />
      </xsl:copy>
   </xsl:template>
</xsl:stylesheet>