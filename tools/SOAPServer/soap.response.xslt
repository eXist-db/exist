<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="2.0">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" media-type="text/xml" omit-xml-declaration="no"/>
    <xsl:param name="isDocumentLiteral" as="xs:string">true</xsl:param>
    <xsl:template match="/">
        <SOAP-ENV:Envelope>
            <SOAP-ENV:Header/>
            <SOAP-ENV:Body>
                <xsl:apply-templates select="webservice"/>
            </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    </xsl:template>
    <xsl:template match="webservice">
        <xsl:variable name="functionName" select="functions/function[1]/name"/>
        <xsl:variable name="namespaceURL" select="URL"/>
        <xsl:variable name="type" select="functions/function[1]/return/type"/>
        <xsl:variable name="responseName">
            <xsl:choose>
                <xsl:when test="$isDocumentLiteral = 'true'">
                    <xsl:value-of select="concat($functionName, 'Response')" />
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$functionName" />
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:element name="{$responseName}" namespace="{$namespaceURL}">
            <xsl:element name="{concat($functionName, 'Result')}" namespace="{$namespaceURL}">
                <xsl:choose>
                    <xsl:when test="functions/function[1]/return/cardinality &lt;= 4">
                        <!-- atomic value -->
                        <xsl:copy-of select="functions/function[1]/return/result/value/node()"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- sequence of values -->
                        <xsl:for-each select="functions/function[1]/return/result/sequence/value">
                            <xsl:element name="{substring-after($type, ':')}" namespace="{$namespaceURL}">
                                <xsl:copy-of select="node()"/>
                            </xsl:element>
                        </xsl:for-each>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:element>
        </xsl:element>
    </xsl:template>
</xsl:stylesheet>