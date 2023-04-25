<?xml version="1.0" encoding="UTF-8"?>
<!--

    eXist-db Open Source Native XML Database
    Copyright (C) 2001 The eXist-db Authors

    info@exist-db.org
    http://www.exist-db.org

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:tei="http://www.tei-c.org/ns/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:math="http://www.w3.org/2005/xpath-functions/math" xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl" exclude-result-prefixes="xs math xd tei" version="3.0">

    <xsl:param name="heading" as="xs:boolean" select="true()"/>
    <xsl:param name="documentID" as="xs:string" select="/tei:TEI/@xml:id"/>

    <xsl:output indent="true"/>

    <xsl:mode on-no-match="shallow-skip" use-accumulators="#all"/>
    <xsl:mode name="html" on-no-match="text-only-copy"/>

    <xsl:accumulator name="document-nos" initial-value="()" as="xs:string*">
        <xsl:accumulator-rule match="tei:div[@type eq 'document']" select="($value, @n)" phase="end"/>
    </xsl:accumulator>

    <xsl:accumulator name="document-ids" initial-value="()" as="xs:string*">
        <xsl:accumulator-rule match="tei:div[tei:div/@type = 'document']" select="()"/>
        <xsl:accumulator-rule match="tei:div[@type eq 'document']" select="($value, @xml:id)" phase="end"/>
    </xsl:accumulator>

    <xsl:template match="tei:TEI">
        <div class="toc">
            <div class="toc__header">
                <h4 class="title">Contents</h4>
            </div>
            <nav aria-label="Side navigation,,," class="toc__chapters">
                <ul class="chapters js-smoothscroll">
                    <xsl:apply-templates select="tei:text"/>
                </ul>
            </nav>
        </div>
    </xsl:template>

    <xsl:template match="tei:div[@xml:id][not(@type = ('document'))]">
        <xsl:variable name="accDocs" as="xs:string*" select="accumulator-after('document-nos')"/>
        <xsl:variable name="prevDocs" as="xs:string*" select="accumulator-before('document-nos')"/>
        <xsl:variable name="docs" as="xs:string*" select="$accDocs[not(. = $prevDocs)]"/>
        <xsl:variable name="prevDocIDs" as="xs:string*" select="accumulator-before('document-ids')"/>
        <xsl:variable name="docIDs" as="xs:string*" select="accumulator-after('document-ids')[not(. = $prevDocIDs)]"/>
        <li data-tei-id="{@xml:id}">
            <xsl:if test="exists($docIDs) and tei:div[@type='document']">
                <xsl:attribute name="data-tei-documents" select="string-join($docIDs, ' ')"/>
            </xsl:if>

            <a href="/{$documentID}/{@xml:id}">
                <xsl:apply-templates mode="html" select="tei:head"/>
            </a>
            <xsl:value-of select="(' (Document' || 's'[count($docs) gt 1] || ' ' || $docs[1] || ' - '[count($docs) gt 1] || $docs[last()][count($docs) gt 1] || ')')[exists($docs)]"/>

            <xsl:where-populated>
                <ul class="chapters__nested">
                    <xsl:apply-templates/>
                </ul>
            </xsl:where-populated>
        </li>
    </xsl:template>

    <xsl:template match="tei:div[@xml:id eq 'toc']" priority="2"/>

    <xsl:template match="tei:head/tei:note" mode="html"/>

    <xsl:template match="tei:lb" mode="html">
        <br/>
    </xsl:template>

</xsl:stylesheet>
