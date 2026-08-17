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
    Entry point of the `xqts-compare` Maven goal (see the README):

        mvn -pl exist-xqts exec:exec@xqts-compare \
            -Dprevious-result=<dir> -Dcurrent-result=<dir>

    Compares two exist-xqts-runner result folders and writes
    `comparison-results.xml` (the primary output) plus the GitHub-flavoured
    Markdown rendering `comparison-results.md` next to it. Terminates with an
    actionable message when a parameter is missing or does not point to a
    comparable result folder.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:err="http://www.w3.org/2005/xqt-errors"
    xmlns:cr="http://exist-db.org/exist-xqts/compare-results"
    exclude-result-prefixes="xs err"
    version="3.0">

    <xsl:import href="compare-results.xslt"/>
    <xsl:import href="compare-results-md.xslt"/>

    <!-- Paths to two result folders written by `exist-xqts-runner ... - -output-dir <dir>` -->
    <xsl:param name="previous-result" as="xs:string" select="''"/>
    <xsl:param name="current-result" as="xs:string" select="''"/>

    <xsl:output method="xml" version="1.0" omit-xml-declaration="no" indent="yes" encoding="UTF-8"/>
    <xsl:output name="cr:markdown" method="text" encoding="UTF-8"/>

    <xsl:variable name="cr:usage" as="xs:string" select="'Usage: mvn -pl exist-xqts exec:exec@xqts-compare -Dprevious-result=/path/to/previous -Dcurrent-result=/path/to/current'"/>
    <xsl:variable name="cr:expectation" as="xs:string" select="'It must point to a result folder written by exist-xqts-runner (its --output-dir), which contains the JUnit reports under junit/data/*.xml. Relative paths are resolved against this stylesheet, so prefer absolute paths.'"/>

    <xsl:template name="compare" as="document-node(element(cr:comparison))">
        <xsl:variable name="previous-junit-data-path" select="cr:validated-junit-data-path('previous-result', $previous-result)" as="xs:string"/>
        <xsl:variable name="current-junit-data-path" select="cr:validated-junit-data-path('current-result', $current-result)" as="xs:string"/>
        <xsl:variable name="comparison" as="document-node(element(cr:comparison))">
            <xsl:call-template name="compare-results">
                <xsl:with-param name="previous-junit-data-path" select="$previous-junit-data-path"/>
                <xsl:with-param name="current-junit-data-path" select="$current-junit-data-path"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:result-document href="comparison-results.md" format="cr:markdown">
            <xsl:apply-templates select="$comparison/cr:comparison"/>
        </xsl:result-document>
        <xsl:sequence select="$comparison"/>
    </xsl:template>

    <!--
        Validate that $result-dir denotes a comparable runner result folder and
        return the path of the JUnit data directory within it, terminating the
        transformation with an actionable message otherwise.
    -->
    <xsl:function name="cr:validated-junit-data-path" as="xs:string">
        <xsl:param name="parameter-name" as="xs:string"/>
        <xsl:param name="result-dir" as="xs:string"/>
        <xsl:if test="$result-dir eq ''">
            <xsl:message terminate="yes" select="concat('Missing required parameter -D', $parameter-name, '. ', $cr:usage)"/>
        </xsl:if>
        <xsl:variable name="junit-data-path" select="concat($result-dir, '/junit/data')" as="xs:string"/>
        <xsl:variable name="testsuites" as="element(testsuite)*">
            <xsl:try>
                <xsl:sequence select="collection(concat($junit-data-path, '?select=*.xml'))/testsuite"/>
                <xsl:catch>
                    <xsl:message terminate="yes" select="concat('Cannot read -D', $parameter-name, '=', $result-dir, ': ', $err:description, ' ', $cr:expectation)"/>
                </xsl:catch>
            </xsl:try>
        </xsl:variable>
        <xsl:if test="empty($testsuites)">
            <xsl:message terminate="yes" select="concat('No JUnit test-suite reports found in ', $junit-data-path, ', so -D', $parameter-name, '=', $result-dir, ' cannot be compared. ', $cr:expectation)"/>
        </xsl:if>
        <xsl:sequence select="$junit-data-path"/>
    </xsl:function>

</xsl:stylesheet>
