<?xml version="1.0" encoding="UTF-8"?><xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
   xmlns="http://www.tei-c.org/ns/1.0" xmlns:tei="http://www.tei-c.org/ns/1.0"
   xmlns:p="http://schema.primaresearch.org/PAGE/gts/pagecontent/2013-07-15"
   xmlns:pc="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15"
   xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink"
   xmlns:map="http://www.w3.org/2005/xpath-functions/map" xmlns:local="local"
   xmlns:xstring="https://github.com/dariok/XStringUtils" exclude-result-prefixes="#all"
   version="3.0">

   <xsl:output indent="0"/>
   
   <xsl:variable name="langs" select="map { 'French': 'fr', 'German': 'de', 'English': 'en', 'Latin': 'la',
      'Spanish': 'es', 'Ancient Greek': 'grc' }"/>

   <xsl:include href="combine-hi.xsl" />
   
   <xd:doc>
      <xd:desc>Page XML parameter 'PAGEXML' with which ID and USE definition is referenced in the mets:fileGrp to the PAGE xml files.</xd:desc>
   </xd:doc>
   <xsl:param name="PAGEXML" select="'PAGEXML'" />

   <xd:doc>
      <xd:desc>Whether to create `rs type="..."` for person/place/org (default) or `persName` etc.
         (false())</xd:desc>
   </xd:doc>
   <xsl:param name="rs" select="true()"/>

   <xd:doc>
      <xd:desc>Whether to run white space tokenization</xd:desc>
   </xd:doc>
   <xsl:param name="tokenize" select="false()"/>
   <xsl:include href="tokenize.xsl" />
   
   <xd:doc>
      <xd:desc>Whether to combine entities over line breaks</xd:desc>
   </xd:doc>
   <xsl:param name="combine" select="false()"/>
   <xsl:include href="combine-continued.xsl" />

   <xd:doc>
      <xd:desc>If false(), region types that correspond to valid TEI elements will be returned as
         this element; types that do not correspond to a TEI element will be returned as
         tei:ab[@type]. If set to true(), all region types (except for paragraph, heading) will be
         returned as tei:ab.</xd:desc>
   </xd:doc>
   <xsl:param name="ab" select="false()"/>

   <xd:doc>
      <xd:desc>If true(), export the (estimated) word coordinates to the facsimile section. Default:
         false().</xd:desc>
   </xd:doc>
   <xsl:param name="word-coordinates" select="false()"/>

   <xd:doc>
      <xd:desc>Whether to create bounding rectangles from polygons (default: true())</xd:desc>
   </xd:doc>
   <xsl:param name="bounding-rectangles" select="true()"/>
   <xsl:include href="simplify-coordinates.xsl" />

   <xd:doc>
      <xd:desc>Whether to export lines without baseline (true()) or not (false(), default)</xd:desc>
   </xd:doc>
   <xsl:param name="withoutBaseline" select="false()"/>

   <xd:doc>
      <xd:desc>Whether to export regions without text lines (true()) or not (false(),
         default)</xd:desc>
   </xd:doc>
   <xsl:param name="withoutTextline" select="false()"/>
   
   <xd:doc>
      <xd:desc>Whether to export custom attributes from tags that we do not know how to convert to valid TEI (true(),
         default) or whether to discard them (false()).</xd:desc>
   </xd:doc>
   <xsl:param name="unknownAttributes" select="true()" />

   <xd:doc scope="stylesheet">
      <xd:desc>
         <xd:p><xd:b>Author:</xd:b> Dario Kampkaspar, dario.kampkaspar@oeaw.ac.at |
            dario.kampkaspar@tu-darmstadt.de</xd:p>
         <xd:p>Austrian Centre for Digital Humanities http://acdh.oeaw.ac.at | University and State
            Library Darmstadt https://ulb.tu-darmstadt.de</xd:p>
         <xd:p/>
         <xd:p>This stylesheet, when applied to mets.xml of the PAGE output, will create (valid)
            TEI</xd:p>
         <xd:p>While this XSLT is designed to run on many different flavours of PAGE-XMLs described
            by a common mets.xml file, some special care was taken to include meta data provided by
            Transkribus; other meta data providers may be included if examples are provided.</xd:p>
         <xd:p/>
         <xd:p><xd:b>Contributor</xd:b> Matthias Boenig, github:@tboenig</xd:p>
         <xd:p>OCR-D, Berlin-Brandenburg Academy of Sciences and Humanities
            http://ocr-d.de/eng</xd:p>
         <xd:p>extend the original XSL-Stylesheet by specific elements based on the @typing of the
            text region</xd:p>
         <xd:p/>
         <xd:p><xd:b>Contributor</xd:b> Peter Stadler, github:@peterstadler</xd:p>
         <xd:p>Carl-Maria-von-Weber-Gesamtausgabe</xd:p>
         <xd:p>Added corrections to tei:sic/tei:corr</xd:p>
         <xd:p/>
         <xd:p><xd:b>Contributor</xd:b> Till Grallert, github:@tillgrallert</xd:p>
         <xd:p>Orient-Institut Beirut</xd:p>
         <xd:p>Use tei:ab as fallback instead of tei:p</xd:p>
      </xd:desc>
   </xd:doc>

   <!-- use extended string functions from https://github.com/dariok/XStringUtils -->
   <xsl:include href="string-pack.xsl"/>

   <xsl:param name="debug" select="false()"/>

   <xd:doc>
      <xd:desc>Entry – note: we also allow execution on a singe PAGE file</xd:desc>
   </xd:doc>
   <xsl:template match="/">
      <xsl:apply-templates select="mets:mets" />
      <xsl:apply-templates select="*/p:Page | */pc:Page" mode="facsimile">
         <xsl:with-param name="numCurr" tunnel="1" select="*/*:Metadata/*:TranskribusMetadata/@pageNr" />
      </xsl:apply-templates>
      <xsl:apply-templates select="*/p:Page | */pc:Page" mode="text">
         <xsl:with-param name="numCurr" tunnel="1" select="*/*:Metadata/*:TranskribusMetadata/@pageNr" />
      </xsl:apply-templates>
   </xsl:template>

   <xd:doc>
      <xd:desc>helper: gather page contents</xd:desc>
   </xd:doc>
   <xsl:variable name="make_div">
      <div>
         <xsl:apply-templates select="//mets:fileSec//mets:fileGrp[(@ID, @USE) = $PAGEXML]/mets:file" mode="text" />
      </div>
   </xsl:variable>
   
   <xd:doc>
      <xd:desc>Entry point: start at the top of METS.xml</xd:desc>
   </xd:doc>
   <xsl:template match="/mets:mets">
      <TEI>
         <xsl:text>
   </xsl:text>
         <teiHeader>
            <xsl:text>
      </xsl:text>
            <fileDesc>
               <xsl:text>
         </xsl:text>
               <titleStmt>
                  <xsl:apply-templates select="mets:amdSec" mode="titleStmt"/>
                  <xsl:text>
         </xsl:text>
               </titleStmt>
               <xsl:text>
         </xsl:text>
               <publicationStmt>
                  <xsl:apply-templates select="mets:amdSec" mode="publicationStmt"/>
               </publicationStmt>
               <xsl:text>
         </xsl:text>
               <sourceDesc>
                  <xsl:text>
            </xsl:text>
                  <bibl>
                     <xsl:apply-templates select="mets:amdSec" mode="sourceDesc"/>
                  </bibl>
                  <xsl:text>
         </xsl:text>
               </sourceDesc>
               <xsl:text>
      </xsl:text>
            </fileDesc>
            <xsl:text>
      </xsl:text>
            <profileDesc>
               <xsl:apply-templates select="descendant::*:trpDocMetadata/*:language" />
               <xsl:text>
      </xsl:text>
            </profileDesc>
            <xsl:text>
   </xsl:text>
         </teiHeader>
         <xsl:text>
   </xsl:text>
         <facsimile>
            <xsl:choose>
               <xsl:when test="$bounding-rectangles">
                  <xsl:variable name="facs">
                     <xsl:apply-templates select="mets:fileSec//mets:fileGrp[(@ID, @USE) = $PAGEXML]/mets:file" mode="facsimile"/>
                  </xsl:variable>
                  <xsl:apply-templates select="$facs" mode="bounding-rectangle" />
               </xsl:when>
               <xsl:otherwise>
                  <xsl:apply-templates select="mets:fileSec//mets:fileGrp[(@ID, @USE) = $PAGEXML]/mets:file" mode="facsimile"/>
               </xsl:otherwise>
            </xsl:choose>
            <xsl:text>
   </xsl:text>
         </facsimile>
         <xsl:text>
   </xsl:text>
         <text>
            <xsl:text>
      </xsl:text>
            <body>
               <xsl:for-each-group
                     select="$make_div//*[local-name() = 'div']/*"
                     group-starting-with="*[local-name() = 'pb' and following-sibling::*[1][local-name() = 'head']]
                        | *[local-name() = 'head' and not(preceding-sibling::*[1][local-name() = 'pb'])]"
               >
                  <xsl:text>
         </xsl:text>
                  <div xmlns="http://www.tei-c.org/ns/1.0">
                     <xsl:variable name="combined">
                        <xsl:choose>
                           <xsl:when test="$combine">
                              <xsl:apply-templates select="current-group()" mode="continued" />
                           </xsl:when>
                           <xsl:otherwise>
                              <xsl:copy-of select="current-group()" />
                           </xsl:otherwise>
                        </xsl:choose>
                     </xsl:variable>
                     <xsl:variable name="combined-hi">
                        <xsl:apply-templates select="$combined" mode="combine-hi" />
                     </xsl:variable>
                     <xsl:variable name="tokenized">
                        <xsl:choose>
                           <xsl:when test="$tokenize">
                              <xsl:apply-templates select="$combined-hi" mode="tokenize" />
                           </xsl:when>
                           <xsl:otherwise>
                              <xsl:copy-of select="$combined-hi" />
                           </xsl:otherwise>
                        </xsl:choose>
                     </xsl:variable>
                     <xsl:for-each select="$tokenized/*">
                        <xsl:text>
            </xsl:text>
                        <xsl:sequence select="." />
            </xsl:for-each>
                        <xsl:text>
         </xsl:text>
                  </div>
               </xsl:for-each-group>
               <xsl:text>
      </xsl:text>
            </body>
            <xsl:text>
   </xsl:text>
         </text>
         <xsl:text>
</xsl:text>
      </TEI>
   </xsl:template>

   <!-- create teiHeader from METS and (if present) included Transkribus Meta data -->
   <xd:doc>
      <xd:desc>Contents for titleStmt</xd:desc>
   </xd:doc>
   <xsl:template match="mets:amdSec" mode="titleStmt">
      <xsl:apply-templates select="descendant::trpDocMetadata/title"/>
      <xsl:apply-templates select="descendant::trpDocMetadata/author"/>
   </xsl:template>

   <xd:doc>
      <xd:desc>Contents for publicationStmt</xd:desc>
   </xd:doc>
   <xsl:template match="mets:amdSec" mode="publicationStmt">
      <xsl:text>
            </xsl:text>
      <xsl:apply-templates select="descendant::trpDocMetadata//colList[1]/colName" />
      <xsl:text>
         </xsl:text>
   </xsl:template>

   <xd:doc>
      <xd:desc>Contents for sourceDesc</xd:desc>
   </xd:doc>
   <xsl:template match="mets:amdSec" mode="sourceDesc">
      <xsl:apply-templates select="descendant::trpDocMetadata/title"/>
      <xsl:apply-templates
         select="descendant::trpDocMetadata/author | descendant::trpDocMetadata/writer"/>
      <xsl:text>
               </xsl:text>
      <idno type="Transkribus">
         <xsl:value-of select="descendant::trpDocMetadata/docId"/>
      </idno>
      <xsl:text>
            </xsl:text>
      <xsl:apply-templates select="descendant::trpDocMetadata/externalId"/>
      <xsl:apply-templates select="descendant::trpDocMetadata/desc"/>
   </xsl:template>

   <xd:doc>
      <xd:desc>Contents for editionStmt</xd:desc>
   </xd:doc>
   <xsl:template match="mets:amdSec" mode="editionStmt">
      <p>TRP document creator: <xsl:value-of select="descendant::trpDocMetadata/uploader"/></p>
      <xsl:apply-templates select="mets:amdSec//trpDocMetadata/desc"/>
   </xsl:template>

   <!-- Templates for trpMetaData -->
   <xd:doc>
      <xd:desc>
         <xd:p>The title within the Transkribus meta data</xd:p>
      </xd:desc>
   </xd:doc>
   <xsl:template match="title">
      <xsl:text>
            </xsl:text>
      <title>
         <xsl:if test="position() = 1">
            <xsl:attribute name="type">main</xsl:attribute>
         </xsl:if>
         <xsl:apply-templates/>
      </title>
   </xsl:template>

   <xd:doc>
      <xd:desc>The author as stated in Transkribus meta data. Will be used in the teiHeader as
         titleStmt/author</xd:desc>
   </xd:doc>
   <xsl:template match="author">
      <xsl:text>
            </xsl:text>
      <author>
         <xsl:apply-templates/>
      </author>
   </xsl:template>

   <xd:doc>
      <xd:desc>The author as stated in Transkribus meta data. Will be used in the teiHeader as
         titleStmt/respStmt</xd:desc>
   </xd:doc>
   <xsl:template match="writer">
      <xsl:text>
            </xsl:text>
      <respStmt>
         <resp>Writer</resp>
         <name>
            <xsl:apply-templates/>
         </name>
      </respStmt>
   </xsl:template>

    <xd:doc>
        <xd:desc>The uploader of the current document. Will be used as titleStmt/principal</xd:desc>
    </xd:doc>
    <xsl:template match="uploader">
        <respStmt>
            <resp>Uploader</resp>
            <xsl:apply-templates/>
        </respStmt>
    </xsl:template>

   <xd:doc>
      <xd:desc>The description as given in Transkribus meta data. Will be used in
         sourceDesc</xd:desc>
   </xd:doc>
   <xsl:template match="desc">
      <note>
         <xsl:apply-templates/>
      </note>
   </xsl:template>

   <xd:doc>
      <xd:desc>The name of the collection from which this document was exported. Will be used as
         seriesStmt/title</xd:desc>
   </xd:doc>
   <xsl:template match="colName">
      <p>
         <xsl:apply-templates/>
      </p>
   </xsl:template>

   <xd:doc>
      <xd:desc>Transkribus meta data: external ID</xd:desc>
   </xd:doc>
   <xsl:template match="externalId">
      <idno type="external">
         <xsl:value-of select="."/>
      </idno>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>Transkribus meta data: languages</xd:desc>
   </xd:doc>
   <xsl:template match="language">
      <xsl:text>
         </xsl:text>
      <langUsage>
         <xsl:for-each select="tokenize(., ', ')">
            <xsl:text>
            </xsl:text>
            <language>
               <xsl:attribute name="ident">
                  <xsl:value-of select="map:get($langs, .)" />
               </xsl:attribute>
               <xsl:value-of select="." />
            </language>
         </xsl:for-each>
         <xsl:text>
         </xsl:text>
      </langUsage>
   </xsl:template>

   <!-- Templates for METS -->
   <xd:doc>
      <xd:desc>Create tei:facsimile with @xml:id</xd:desc>
   </xd:doc>
   <xsl:template match="mets:file" mode="facsimile">
      <xsl:variable name="file" select="document(mets:FLocat/@xlink:href, /)"/>
      <xsl:variable name="numCurr" select="@SEQ"/>

      <xsl:apply-templates select="$file//p:Page | $file//pc:Page" mode="facsimile">
         <xsl:with-param name="imageName" select="substring-after(mets:FLocat/@xlink:href, '/')"/>
         <xsl:with-param name="numCurr" select="$numCurr" tunnel="true"/>
      </xsl:apply-templates>
   </xsl:template>

   <xd:doc>
      <xd:desc>Apply by-page</xd:desc>
   </xd:doc>
   <xsl:template match="mets:file" mode="text">
      <xsl:variable name="file" select="document(mets:FLocat/@xlink:href, .)"/>
      <xsl:variable name="numCurr" select="@SEQ"/>

      <xsl:apply-templates select="$file//p:Page | $file//pc:Page" mode="text">
         <xsl:with-param name="numCurr" select="$numCurr" tunnel="true"/>
      </xsl:apply-templates>
   </xsl:template>

   <!-- Templates for PAGE, facsimile -->
   <xd:doc>
      <xd:desc>
         <xd:p>Create tei:facsimile/tei:surface</xd:p>
      </xd:desc>
      <xd:param name="imageName">
         <xd:p>the file name of the image</xd:p>
      </xd:param>
      <xd:param name="numCurr">
         <xd:p>Numerus currens of the parent facsimile</xd:p>
      </xd:param>
   </xd:doc>
   <xsl:template match="p:Page | pc:Page" mode="facsimile">
      <xsl:param name="imageName"/>
      <xsl:param name="numCurr" tunnel="true"/>

<!--      <xsl:variable name="coords" select="tokenize(p:PrintSpace/p:Coords/@points, ' ')"/>-->
      <xsl:variable name="type" select="substring-after(@imageFilename, '.')"/>

      <xsl:text>
      </xsl:text>
      <surface ulx="0" uly="0" lrx="{@imageWidth}" lry="{@imageHeight}" xml:id="facs_{$numCurr}">
         <xsl:text>
         </xsl:text>
         <graphic url="{encode-for-uri(@imageFilename)}" width="{@imageWidth}px"
            height="{@imageHeight}px"/>
         <!-- include Transkribus image link as second graphic element for later evaluation -->
         <xsl:apply-templates
            select="preceding-sibling::p:Metadata/*:TranskribusMetadata,
                    preceding-sibling::pc:Metadata/*:TranskribusMetadata"/>
         <xsl:apply-templates
            select="p:PrintSpace | p:TextRegion | p:SeparatorRegion | p:GraphicRegion | p:TableRegion
                  | pc:PrintSpace | pc:TextRegion | pc:SeparatorRegion | pc:GraphicRegion | pc:TableRegion"
            mode="facsimile"/>
         <xsl:text>
      </xsl:text>
      </surface>
   </xsl:template>

   <xd:doc>
      <xd:desc>create the zones within facsimile/surface</xd:desc>
      <xd:param name="numCurr">Numerus currens of the current page</xd:param>
   </xd:doc>
   <xsl:template
      match="p:PrintSpace | p:TextRegion | p:SeparatorRegion | p:GraphicRegion | p:TextLine
           | pc:PrintSpace | pc:TextRegion | pc:SeparatorRegion | pc:GraphicRegion | pc:TextLine"
      mode="facsimile">
      <xsl:param name="numCurr" tunnel="true"/>

      <xsl:variable name="renditionValue">
         <xsl:choose>
            <xsl:when test="local-name() = 'TextRegion'">TextRegion</xsl:when>
            <xsl:when test="local-name() = 'SeparatorRegion'">Separator</xsl:when>
            <xsl:when test="local-name() = 'GraphicRegion'">Graphic</xsl:when>
            <xsl:when test="local-name() = 'TextLine'">Line</xsl:when>
            <xsl:when test="local-name(parent::*) = 'TableCell'">TableCell</xsl:when>
            <xsl:otherwise>printspace</xsl:otherwise>
         </xsl:choose>
      </xsl:variable>
      <xsl:variable name="custom" as="map(xs:string, xs:string)">
         <xsl:map>
            <xsl:for-each-group select="tokenize(@custom || ' lfd {' || $numCurr, '\} ')"
               group-by="substring-before(., ' ')">
               <xsl:map-entry key="substring-before(., ' ')"
                  select="string-join(substring-after(., '{'), '–')"/>
            </xsl:for-each-group>
         </xsl:map>
      </xsl:variable>

      <xsl:choose>
         <xsl:when test="(self::p:TextLine or self::pc:TextLine) and (parent::p:TableCell or parent::pc:TableCell)">
            <xsl:text>
               </xsl:text>
         </xsl:when>
         <xsl:when test="self::p:TextLine or self::pc:TextLine">
            <xsl:text>
            </xsl:text>
         </xsl:when>
         <xsl:otherwise>
            <xsl:text>
         </xsl:text>
         </xsl:otherwise>
      </xsl:choose>
      <zone points="{(p:Coords/@points, pc:Coords/@points)}" rendition="{$renditionValue}">
         <xsl:if test="$renditionValue != 'printspace'">
            <xsl:attribute name="xml:id">
               <xsl:value-of select="'facs_' || $numCurr || '_' || @id"/>
            </xsl:attribute>
         </xsl:if>
         <xsl:if test="@type">
            <xsl:attribute name="subtype">
               <xsl:value-of select="@type"/>
            </xsl:attribute>
         </xsl:if>
         <xsl:if test="map:contains($custom, 'structure') and not(@type)">
            <xsl:attribute name="subtype"
               select="substring-after(substring-before(map:get($custom, 'structure'), ';'), ':')"/>
         </xsl:if>
         <xsl:apply-templates select="p:TextLine | pc:TextLine" mode="facsimile"/>
         <xsl:if test="$word-coordinates">
            <xsl:apply-templates select="p:Word | pc:Word" mode="facsimile" />
         </xsl:if>
         <xsl:choose>
            <xsl:when test="self::p:TextLine and p:Word and $word-coordinates">
               <xsl:text>
            </xsl:text>
            </xsl:when>
            <xsl:when test="self::p:TextRegion">
               <xsl:text>
         </xsl:text>
            </xsl:when>
            <xsl:when test="self::pc:TextLine and pc:Word and $word-coordinates">
               <xsl:text>
            </xsl:text>
            </xsl:when>
            <xsl:when test="self::pc:TextRegion">
               <xsl:text>
         </xsl:text>
            </xsl:when>
         </xsl:choose>
      </zone>
   </xsl:template>
   
   <xd:doc>
      <xd:desc>create a zone for each word within facsimile/surface</xd:desc>
      <xd:param name="numCurr">Numerus currens of the current page</xd:param>
   </xd:doc>
   <xsl:template match="p:Word | pc:Word" mode="facsimile">
      <xsl:param name="numCurr" tunnel="true"/>
      
      <xsl:text>
               </xsl:text>
      <zone points="{(p:Coords/@points, pc:Coords/@points)}" type="word">
         <xsl:attribute name="xml:id">
            <xsl:value-of select="'facs_' || $numCurr || '_' || @id"/>
         </xsl:attribute>
      </zone>
   </xsl:template>

   <xd:doc>
      <xd:desc>Create the zone for a table</xd:desc>
      <xd:param name="numCurr">Numerus currens of the current page</xd:param>
   </xd:doc>
   <xsl:template match="p:TableRegion | pc:TableRegion" mode="facsimile">
      <xsl:param name="numCurr" tunnel="true"/>

      <xsl:text>
         </xsl:text>
      <zone points="{(p:Coords/@points, pc:Coords/@points)}" rendition="Table">
         <xsl:attribute name="xml:id">
            <xsl:value-of select="'facs_' || $numCurr || '_' || @id"/>
         </xsl:attribute>
         <xsl:apply-templates select="p:TableCell | pc:TableCell" mode="facsimile"/>
         <xsl:text>
         </xsl:text>
      </zone>
   </xsl:template>
   
   <xsl:template match="p:TableCell | pc:TableCell" mode="facsimile">
      <xsl:param name="numCurr" tunnel="true"/>
      
      <xsl:text>
            </xsl:text>
      <zone points="{(p:Coords/@points, pc:Coords/@points)}" rendition="TableCell">
         <xsl:attribute name="xml:id">
            <xsl:value-of select="'facs_' || $numCurr || '_' || @id"/>
         </xsl:attribute>
         <xsl:apply-templates select="p:TextLine | pc:TextLine" mode="facsimile" />
         <xsl:text>
            </xsl:text>
      </zone>
   </xsl:template>

   <xd:doc>
      <xd:desc>create the page content</xd:desc>
      <xd:param name="numCurr">Numerus currens of the current page</xd:param>
   </xd:doc>
   <!-- Templates for PAGE, text -->
   <xsl:template match="p:Page | pc:Page" mode="text">
      <xsl:param name="numCurr" tunnel="true"/>
      <pb facs="#facs_{$numCurr}" n="{$numCurr}" xml:id="img_{format-number($numCurr, '0000')}"/>
      <xsl:apply-templates
         select="p:TextRegion | p:SeparatorRegion | p:GraphicRegion | p:TableRegion
               | pc:TextRegion | pc:SeparatorRegion | pc:GraphicRegion | pc:TableRegion" mode="text">
         <xsl:with-param name="center" tunnel="true" select="number(@imageWidth) div 2"
            as="xs:double"/>
      </xsl:apply-templates>
   </xsl:template>

   <xd:doc>
      <xd:desc>
         <xd:p>create specific elements based on the @typing of the text region</xd:p>
         <xd:p>PAGE labels for text region see: https://www.primaresearch.org/tools/PAGELibraries
            caption observed header observed footer observed page-number observed drop-capital
            ignored credit ignored floating ignored signature-mark observed catch-word observed
            marginalia observed footnote observed footnote-continued observed endnote ignored
            TOC-entry ignored list-label ignored other observed </xd:p>
      </xd:desc>
      <xd:param name="numCurr"/>
      <xd:param name="center"/>
   </xd:doc>
   <xsl:template match="p:TextRegion | pc:TextRegion" mode="text">
      <xsl:param name="numCurr" tunnel="true"/>
      <xsl:param name="center" tunnel="true" as="xs:double"/>
      
      <xsl:variable name="custom" as="map(*)">
         <xsl:choose>
            <xsl:when test="@custom">
               <xsl:apply-templates select="@custom"/>
            </xsl:when>
            <xsl:otherwise>
               <xsl:map/>
            </xsl:otherwise>
         </xsl:choose>
      </xsl:variable>
      <xsl:variable name="regionType" as="xs:string*" select="(@type, $custom?structure?type)" />

      <xsl:choose>
         <xsl:when test="not(p:TextLine or pc:TextLine or $withoutTextline)"/>
         <xsl:when test="'heading' = $regionType">
            <head facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </head>
         </xsl:when>
         <xsl:when test="'caption' = $regionType and not($ab)">
            <figure>
               <head facs="#facs_{$numCurr}_{@id}">
                  <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
               </head>
            </figure>
         </xsl:when>
         <xsl:when test="'header' = $regionType and not($ab)">
            <fw type="header" place="top" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </fw>
         </xsl:when>
         <xsl:when test="'catch-word' = $regionType and not($ab)">
            <fw type="catch" place="bottom" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </fw>
         </xsl:when>
         <xsl:when test="'signature-mark' = $regionType and not($ab)">
            <fw place="bottom" type="sig" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </fw>
         </xsl:when>
         <xsl:when test="'marginalia' = $regionType and not($ab)">
            <xsl:variable name="side">
               <xsl:choose>
                  <xsl:when test="number(substring-before((p:Coords/@points, pc:Coords/@points), ',')) gt $center"
                     >margin-right</xsl:when>
                  <xsl:otherwise>margin-left</xsl:otherwise>
               </xsl:choose>
            </xsl:variable>
            <note place="{$side}" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </note>
         </xsl:when>
         <xsl:when test="'footnote' = $regionType and not($ab)">
            <note place="foot" n="[footnote reference]" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </note>
         </xsl:when>
         <xsl:when test="'footnote-continued' = $regionType and not($ab)">
            <note place="foot" n="[footnote-continued reference]" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </note>
         </xsl:when>
         <xsl:when test="'endnote' = $regionType and not($ab)">
            <note type="endnote" n="[footnote reference]" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </note>
         </xsl:when>
         <xsl:when test="'footer' = $regionType and not($ab)">
            <fw type="footer" place="bottom" facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </fw>
         </xsl:when>
         <xsl:when test="'page-number' = $regionType and not($ab)">
            <fw type="page-number" facs="#facs_{$numCurr}_{@id}">
               <xsl:attribute name="place">
                  <xsl:variable name="verticalPosition"
                     select="(p:Coords/@points, pc:Coords/@points) => substring-before(' ') => substring-after(',') => number()"/>
                  <xsl:choose>
                     <xsl:when
                        test="$verticalPosition div number(../@imageHeight) lt .33">
                        <xsl:text>top</xsl:text>
                     </xsl:when>
                     <xsl:when
                        test="$verticalPosition div number(../@imageHeight) lt .66">
                        <xsl:text>centre</xsl:text>
                     </xsl:when>
                     <xsl:otherwise>
                        <xsl:text>bottom</xsl:text>
                     </xsl:otherwise>
                  </xsl:choose>
               </xsl:attribute>
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </fw>
         </xsl:when>
         <xsl:when test="'paragraph' = $regionType">
            <xsl:text>
            </xsl:text>
            <p facs="#facs_{$numCurr}_{@id}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
            </p>
         </xsl:when>
         <!-- the fallback option should be a semantically open element such as <ab> -->
         <xsl:otherwise>
            <xsl:text>
            </xsl:text>
            <ab facs="#facs_{$numCurr}_{@id}" type="{(@type,$custom?structure?type)[normalize-space() != ''][1]}">
               <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
               <xsl:text>
            </xsl:text>
            </ab>
         </xsl:otherwise>
      </xsl:choose>
   </xsl:template>

   <xd:doc>
      <xd:desc>create a table</xd:desc>
      <xd:param name="numCurr"/>
   </xd:doc>
   <xsl:template match="p:TableRegion | pc:TableRegion" mode="text">
      <xsl:param name="numCurr" tunnel="true"/>
      <xsl:text>
      </xsl:text>
      <table facs="#facs_{$numCurr}_{@id}">
         <xsl:for-each-group select="p:TableCell | pc:TableCell" group-by="@row">
            <xsl:sort select="@col"/>
            <xsl:text>
        </xsl:text>
            <row n="{@row}">
               <xsl:apply-templates select="current-group()"/>
            </row>
         </xsl:for-each-group>
      </table>
   </xsl:template>

   <xd:doc>
      <xd:desc>create table cells</xd:desc>
      <xd:param name="numCurr"/>
   </xd:doc>
   <xsl:template match="p:TableCell | pc:TableCell">
      <xsl:param name="numCurr" tunnel="true"/>
      <xsl:text>
          </xsl:text>
      <cell facs="#facs_{$numCurr}_{@id}" n="{@col}">
         <xsl:apply-templates select="@rowSpan | @colSpan"/>
         <xsl:attribute name="rend">
            <xsl:value-of select="number((xs:boolean(@leftBorderVisible), false())[1])"/>
            <xsl:value-of select="number((xs:boolean(@topBorderVisible), false())[1])"/>
            <xsl:value-of select="number((xs:boolean(@rightBorderVisible), false())[1])"/>
            <xsl:value-of select="number((xs:boolean(@bottomBorderVisible), false())[1])"/>
         </xsl:attribute>
         <xsl:apply-templates select="p:TextLine | pc:TextLine"/>
      </cell>
   </xsl:template>
   <xd:doc>
      <xd:desc>rowspan -> rows</xd:desc>
   </xd:doc>
   <xsl:template match="@rowSpan">
      <xsl:choose>
         <xsl:when test=". &gt; 1">
            <xsl:attribute name="rows" select="."/>
         </xsl:when>
      </xsl:choose>
   </xsl:template>
   <xd:doc>
      <xd:desc>colspan -> cols</xd:desc>
   </xd:doc>
   <xsl:template match="@colSpan">
      <xsl:choose>
         <xsl:when test=". &gt; 1">
            <xsl:attribute name="cols" select="."/>
         </xsl:when>
      </xsl:choose>
   </xsl:template>

   <xd:doc>
      <xd:desc>create a figure for graphics
         – Provided by github:@liladude in https://github.com/dariok/page2tei/issues/25#issuecomment-1543625106
         – Reported by github:@giorgiaagostini</xd:desc>
      <xd:param name="numCurr"/>
   </xd:doc>
   <xsl:template match="p:GraphicRegion" mode="text">
      <xsl:param name="numCurr" tunnel="true" />
      <xsl:text>
      </xsl:text>
      <figure facs="#facs_{$numCurr}_{@id}">
         <graphic xml:id="#facs_{$numCurr}_{@id}" />
      </figure>
   </xsl:template>

   <xd:doc>
      <xd:desc>Converts one line of PAGE to one line of TEI</xd:desc>
      <xd:param name="numCurr">Numerus currens, to be tunneled through from the page
         level</xd:param>
   </xd:doc>
   <xsl:template match="p:TextLine | pc:TextLine">
      <xsl:param name="numCurr" tunnel="true"/>

      <xsl:if test="p:Baseline or pc:Baseline or $withoutBaseline">
         <xsl:variable name="text" select="p:TextEquiv/p:Unicode, pc:TextEquiv/pc:Unicode"/>
         <xsl:variable name="custom" as="text()*">
            <xsl:for-each select="tokenize(@custom, '\}')">
               <xsl:variable name="content" select="substring-after(., '{') => normalize-space()"/>
               <xsl:variable name="name" select="substring-before(., ' {') => normalize-space()"/>
               <xsl:choose>
                  <xsl:when test="not(contains(., 'offset:'))" />
                  <xsl:otherwise>
                     <xsl:value-of select="normalize-space()"/>
                  </xsl:otherwise>
               </xsl:choose>
            </xsl:for-each>
         </xsl:variable>
         <xsl:variable name="starts" as="map(*)">
            <xsl:map>
               <xsl:if test="count($custom) &gt; 0">
                  <xsl:for-each-group select="$custom"
                     group-by="substring-before(substring-after(., 'offset:'), ';')">
                     <xsl:map-entry key="xs:int(current-grouping-key())" select="current-group()"/>
                  </xsl:for-each-group>
               </xsl:if>
            </xsl:map>
         </xsl:variable>
         <xsl:variable name="ends" as="map(*)">
            <xsl:map>
               <xsl:if test="count($custom) &gt; 0">
                  <xsl:for-each-group select="$custom" group-by="
                        xs:int(substring-before(substring-after(., 'offset:'), ';'))
                        + xs:int(substring-before(substring-after(., 'length:'), ';'))">
                     <xsl:map-entry key="current-grouping-key()" select="current-group()"/>
                  </xsl:for-each-group>
               </xsl:if>
            </xsl:map>
         </xsl:variable>
         <xsl:variable name="prepped">
            <xsl:for-each select="0 to string-length($text)">
               <xsl:if test=".">
                  <xsl:value-of select="substring($text, ., 1)"/>
               </xsl:if>
               <!-- place end marker for all non-void elements that end here; we must not place void elements here
                  as this would mean closing a tei:gap before it was opened -->
               <xsl:for-each select="map:get($ends, .)">
                  <xsl:sort select="substring-before(substring-after(., 'offset:'), ';')"
                     order="descending"/>
                  <xsl:sort select="substring(., 1, 3)" order="descending"/>
                  <xsl:if test="substring-after(., 'length:') => substring-before(';') != '0'">
                     <xsl:element name="local:m">
                        <xsl:attribute name="type" select="normalize-space(substring-before(., ' '))"/>
                        <xsl:attribute name="o" select="substring-after(., 'offset:')"/>
                        <xsl:attribute name="pos">e</xsl:attribute>
                     </xsl:element>
                  </xsl:if>
               </xsl:for-each>
               <xsl:for-each select="map:get($starts, .)">
                  <xsl:sort select="
                        xs:int(substring-before(substring-after(., 'offset:'), ';'))
                        + xs:int(substring-before(substring-after(., 'length:'), ';'))"
                     order="descending"/>
                  <xsl:sort select="substring(., 1, 3)" order="ascending"/>
                  <xsl:element name="local:m">
                     <xsl:attribute name="type" select="normalize-space(substring-before(., ' '))"/>
                     <xsl:attribute name="o" select="substring-after(., 'offset:')"/>
                     <xsl:attribute name="pos">s</xsl:attribute>
                  </xsl:element>
               </xsl:for-each>
               <!-- place end marker for void elements such as tei:gap -->
               <xsl:for-each select="map:get($ends, .)">
                  <xsl:sort select="substring-before(substring-after(., 'offset:'), ';')"
                     order="descending"/>
                  <xsl:sort select="substring(., 1, 3)" order="descending"/>
                  <xsl:if test="substring-after(., 'length:') => substring-before(';') = '0'">
                     <xsl:element name="local:m">
                        <xsl:attribute name="type" select="normalize-space(substring-before(., ' '))"/>
                        <xsl:attribute name="o" select="substring-after(., 'offset:')"/>
                        <xsl:attribute name="pos">e</xsl:attribute>
                     </xsl:element>
                  </xsl:if>
               </xsl:for-each>
            </xsl:for-each>
         </xsl:variable>
         <xsl:variable name="prepared">
            <xsl:for-each select="$prepped/node()">
               <xsl:choose>
                  <xsl:when test="@pos = 'e'">
                     <xsl:variable name="position" select="count(preceding-sibling::node())"/>
                     <xsl:variable name="o" select="@o"/>
                     <xsl:variable name="id" select="@type"/>
                     <xsl:variable name="precs"
                        select="preceding-sibling::local:m[@pos = 's' and preceding-sibling::local:m[@o = $o]]"/>

                     <xsl:for-each select="$precs">
                        <xsl:variable name="so" select="@o"/>
                        <xsl:variable name="myP"
                           select="count(following-sibling::local:m[@pos = 'e' and @o = $so]/preceding-sibling::node())"/>
                        <xsl:if test="
                              following-sibling::local:m[@pos = 'e' and @o = $so
                              and $myP &gt; $position] and not(@type = $id)">
                           <local:m type="{@type}" pos="e" o="{@o}"
                              prev="{$myP||'.'||$position||($myP > $position)}"/>
                        </xsl:if>
                     </xsl:for-each>
                     <xsl:sequence select="."/>
                     <xsl:for-each select="$precs">
                        <xsl:variable name="so" select="@o"/>
                        <xsl:variable name="myP"
                           select="count(following-sibling::local:m[@pos = 'e' and @o = $so]/preceding-sibling::node())"/>
                        <xsl:if test="
                              following-sibling::local:m[@pos = 'e' and @o = $so
                              and $myP &gt; $position] and not(@type = $id)">
                           <local:m type="{@type}" pos="s" o="{@o}"
                              prev="{$myP||'.'||$position||($myP > $position)}"/>
                        </xsl:if>
                     </xsl:for-each>
                  </xsl:when>
                  <xsl:otherwise>
                     <xsl:sequence select="."/>
                  </xsl:otherwise>
               </xsl:choose>
            </xsl:for-each>
         </xsl:variable>

         <!-- TODO parameter to create <l>...</l> - #1 -->
         <xsl:text>
               </xsl:text>
         <lb facs="#facs_{$numCurr}_{@id}">
            <xsl:if test="@custom">
               <xsl:variable name="pos"
                  select="xs:integer(substring-before(substring-after(@custom, 'index:'), ';')) + 1"/>
               <xsl:attribute name="n">
                  <xsl:text>N</xsl:text>
                  <xsl:value-of select="format-number($pos, '000')"/>
               </xsl:attribute>
            </xsl:if>
         </lb>
         <xsl:apply-templates select="$prepared/text()[not(preceding-sibling::local:m)]"/>
         <xsl:apply-templates select="
               $prepared/local:m[@pos = 's']
               [count(preceding-sibling::local:m[@pos = 's']) = count(preceding-sibling::local:m[@pos = 'e'])]"/>
         <!--[not(preceding-sibling::local:m[1][@pos='s'])]" />-->
      </xsl:if>
   </xsl:template>

   <xd:doc>
      <xd:desc>Starting milestones for (possibly nested) elements</xd:desc>
   </xd:doc>
   <xsl:template match="local:m[@pos = 's']">
      <xsl:param name="numCurr" tunnel="true" />
      <xsl:variable name="o" select="@o"/>
      <xsl:variable name="custom" as="map(*)">
         <xsl:map>
            <xsl:variable name="t" select="tokenize(@o, ';')"/>
            <xsl:if test="count($t) &gt; 1">
               <xsl:for-each select="$t[. != '']">
                  <xsl:map-entry
                     key="normalize-space(substring-before(., ':'))"
                     select="normalize-space(substring-after(., ':'))"/>
               </xsl:for-each>
            </xsl:if>
         </xsl:map>
      </xsl:variable>

      <xsl:variable name="elem">
         <local:t>
            <xsl:sequence select="
                  following-sibling::node()
                  intersect following-sibling::local:m[@o = $o]/preceding-sibling::node()"
            />
         </local:t>
      </xsl:variable>

      <xsl:choose>
         <xsl:when test="@type = 'textStyle'">
            <xsl:variable name="rend" as="xs:string*">
               <xsl:if test="$custom?italic = 'true'">
                  <xsl:text>font-style: italic;</xsl:text>
               </xsl:if>
               <xsl:if test="$custom?bold = 'true'">
                  <xsl:text>font-weight: bold;</xsl:text>
               </xsl:if>
               <xsl:if test="$custom?underlined = 'true'">
                  <xsl:text>text-decoration: underline;</xsl:text>
               </xsl:if>
               <xsl:if test="$custom?strikethrough = 'true'">
                  <xsl:text>text-decoration: line-through;</xsl:text>
               </xsl:if>
               <xsl:if test="number($custom?fontSize) gt 0">
                  <xsl:value-of select="'font-size: ' || $custom?fontSize || 'px;'"/>
               </xsl:if>
               <xsl:if test="number($custom?kerning) gt 0">
                  <xsl:value-of select="'letter-spacing: ' || $custom?fontSize || 'px;'"/>
               </xsl:if>
               <xsl:if test="$custom?fontFamily != ''">
                  <xsl:value-of select="'font-family: ' || $custom?fontFamily || ';'"/>
               </xsl:if>
               <xsl:if test="$custom?superscript = 'true'">
                  <xsl:text>vertical-align: superscript;</xsl:text>
               </xsl:if>
               <xsl:if test="$custom?subscript = 'true'">
                  <xsl:text>vertical-align: subscript;</xsl:text>
               </xsl:if>
               <xsl:if test="$custom?smallCaps = 'true'">
                  <xsl:text>font-variant-caps: small-caps;</xsl:text>
               </xsl:if>
               <xsl:if test="$custom?letterSpaced = 'true'">
                  <xsl:text>letter-spacing: 5px;</xsl:text>
               </xsl:if>
            </xsl:variable>
            <hi>
               <xsl:if test="count($rend) gt 0">
                  <xsl:attribute name="style" select="string-join($rend, ' ')"/>
               </xsl:if>
               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </hi>
         </xsl:when>
         <xsl:when test="@type = 'supplied'">
            <supplied reason="">
               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </supplied>
         </xsl:when>
         <xsl:when test="@type = 'abbrev'">
            <choice>
               <xsl:if test="$custom('continued')">
                  <xsl:attribute name="continued" select="true()"/>
               </xsl:if>
               <expan>
                  <xsl:value-of select="replace(map:get($custom, 'expansion'), '\\u0020', ' ')"/>
               </expan>
               <abbr>
                  <xsl:call-template name="elem">
                     <xsl:with-param name="elem" select="$elem"/>
                  </xsl:call-template>
               </abbr>
            </choice>
         </xsl:when>
         <xsl:when test="@type = 'sic'">
            <choice>
               <corr>
                  <xsl:value-of select="replace(map:get($custom, 'correction'), '\\u0020', ' ')"/>
               </corr>
               <sic>
                  <xsl:call-template name="elem">
                     <xsl:with-param name="elem" select="$elem"/>
                  </xsl:call-template>
               </sic>
            </choice>
         </xsl:when>
         <xsl:when test="@type = 'date'">
            <date>
               <!--<xsl:variable name="year" select="if(map:keys($custom) = 'year') then format-number(xs:integer(map:get($custom, 'year')), '0000') else '00'"/>
          <xsl:variable name="month" select=" if(map:keys($custom) = 'month') then format-number(xs:integer(map:get($custom, 'month')), '00') else '00'"/>
          <xsl:variable name="day" select=" if(map:keys($custom) = 'day') then format-number(xs:integer(map:get($custom, 'day')), '00') else '00'"/>
          <xsl:variable name="when" select="$year||'-'||$month||'-'||$day" />
          <xsl:if test="$when != '0000-00-00'">
            <xsl:attribute name="when" select="$when" />
          </xsl:if>-->
               <xsl:for-each select="map:keys($custom)">
                  <xsl:if test=". != 'length' and . != ''">
                     <xsl:attribute name="{.}" select="map:get($custom, .)"/>
                  </xsl:if>
               </xsl:for-each>
               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </date>
         </xsl:when>
         <xsl:when test="@type = 'person'">
            <xsl:variable name="elName" select="
                  if ($rs) then
                     'rs'
                  else
                     'persName'"/>
            <xsl:element name="{$elName}">
               <xsl:if test="$rs">
                  <xsl:attribute name="type">person</xsl:attribute>
               </xsl:if>
               <xsl:if test="$custom('lastname') != '' or $custom('firstname') != ''">
                  <xsl:attribute name="key"
                     select="replace($custom('lastname'), '\\u0020', ' ') || ', ' || replace($custom('firstname'), '\\u0020', ' ')"
                  />
               </xsl:if>
               <xsl:if test="$custom('continued')">
                  <xsl:attribute name="continued" select="true()"/>
               </xsl:if>
               <xsl:if test="$unknownAttributes">
                  <xsl:for-each select="map:keys($custom)">
                     <xsl:if test="not(. = ('', 'length', 'lastname', 'firstname'))">
                        <xsl:attribute name="{.}" select="$custom(.)"/>
                     </xsl:if>
                  </xsl:for-each>
               </xsl:if>
               
               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </xsl:element>
         </xsl:when>
         <xsl:when test="@type = 'place'">
            <xsl:variable name="elName" select="
                  if ($rs) then
                     'rs'
                  else
                     'placeName'"/>
            <xsl:element name="{$elName}">
               <xsl:if test="$rs">
                  <xsl:attribute name="type">place</xsl:attribute>
               </xsl:if>
               <xsl:if test="$custom('placeName') != ''">
                  <xsl:attribute name="key" select="replace($custom('placeName'), '\\u0020', ' ')"/>
               </xsl:if>
               <xsl:if test="$custom?continued">
                  <xsl:attribute name="continued" select="true()"/>
               </xsl:if>

               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </xsl:element>
         </xsl:when>
         <xsl:when test="@type = 'organization'">
            <xsl:variable name="elName" select="
                  if ($rs) then
                     'rs'
                  else
                     'orgName'"/>
            <xsl:element name="{$elName}">
               <xsl:if test="$rs">
                  <xsl:attribute name="type">org</xsl:attribute>
               </xsl:if>
               <xsl:if test="$custom?continued">
                  <xsl:attribute name="continued" select="true()"/>
               </xsl:if>
               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </xsl:element>
         </xsl:when>
         <xsl:otherwise>
            <xsl:element name="{@type}">
               <xsl:for-each select="map:keys($custom)">
                  <xsl:if test="not(. = ('', 'length'))">
                     <xsl:try>
                        <xsl:attribute name="{.}" select="$custom(.)"/>
                        <xsl:catch>
                           <xsl:message select="string($o)" />
                           <xsl:message select="string($numCurr)"/>
                        </xsl:catch>
                     </xsl:try>
                  </xsl:if>
               </xsl:for-each>
               <xsl:call-template name="elem">
                  <xsl:with-param name="elem" select="$elem"/>
               </xsl:call-template>
            </xsl:element>
         </xsl:otherwise>
      </xsl:choose>

      <xsl:apply-templates
         select="following-sibling::local:m[@pos = 'e' and @o = $o]/following-sibling::node()[1][self::text()]"
      />
   </xsl:template>

   <xd:doc>
      <xd:desc>Process what's between a pair of local:m</xd:desc>
      <xd:param name="elem"/>
   </xd:doc>
   <xsl:template name="elem">
      <xsl:param name="elem"/>

      <xsl:choose>
         <xsl:when test="$elem//local:m">
            <xsl:apply-templates select="$elem/local:t/text()[not(preceding-sibling::local:m)]"/>
            <xsl:apply-templates select="
                  $elem/local:t/local:m[@pos = 's']
                  [not(preceding-sibling::local:m[1][@pos = 's'])]"/>
         </xsl:when>
         <xsl:otherwise>
            <xsl:sequence select="$elem/local:t/node()"/>
         </xsl:otherwise>
      </xsl:choose>
   </xsl:template>

   <xd:doc>
      <xd:desc>Leave out possibly unwanted parts</xd:desc>
   </xd:doc>
   <xsl:template match="p:Metadata | pc:Metadata" mode="text"/>

   <xd:doc>
      <xd:desc>TranskribusMetadata contains the link to the image on Transkribus’ servers; return a
         tei:graphic element with this URL so it can be evaluated during postprocessing</xd:desc>
   </xd:doc>
   <xsl:template match="*:TranskribusMetadata[@imgUrl]">
      <xsl:text>
         </xsl:text>
      <graphic url="{@imgUrl}" width="{following::p:Page/@imageWidth}px"
         height="{following::p:Page/@imageHeight}px"/>
   </xsl:template>

   <xd:doc>
      <xd:desc>Parse the content of an attribute such as @custom into a map.</xd:desc>
   </xd:doc>
   <xsl:template match="@custom" as="map(*)">
      <xsl:map>
         <xsl:for-each select="tokenize(., '\}')[normalize-space() != '']">
            <xsl:map-entry key="substring-before(normalize-space(), ' ')">
               <xsl:map>
                  <xsl:for-each
                     select="tokenize(substring-after(., '{'), ';')[normalize-space() != '']">
                     <xsl:map-entry key="normalize-space(substring-before(., ':'))" select="normalize-space(substring-after(., ':'))"
                     />
                  </xsl:for-each>
               </xsl:map>
            </xsl:map-entry>
         </xsl:for-each>
      </xsl:map>
   </xsl:template>

   <xd:doc>
      <xd:desc>Text nodes to be copied – we replace U+xA0 by a regular space U+x20</xd:desc>
   </xd:doc>
   <xsl:template match="text()">
      <xsl:value-of select="translate(., '', ' ')"/>
   </xsl:template>
</xsl:stylesheet>
