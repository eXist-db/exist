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
    Prepares conf.xml for vector embeddings (exist-distribution `vector` profile; the Docker
    image inherits the result).

    Input: src/main/config/conf.xml.
    Param `models`: whitespace-separated list of model specs, each `id|dimension|baseUrl`,
      where baseUrl is the download location that serves `model.onnx` and `tokenizer.json`
      (same convention as ModelPathResolver's http(s) model paths).

    Output (primary): conf.xml with
      - the `enabled="no"` entry for the vector XQuery module removed (so the SPI registration applies)
      - <vector-models enabled="yes"> listing every model at path onnx-models/<id>
    Output (secondary, `fetch-script` param): an Ant build file whose `fetch` target downloads
      each model into ${cache.dir}/<id> and copies it to ${dest.dir}/<id>.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs"
    version="3.0">

    <xsl:param name="models" as="xs:string" select="''"/>
    <xsl:param name="fetch-script" as="xs:string" required="yes"/>

    <xsl:output method="xml" indent="no" omit-xml-declaration="no"/>

    <xsl:variable name="vector-module-uri" select="'http://exist-db.org/xquery/vector'"/>

    <xsl:variable name="specs" as="element(model)*">
        <xsl:for-each select="tokenize(normalize-space($models), ' ')[. ne '']">
            <xsl:variable name="parts" select="tokenize(., '\|')"/>
            <xsl:if test="count($parts) ne 3">
                <xsl:message terminate="yes">vector model spec '<xsl:value-of select="."/>' must be id|dimension|baseUrl</xsl:message>
            </xsl:if>
            <xsl:if test="not(matches($parts[1], '^[A-Za-z0-9._-]+$'))">
                <xsl:message terminate="yes">vector model id '<xsl:value-of select="$parts[1]"/>' may only contain A-Z a-z 0-9 . _ -</xsl:message>
            </xsl:if>
            <xsl:if test="not($parts[2] castable as xs:positiveInteger)">
                <xsl:message terminate="yes">vector model '<xsl:value-of select="$parts[1]"/>': dimension '<xsl:value-of select="$parts[2]"/>' is not a positive integer</xsl:message>
            </xsl:if>
            <xsl:if test="not(matches($parts[3], '^https?://'))">
                <xsl:message terminate="yes">vector model '<xsl:value-of select="$parts[1]"/>': download location '<xsl:value-of select="$parts[3]"/>' must be an http(s) URL</xsl:message>
            </xsl:if>
            <model id="{$parts[1]}" dimension="{$parts[2]}" url="{replace($parts[3], '/+$', '')}"/>
        </xsl:for-each>
    </xsl:variable>

    <xsl:template match="/">
        <xsl:if test="empty($specs)">
            <xsl:message terminate="yes">the vector profile needs at least one model in vector.models</xsl:message>
        </xsl:if>
        <xsl:variable name="dupes" select="$specs/@id[count(index-of($specs/@id, .)) gt 1]"/>
        <xsl:if test="exists($dupes)">
            <xsl:message terminate="yes">duplicate vector model id(s): <xsl:value-of select="distinct-values($dupes)"/></xsl:message>
        </xsl:if>
        <xsl:apply-templates/>
        <xsl:result-document href="{$fetch-script}" method="xml" indent="yes">
            <project name="fetch-vector-models" default="fetch">
                <target name="fetch">
                    <xsl:for-each select="$specs">
                        <echo message="vector model {@id} ({@dimension}d) from {@url}"/>
                        <mkdir dir="${{cache.dir}}/{@id}"/>
                        <get src="{@url}/model.onnx" dest="${{cache.dir}}/{@id}/model.onnx" skipexisting="true"/>
                        <get src="{@url}/tokenizer.json" dest="${{cache.dir}}/{@id}/tokenizer.json" skipexisting="true"/>
                        <copy todir="${{dest.dir}}/{@id}">
                            <fileset dir="${{cache.dir}}/{@id}" includes="model.onnx,tokenizer.json"/>
                        </copy>
                    </xsl:for-each>
                </target>
            </project>
        </xsl:result-document>
    </xsl:template>

    <!-- drop the disabling entry; the module is SPI-registered once exist-vector is on the classpath -->
    <xsl:template match="module[@uri eq $vector-module-uri][@enabled eq 'no']"/>

    <xsl:template match="vector-models">
        <vector-models enabled="yes">
            <xsl:for-each select="$specs">
                <model id="{@id}" path="onnx-models/{@id}" dimension="{@dimension}"/>
            </xsl:for-each>
        </vector-models>
    </xsl:template>

    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
