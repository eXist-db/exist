<?xml version="1.0" encoding="UTF-8"?>
<!--
    Schema governance, single-pass. Reads the context document produced by
    .github/scripts/prepare-governance-context.sh (pure git plumbing — base
    revision, changed paths, BASE-revision copies of each schema/*.xsd) plus
    pom.xml's validate-canonical-instances validationSets, and:

      1. pairs each native XSD with its canonical template(s),
      2. for any pair touched in this diff, compares old vs current
         xs:schema/@version and errors if it didn't move,
      3. does the same for any orphan schema/*.xsd not listed as a pair
         (e.g. schema-version-type.xsd),
      4. emits GitHub Actions ::error:: annotations and fails the build
         (xsl:message terminate="yes") if anything is wrong.

    Run via: mvn xml:transform@schema-governance (see root pom.xml — bound
    to phase=none, so it never runs on an ordinary build).
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:g="http://exist-db.org/ns/schema-governance"
    exclude-result-prefixes="xs">

    <xsl:output method="xml" indent="yes"/>

    <xsl:variable name="ctx" select="/g:ctx"/>
    <xsl:variable name="workspace" select="string($ctx/@workspace)"/>
    <xsl:variable name="pom-uri" select="concat($workspace, '/pom.xml')"/>
    <xsl:variable name="changed" select="tokenize(unparsed-text(string($ctx/@changed-file)), '\n')[. != '']"/>

    <xsl:variable name="sets" select="document($pom-uri)//*:execution[*:id = 'validate-canonical-instances']
        /*:configuration/*:validationSets/*:validationSet"/>

    <xsl:function name="g:rel" as="xs:string">
        <xsl:param name="p" as="xs:string"/>
        <xsl:sequence select="substring-after($p, '${project.basedir}/')"/>
    </xsl:function>

    <!-- Compares old (BASE) vs current xs:schema/@version for one schema
         path; empty sequence means "no problem found". -->
    <xsl:function name="g:check-version" as="element(g:error)?">
        <xsl:param name="schema" as="xs:string"/>
        <xsl:param name="what" as="xs:string"/>
        <xsl:variable name="head-uri" select="concat($workspace, '/', $schema)"/>
        <xsl:variable name="head" select="if (doc-available($head-uri))
            then document($head-uri)/xs:schema/@version else ()"/>
        <xsl:variable name="base-uri" select="concat(string($ctx/@base-dir), '/', tokenize($schema, '/')[last()])"/>
        <xsl:variable name="old" select="if (doc-available($base-uri))
            then document($base-uri)/xs:schema/@version else ()"/>
        <xsl:choose>
            <xsl:when test="empty($head)">
                <g:error path="{$schema}" message="Missing xs:schema/@version (see schema/README.md)"/>
            </xsl:when>
            <xsl:when test="empty($old)"/> <!-- new at BASE, or BASE copy unreadable: nothing to compare -->
            <xsl:when test="$old = $head">
                <g:error path="{$schema}" message="{concat($what, ' changed but @version is still &quot;', $head,
                    '&quot;. Bump xs:schema/@version (see schema/README.md).')}"/>
            </xsl:when>
        </xsl:choose>
    </xsl:function>

    <xsl:template match="/">
        <xsl:if test="empty($sets)">
            <xsl:message terminate="yes">governance.xsl: no xml-maven-plugin execution with
                id='validate-canonical-instances' found in <xsl:value-of select="$pom-uri"/> — the execution id
                has drifted, or $pom-uri is wrong. Schema governance cannot run blind.</xsl:message>
        </xsl:if>

        <xsl:variable name="errors" as="element(g:error)*">
            <xsl:for-each select="$sets">
                <xsl:variable name="schema" select="g:rel(string(*:systemId))"/>
                <xsl:for-each select="*:includes/*:include">
                    <xsl:variable name="template" select="concat(g:rel(string(../../*:dir)), '/', string(.))"/>
                    <xsl:if test="$schema = $changed or $template = $changed">
                        <xsl:sequence select="g:check-version($schema,
                            if ($schema = $changed and $template = $changed) then 'Schema and template'
                            else if ($schema = $changed) then 'Schema'
                            else concat('Template ', $template))"/>
                    </xsl:if>
                </xsl:for-each>
            </xsl:for-each>
            <xsl:variable name="paired-schemas" as="xs:string*">
                <xsl:for-each select="$sets">
                    <xsl:sequence select="g:rel(string(*:systemId))"/>
                </xsl:for-each>
            </xsl:variable>
            <xsl:for-each select="$changed[starts-with(., 'schema/') and ends-with(., '.xsd')
                and not(. = $paired-schemas)]">
                <xsl:sequence select="g:check-version(., 'Schema')"/>
            </xsl:for-each>
        </xsl:variable>

        <g:report status="{if (exists($errors)) then 'failed' else 'passed'}" base="{string($ctx/@base-ref)}">
            <xsl:sequence select="$errors"/>
        </g:report>

        <xsl:for-each select="$errors">
            <xsl:message><xsl:value-of select="concat('::error file=', @path, '::', @message)"/></xsl:message>
        </xsl:for-each>
        <xsl:if test="exists($errors)">
            <xsl:message terminate="yes">Schema governance failed (<xsl:value-of select="count($errors)"/> error(s) above).</xsl:message>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>
