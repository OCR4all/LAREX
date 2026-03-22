<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:xs="http://www.w3.org/2001/XMLSchema"
   xmlns:tei="http://www.tei-c.org/ns/1.0"
   xmlns:math="http://www.w3.org/2005/xpath-functions/math"
   xmlns="http://www.tei-c.org/ns/1.0"
   exclude-result-prefixes="#all"
   version="3.0">
   
   <xsl:template match="*[tei:hi]" mode="combine-hi">
      <xsl:copy>
         <xsl:sequence select="@*" />
         <xsl:for-each-group select="node()"
               group-starting-with="tei:hi[
                  @style != preceding-sibling::tei:hi[1]/@style
                  or not(preceding-sibling::tei:hi)
                  or normalize-space(string-join(preceding-sibling::tei:hi[1]/following-sibling::node() intersect preceding-sibling::node())) != ''
               ]">
            <xsl:choose>
               <xsl:when test="not(current-group()[self::tei:hi])">
                  <xsl:sequence select="current-group()" />
               </xsl:when>
               <xsl:otherwise>
                  <xsl:variable name="lastHi" select="(index-of(current-group(), current-group()[self::tei:hi][last()]))[last()]"/>
                  
                  <hi style="{current-group()[1]/@style}">
                     <xsl:apply-templates select="current-group()[position() le $lastHi]" mode="do-combine-hi" />
                  </hi>
                  <xsl:sequence select="current-group()[position() gt $lastHi]" />
               </xsl:otherwise>
            </xsl:choose>
         </xsl:for-each-group>
      </xsl:copy>
   </xsl:template>
   
   <xsl:template match="tei:hi" mode="do-combine-hi">
      <xsl:apply-templates mode="combine-hi"/>
   </xsl:template>
   
   <xsl:template match="@* | node()" mode="combine-hi do-combine-hi">
      <xsl:copy>
         <xsl:apply-templates select="@* | node()" mode="#current"/>
      </xsl:copy>
   </xsl:template>
</xsl:stylesheet>
