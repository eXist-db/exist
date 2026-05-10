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
<!--
    Transforms the indexes-integration-tests conf.xml into the variant
    used by the JMH benchmark module. The benchmark module needs the
    plain `range:eq` index module on top of the lucene + ngram modules
    that the integration-tests config already registers.

    Sourcing from the integration-tests conf.xml (rather than vendoring
    a copy here) avoids drift potential as the upstream conf evolves.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

    <xsl:output method="xml" indent="yes" omit-xml-declaration="no"/>

    <!-- Add the plain range index module alongside the existing lucene + ngram modules. -->
    <xsl:template match="indexer/modules">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
            <module id="range-index" class="org.exist.indexing.range.RangeIndex"/>
        </xsl:copy>
    </xsl:template>

    <!-- Add the range XQuery module alongside the existing lucene + ngram modules. -->
    <xsl:template match="xquery/builtin-modules">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
            <module uri="http://exist-db.org/xquery/range" class="org.exist.xquery.modules.range.RangeIndexModule"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
