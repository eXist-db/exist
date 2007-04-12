<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" media-type="text/xml" omit-xml-declaration="no"/>
    <xsl:template match="/webservice">
        <SOAP-ENV:Envelope>
            <SOAP-ENV:Header/>
            <SOAP-ENV:Body>
                <xsl:variable name="functionName" select="/webservice/functions/function[1]/name"/>
                <xsl:variable name="namespaceURL" select="/webservice/URL"/>
                <xsl:variable name="type" select="/webservice/functions/function[1]/return/type"/>
                <xsl:element name="{concat($functionName, 'Response')}" namespace="{$namespaceURL}">
                    <xsl:element name="{concat($functionName, 'Result')}" namespace="{$namespaceURL}">
                        <xsl:choose>
                            <xsl:when test="/webservice/functions/function[1]/return/cardinality &lt;= 4">
                                <!-- atomic value -->
                                <xsl:copy-of select="/webservice/functions/function[1]/return/result/value/node()"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- sequence of values -->
                                <xsl:for-each select="/webservice/functions/function[1]/return/result/sequence/value">
                                    <xsl:element name="{substring-after($type, ':')}" namespace="{$namespaceURL}">
                                        <xsl:copy-of select="node()"/>
                                    </xsl:element>
                                </xsl:for-each>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>
                </xsl:element>
            </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    </xsl:template>
</xsl:stylesheet>