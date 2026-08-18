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
    xmlns:ri="http://exist-db.org/exist-xqts-runner/runner-info"
    exclude-result-prefixes="xs ri"
    version="2.0">

    <xsl:output method="xml" version="1.0" omit-xml-declaration="no" indent="yes" encoding="UTF-8"/>


    <xsl:template name="compare-results" as="document-node(element(cr:comparison))">
        <xsl:param name="previous-junit-data-path" as="xs:string" required="yes"/>
        <xsl:param name="current-junit-data-path" as="xs:string" required="yes"/>
        <xsl:variable name="previous-summary" select="cr:summarise-results($previous-junit-data-path)" as="document-node(element(cr:results))"/>
        <xsl:variable name="current-summary" select="cr:summarise-results($current-junit-data-path)" as="document-node(element(cr:results))"/>
        <xsl:variable name="new-changes" as="element()+">
            <xsl:for-each select="('pass', 'skipped', 'failures', 'errors')">
                <xsl:sequence select="cr:new-changes($previous-summary/cr:results, $current-summary/cr:results, .)"/>
            </xsl:for-each>
        </xsl:variable>
        <xsl:variable name="previous-runner-info" as="document-node()?" select="cr:load-runner-info($previous-junit-data-path)"/>
        <xsl:variable name="current-runner-info" as="document-node()?" select="cr:load-runner-info($current-junit-data-path)"/>
        <xsl:document>
            <cr:comparison>
                <xsl:variable name="warnings" as="element(cr:warning)*" select="cr:drift-warnings($previous-runner-info, $current-runner-info)"/>
                <xsl:if test="exists($warnings)">
                    <cr:warnings>
                        <xsl:sequence select="$warnings"/>
                    </cr:warnings>
                </xsl:if>
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
        <xsl:variable name="pass" select="$tests - $skipped - $failures - $errors" as="xs:integer"/>
        <xsl:variable name="pass-pct" select="if ($tests eq 0) then 0 else (100 * $pass) div $tests" as="xs:decimal"/>
        <xsl:document>
            <cr:results tests="{$tests}" pass="{$pass}" pass-pct="{$pass-pct}" skipped="{$skipped}" failures="{$failures}" errors="{$errors}" time="{sum($testsuite/@time/xs:float(.))}">
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
        <xsl:choose>
            <xsl:when test="$attr-name eq 'pass'">
                <xsl:attribute name="pass-pct-delta" select="xs:decimal($current-results/@pass-pct) - xs:decimal($previous-results/@pass-pct)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:attribute name="{$attr-name}-pct" select="(($current-attr - $previous-attr) div $previous-attr) * 100"/>
            </xsl:otherwise>
        </xsl:choose>
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

    <!--
        Locate `runner-info.xml` next to the run's output dir. The junit data
        path is `<output>/junit/data`, so the metadata file sits two levels up.
        Returns the empty sequence if the file is missing or unparseable, so
        the warning step degrades to a no-op rather than failing the comparison.
    -->
    <xsl:function name="cr:load-runner-info" as="document-node()?">
        <xsl:param name="junit-data-path" as="xs:string"/>
        <xsl:variable name="uri" select="concat($junit-data-path, '/../../runner-info.xml')"/>
        <xsl:choose>
            <xsl:when test="doc-available($uri)">
                <xsl:sequence select="doc($uri)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="()"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <!--
        Emit a `cr:warning` per drift kind detected. The two kinds:
          - runner-drift: runner-JAR git-sha or sha256 differs between the runs
          - embedded-exist-core-drift: embedded `exist-core` version differs
            even when the runner JAR git-sha matches
        See https://github.com/eXist-db/exist/issues/6326.
    -->
    <xsl:function name="cr:drift-warnings" as="element(cr:warning)*">
        <xsl:param name="previous" as="document-node()?"/>
        <xsl:param name="current" as="document-node()?"/>
        <xsl:if test="exists($previous) and exists($current)">
            <xsl:variable name="prev-jar-sha" select="string($previous//ri:runner-jar/ri:git-sha[not(@unknown='true')])"/>
            <xsl:variable name="curr-jar-sha" select="string($current//ri:runner-jar/ri:git-sha[not(@unknown='true')])"/>
            <xsl:variable name="prev-jar-hash" select="string($previous//ri:runner-jar/ri:sha256[not(@unknown='true')])"/>
            <xsl:variable name="curr-jar-hash" select="string($current//ri:runner-jar/ri:sha256[not(@unknown='true')])"/>
            <xsl:variable name="prev-core-ver" select="string($previous//ri:embedded-exist-core/ri:version[not(@unknown='true')])"/>
            <xsl:variable name="curr-core-ver" select="string($current//ri:embedded-exist-core/ri:version[not(@unknown='true')])"/>

            <xsl:variable name="runner-drift" as="xs:boolean" select="
                ($prev-jar-sha ne '' and $curr-jar-sha ne '' and $prev-jar-sha ne $curr-jar-sha)
                or ($prev-jar-hash ne '' and $curr-jar-hash ne '' and $prev-jar-hash ne $curr-jar-hash)"/>

            <xsl:if test="$runner-drift">
                <cr:warning kind="runner-drift">
                    <cr:summary>Runner JAR build SHA or sha256 differs between the previous and current XQTS runs. Test deltas may include runner-side effects unrelated to this PR. See https://github.com/eXist-db/exist/issues/6326.</cr:summary>
                    <cr:previous>
                        <xsl:copy-of select="$previous//ri:runner-jar"/>
                    </cr:previous>
                    <cr:current>
                        <xsl:copy-of select="$current//ri:runner-jar"/>
                    </cr:current>
                </cr:warning>
            </xsl:if>

            <xsl:if test="not($runner-drift) and $prev-core-ver ne '' and $curr-core-ver ne '' and $prev-core-ver ne $curr-core-ver">
                <cr:warning kind="embedded-exist-core-drift">
                    <cr:summary>The runner JAR appears identical, but the embedded `exist-core` version differs between the runs. Test deltas may include `exist-core` shading effects rather than the eXist source under test.</cr:summary>
                    <cr:previous>
                        <xsl:copy-of select="$previous//ri:embedded-exist-core"/>
                    </cr:previous>
                    <cr:current>
                        <xsl:copy-of select="$current//ri:embedded-exist-core"/>
                    </cr:current>
                </cr:warning>
            </xsl:if>
        </xsl:if>
    </xsl:function>

</xsl:stylesheet>
