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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:cr="http://exist-db.org/exist-xqts/compare-results"
    exclude-result-prefixes="xs"
    version="2.0">

    <xsl:param name="xqts.previous.junit-data-path" as="xs:string" required="yes"/>
    <xsl:param name="xqts.current.junit-data-path" as="xs:string" required="yes"/>


    <xsl:output method="xml" version="1.0" omit-xml-declaration="no" indent="yes" encoding="UTF-8"/>


    <xsl:template name="compare-results" as="document-node(element(cr:comparison))">
        <xsl:variable name="previous-summary" select="cr:summarise-results($xqts.previous.junit-data-path)" as="document-node(element(cr:results))"/>
        <xsl:variable name="current-summary" select="cr:summarise-results($xqts.current.junit-data-path)" as="document-node(element(cr:results))"/>
        <xsl:variable name="new-changes" as="element()+">
            <xsl:for-each select="('pass', 'skipped', 'failures', 'errors')">
                <xsl:sequence select="cr:new-changes($previous-summary/cr:results, $current-summary/cr:results, .)"/>
            </xsl:for-each>
        </xsl:variable>
        <xsl:document>
            <cr:comparison>
                <cr:previous>
                    <xsl:copy select="$previous-summary/cr:results">
                        <xsl:copy-of select="@*"/>
                    </xsl:copy>
                </cr:previous>
                <cr:current>
                    <xsl:copy select="$current-summary/cr:results">
                        <xsl:copy-of select="@*"/>
                    </xsl:copy>
                </cr:current>
                <cr:change>
                    <cr:results>
                        <xsl:for-each select="('tests', 'pass', 'skipped', 'failures', 'errors')">
                            <xsl:variable name="attr-name" select="." as="xs:string"/>
                            <xsl:sequence select="cr:calculate-change($previous-summary/cr:results, $current-summary/cr:results, $attr-name)"/>
                            <xsl:if test="$attr-name = ('pass', 'skipped', 'failures', 'errors')">
                                <xsl:attribute name="{$attr-name}-new" select="count($new-changes[local-name(.) eq $attr-name]/testcase)"/>
                            </xsl:if>
                        </xsl:for-each>
                        <xsl:sequence select="cr:calculate-change($previous-summary/cr:results, $current-summary/cr:results, 'time')"/>
                    </cr:results>
                    <cr:new>
                        <xsl:sequence select="$new-changes"/>
                    </cr:new>
                </cr:change>
            </cr:comparison>
        </xsl:document>
    </xsl:template>

    <xsl:function name="cr:summarise-results" as="document-node(element(cr:results))">
        <xsl:param name="junit-data-path" as="xs:string" required="yes"/>
        <xsl:variable name="collection-uri" select="concat($junit-data-path, '?select=*.xml')"/>
        <xsl:variable name="testsuite" select="collection($collection-uri)/testsuite"/>
        <xsl:variable name="tests" select="sum($testsuite/@tests/xs:integer(.))" as="xs:integer"/>
        <xsl:variable name="skipped" select="sum($testsuite/@skipped/xs:integer(.))" as="xs:integer"/>
        <xsl:variable name="failures" select="sum($testsuite/@failures/xs:integer(.))" as="xs:integer"/>
        <xsl:variable name="errors" select="sum($testsuite/@errors/xs:integer(.))" as="xs:integer"/>
        <xsl:document>
            <cr:results tests="{$tests}" pass="{$tests - $skipped - $failures - $errors}" skipped="{$skipped}" failures="{$failures}" errors="{$errors}" time="{sum($testsuite/@time/xs:float(.))}">
                <cr:skipped>
                    <xsl:sequence select="$testsuite/testcase[skipped]"/>
                </cr:skipped>
                <cr:failures>
                    <xsl:sequence select="$testsuite/testcase[failure]"/>
                </cr:failures>
                <cr:errors>
                    <xsl:sequence select="$testsuite/testcase[error]"/>
                </cr:errors>
                <cr:pass>
                    <xsl:sequence select="$testsuite/testcase[empty(skipped)][empty(failure)][empty(error)]"/>
                </cr:pass>
            </cr:results>
        </xsl:document>
    </xsl:function>

    <xsl:function name="cr:calculate-change" as="attribute()+">
        <xsl:param name="previous-results" as="element(cr:results)" required="yes"/>
        <xsl:param name="current-results" as="element(cr:results)" required="yes"/>
        <xsl:param name="attr-name" as="xs:string" required="yes"/>
        
        <xsl:variable name="previous-attr" select="$previous-results/@*[local-name(.) eq $attr-name]"/>
        <xsl:variable name="current-attr" select="$current-results/@*[local-name(.) eq $attr-name]"/>
        
        <xsl:attribute name="{$attr-name}" select="$current-attr - $previous-attr"/>
        <xsl:attribute name="{$attr-name}-pct" select="(($current-attr - $previous-attr) div $previous-attr) * 100"/>
    </xsl:function>

    <xsl:function name="cr:new-changes">
        <xsl:param name="previous-results" as="element(cr:results)" required="yes"/>
        <xsl:param name="current-results" as="element(cr:results)" required="yes"/>
        <xsl:param name="attr-name" as="xs:string" required="yes"/>
        <xsl:variable name="elem-name" as="xs:QName" select="xs:QName(concat('cr:', $attr-name))"/>
        <xsl:variable name="previous-results-names" as="xs:string*" select="$previous-results/element()[node-name(.) eq $elem-name]/testcase/@name/string(.)"/>
        <xsl:element name="cr:{$attr-name}">
            <xsl:apply-templates mode="simple" select="$current-results/element()[node-name(.) eq $elem-name]/testcase[not(@name = $previous-results-names)]"/>
        </xsl:element>
    </xsl:function>

    <xsl:template match="testcase" mode="simple">
        <xsl:copy>
            <xsl:copy-of select="@name"/>
            <xsl:copy-of select="failure|error"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>