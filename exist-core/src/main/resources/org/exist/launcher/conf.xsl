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
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:param name="cacheSize"/>
    <xsl:param name="collectionCache"/>
    <xsl:param name="dataDir"/>

    <xsl:template match="db-connection/@cacheSize">
        <xsl:attribute name="cacheSize"><xsl:value-of select="$cacheSize"/>M</xsl:attribute>
    </xsl:template>

    <xsl:template match="db-connection/@collectionCache">
        <xsl:attribute name="collectionCache"><xsl:value-of select="$collectionCache"/>M</xsl:attribute>
    </xsl:template>

    <xsl:template match="db-connection/@files">
        <xsl:attribute name="files">
            <xsl:value-of select="$dataDir"/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="recovery/@journal-dir">
        <xsl:attribute name="journal-dir">
            <xsl:value-of select="$dataDir"/>
        </xsl:attribute>
    </xsl:template>

    <xsl:template match="@*|node()" priority="-1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>