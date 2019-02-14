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