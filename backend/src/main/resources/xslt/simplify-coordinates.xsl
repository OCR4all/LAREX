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
         <xd:p><xd:b>Created on:</xd:b> 2021-11-08</xd:p>
         <xd:p><xd:b>Author:</xd:b> dario.kampkaspar@tu-darmstadt.de</xd:p>
         <xd:p>evaluate coordinates within @points and turn the bounding rectangle of the polygon</xd:p>
      </xd:desc>
   </xd:doc>
   
   <xd:doc>
      <xd:desc>evaluate coordinates and return bounding rectangle</xd:desc>
   </xd:doc>
   <xsl:template match="tei:zone/@points" mode="bounding-rectangle">
      <xsl:variable name="x" select="local:pointsX(.)" />
      <xsl:variable name="y" select="local:pointsY(.)" />
      <xsl:attribute name="points">
         <xsl:value-of select="min($x) || ',' || min($y)"/>
         <xsl:text> </xsl:text>
         <xsl:value-of select="max($x) || ',' || min($y)"/>
         <xsl:text> </xsl:text>
         <xsl:value-of select="max($x) || ',' || max($y)"/>
         <xsl:text> </xsl:text>
         <xsl:value-of select="min($x) || ',' || max($y)
            "/>
      </xsl:attribute>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>for a polygon, return a sequence of X coordinates, ordered ascending from left to right</xd:desc>
      <xd:param name="pts">the value of @points</xd:param>
   </xd:doc>
   <xsl:function name="local:pointsX" as="xs:integer+">
      <xsl:param name="pts" />
      <xsl:choose>
         <xsl:when test="$pts = ('', 'null,null')">
            <xsl:sequence select="0"/>
         </xsl:when>
         <xsl:otherwise>
            <xsl:for-each select="tokenize($pts, ' ')">
               <xsl:variable name="part" select="substring-before(., ',')" />
               <xsl:value-of select="
                  if ( $part = 'NaN' ) then 0
                  else round(number($part))
               " />
            </xsl:for-each>
         </xsl:otherwise>
      </xsl:choose>
   </xsl:function>
   
   <xd:doc>
      <xd:desc>for a polygon, return a sequence of Y coordinates, ordered ascending from top to bottom</xd:desc>
      <xd:param name="pts">the value of @points</xd:param>
   </xd:doc>
   <xsl:function name="local:pointsY" as="xs:integer+">
      <xsl:param name="pts" />
      <xsl:choose>
         <xsl:when test="$pts = ('', 'null,null')">
            <xsl:sequence select="0"/>
         </xsl:when>
         <xsl:otherwise>
            <xsl:variable name="vals" select="tokenize($pts, ' ')"/>
            <xsl:for-each select="$vals">
               <xsl:variable name="part" select="substring-after(., ',')" />
               <xsl:value-of select="
                  if ( $part = 'NaN' ) then 0
                  else round(number($part))
                  " />
            </xsl:for-each>
         </xsl:otherwise>
      </xsl:choose>
   </xsl:function>
   
   <xd:doc>
      <xd:desc>Default</xd:desc>
   </xd:doc>
   <xsl:template match="@* | node()" mode="bounding-rectangle">
      <xsl:copy>
         <xsl:apply-templates select="@* | node()" mode="#current" />
      </xsl:copy>
   </xsl:template>
   
</xsl:stylesheet>