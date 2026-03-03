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
    Merge repo.xml modified by user with original file. This is necessary because we have to
    remove sensitive information during upload (default password) and need to restore it
    when the package is synchronized back to disk.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:repo="http://exist-db.org/xquery/repo">

    <!-- Set to the original repo.xml which should be merged with the input document -->
    <xsl:param name="original"/>

    <xsl:template match="repo:permissions">
        <permissions xmlns="http://exist-db.org/xquery/repo">
            <!-- If a (new) password has been specified in the input doc, use it.
                 Preserve the original password otherwise.
            -->
            <xsl:attribute name="password">
                <xsl:choose>
                    <xsl:when test="@password">
                        <xsl:value-of select="@password"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$original//repo:permissions/@password"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:attribute>
            <xsl:copy-of select="@*[local-name(.) != 'password']"/>
        </permissions>
    </xsl:template>

    <xsl:template match="repo:deployed"/>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>