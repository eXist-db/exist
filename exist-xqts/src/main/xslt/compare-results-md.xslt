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
    Renders the XML produced by compare-results.xslt into a GitHub-flavoured
    Markdown report suitable for posting as a pull-request comment.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:cr="http://exist-db.org/exist-xqts/compare-results"
    exclude-result-prefixes="xs cr"
    version="2.0">

    <xsl:output method="text" encoding="UTF-8"/>

    <!-- Maximum number of test cases listed per detail section -->
    <xsl:param name="max-listed" as="xs:integer" select="50"/>

    <!-- Display names for the two compared runs, e.g. "`develop`" and "this run" -->
    <xsl:param name="previous-label" as="xs:string" select="'previous'"/>
    <xsl:param name="current-label" as="xs:string" select="'current'"/>

    <xsl:variable name="nl" as="xs:string" select="'&#10;'"/>

    <!-- Format an integer with thousands separators, e.g. 24025 -> 24,025 -->
    <xsl:function name="cr:int" as="xs:string">
        <xsl:param name="n" as="xs:anyAtomicType?"/>
        <xsl:sequence select="format-number(xs:double($n), '#,##0')"/>
    </xsl:function>

    <!-- Format an integer delta with an explicit sign, e.g. +15 / -3 / 0 -->
    <xsl:function name="cr:signed-int" as="xs:string">
        <xsl:param name="n" as="xs:anyAtomicType?"/>
        <xsl:variable name="v" select="xs:double($n)"/>
        <xsl:sequence select="if ($v gt 0) then concat('+', cr:int($v)) else cr:int($v)"/>
    </xsl:function>

    <!-- Format a decimal delta with an explicit sign and two fraction digits -->
    <xsl:function name="cr:signed-dec" as="xs:string">
        <xsl:param name="n" as="xs:anyAtomicType?"/>
        <xsl:variable name="v" select="xs:double($n)"/>
        <xsl:sequence select="if ($v gt 0)
            then concat('+', format-number($v, '#,##0.00'))
            else format-number($v, '#,##0.00')"/>
    </xsl:function>

    <!-- Directional emoji for a delta given whether an increase is good -->
    <xsl:function name="cr:trend" as="xs:string">
        <xsl:param name="delta" as="xs:anyAtomicType?"/>
        <xsl:param name="increase-is-good" as="xs:boolean"/>
        <xsl:variable name="v" select="xs:double($delta)"/>
        <xsl:sequence select="
            if ($v eq 0) then '➖'
            else if (($v gt 0) eq $increase-is-good) then '🟢'
            else '🔴'"/>
    </xsl:function>

    <xsl:template match="/cr:comparison">
        <xsl:variable name="prev" select="cr:previous/cr:results" as="element(cr:results)"/>
        <xsl:variable name="curr" select="cr:current/cr:results" as="element(cr:results)"/>
        <xsl:variable name="chg" select="cr:change/cr:results" as="element(cr:results)"/>

        <xsl:value-of select="concat('## 📊 XQTS result comparison', $nl, $nl)"/>
        <xsl:value-of select="concat('Comparison of ', $current-label, ' against ', $previous-label, '.', $nl, $nl)"/>

        <!-- Environment-drift warnings detected by compare-results.xslt -->
        <xsl:for-each select="cr:warnings/cr:warning">
            <xsl:value-of select="concat('> [!WARNING]', $nl, '> ', normalize-space(cr:summary), $nl, $nl)"/>
        </xsl:for-each>

        <!-- Top-level summary table -->
        <xsl:value-of select="concat('| Metric | ', $previous-label, ' | ', $current-label, ' | Change |', $nl)"/>
        <xsl:value-of select="concat('| :--- | ---: | ---: | ---: |', $nl)"/>

        <!-- Passed (with percentage) -->
        <xsl:value-of select="concat(
            '| ', cr:trend($chg/@pass, true()), ' Passed ',
            '| ', cr:int($prev/@pass), ' (', format-number(xs:double($prev/@pass-pct), '0.00'), '%) ',
            '| ', cr:int($curr/@pass), ' (', format-number(xs:double($curr/@pass-pct), '0.00'), '%) ',
            '| ', cr:signed-int($chg/@pass), ' (', cr:signed-dec($chg/@pass-pct-delta), ' pp) ',
            '|', $nl)"/>

        <!-- Failures -->
        <xsl:value-of select="concat(
            '| ', cr:trend($chg/@failures, false()), ' Failures ',
            '| ', cr:int($prev/@failures),
            ' | ', cr:int($curr/@failures),
            ' | ', cr:signed-int($chg/@failures),
            ' |', $nl)"/>

        <!-- Errors -->
        <xsl:value-of select="concat(
            '| ', cr:trend($chg/@errors, false()), ' Errors ',
            '| ', cr:int($prev/@errors),
            ' | ', cr:int($curr/@errors),
            ' | ', cr:signed-int($chg/@errors),
            ' |', $nl)"/>

        <!-- Skipped -->
        <xsl:value-of select="concat(
            '| ', cr:trend($chg/@skipped, false()), ' Skipped ',
            '| ', cr:int($prev/@skipped),
            ' | ', cr:int($curr/@skipped),
            ' | ', cr:signed-int($chg/@skipped),
            ' |', $nl)"/>

        <!-- Total tests -->
        <xsl:value-of select="concat(
            '| 🧪 Total tests ',
            '| ', cr:int($prev/@tests),
            ' | ', cr:int($curr/@tests),
            ' | ', cr:signed-int($chg/@tests),
            ' |', $nl, $nl)"/>

        <!-- Newly changed test cases relative to develop -->
        <xsl:variable name="new-pass" select="cr:change/cr:new/cr:pass/testcase" as="element()*"/>
        <xsl:variable name="new-failures" select="cr:change/cr:new/cr:failures/testcase" as="element()*"/>
        <xsl:variable name="new-errors" select="cr:change/cr:new/cr:errors/testcase" as="element()*"/>
        <xsl:variable name="new-skipped" select="cr:change/cr:new/cr:skipped/testcase" as="element()*"/>

        <xsl:value-of select="concat(
            'Relative to ', $previous-label, ': ',
            '**', cr:int(count($new-pass)), '** newly passing, ',
            '**', cr:int(count($new-failures)), '** newly failing, ',
            '**', cr:int(count($new-errors)), '** new errors, ',
            '**', cr:int(count($new-skipped)), '** newly skipped.', $nl, $nl)"/>

        <xsl:call-template name="cr:detail-list">
            <xsl:with-param name="heading" select="'🔴 Newly failing tests'"/>
            <xsl:with-param name="cases" select="$new-failures"/>
        </xsl:call-template>
        <xsl:call-template name="cr:detail-list">
            <xsl:with-param name="heading" select="'💥 New errors'"/>
            <xsl:with-param name="cases" select="$new-errors"/>
        </xsl:call-template>
        <xsl:call-template name="cr:detail-list">
            <xsl:with-param name="heading" select="'🟢 Newly passing tests'"/>
            <xsl:with-param name="cases" select="$new-pass"/>
        </xsl:call-template>

        <xsl:value-of select="concat($nl, '&lt;sub>Runtime: ',
            format-number(xs:double($curr/@time), '#,##0.0'), 's (',
            cr:signed-dec($chg/@time), 's vs ', $previous-label, ').&lt;/sub>', $nl)"/>
    </xsl:template>

    <!-- Emit a collapsible <details> block listing test-case names -->
    <xsl:template name="cr:detail-list">
        <xsl:param name="heading" as="xs:string"/>
        <xsl:param name="cases" as="element()*"/>
        <xsl:if test="exists($cases)">
            <xsl:value-of select="concat('&lt;details>', $nl,
                '&lt;summary>', $heading, ' (', cr:int(count($cases)), ')&lt;/summary>', $nl, $nl)"/>
            <xsl:for-each select="$cases[position() le $max-listed]">
                <xsl:value-of select="concat('- `', @name, '`', $nl)"/>
            </xsl:for-each>
            <xsl:if test="count($cases) gt $max-listed">
                <xsl:value-of select="concat('- … and ', cr:int(count($cases) - $max-listed), ' more', $nl)"/>
            </xsl:if>
            <xsl:value-of select="concat($nl, '&lt;/details>', $nl, $nl)"/>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
