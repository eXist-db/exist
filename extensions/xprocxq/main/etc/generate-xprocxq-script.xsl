<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:t='http://xproc.org/ns/testsuite'
    xmlns:p="http://www.w3.org/ns/xproc"
    xmlns:c="http://www.w3.org/ns/xproc-step"
    xmlns:err="http://www.w3.org/ns/xproc-error"
    xmlns:xi="http://www.w3.org/2001/XInclude"
    >
<xsl:output method="text"/>

    <xsl:variable name="path-prefix" select="''"/>

 <xsl:template match="t:test-suite">
#!/bin/bash
    <xsl:apply-templates select="t:test"/>
</xsl:template>
    
    <xsl:template match="t:test">
bin/xprocxq test/<xsl:value-of select="concat(string(t:input[@port='source']/xi:include/@href),'')"/> test/xproc/basic/<xsl:value-of select="t:pipeline/xi:include/@href"/> &amp;&gt; <xsl:value-of select="t:output[@port='result']/xi:include/@href"/>

    </xsl:template>

    
</xsl:stylesheet>
