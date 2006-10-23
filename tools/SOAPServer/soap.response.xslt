<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/" version="2.0">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" media-type="text/xml" omit-xml-declaration="no"/>
    <xsl:template match="/webservice">
        <SOAP-ENV:Envelope>
            <SOAP-ENV:Body>
                <xsl:variable name="functionName" select="/webservice/functions/function[1]/name"/>
                <xsl:variable name="namespaceURL" select="/webservice/URL"/>
                <xsl:element name="{concat($functionName, 'Response')}" namespace="{$namespaceURL}">
                    <xsl:element name="{concat(functionName, 'Result')}" namespace="{$namespaceURL}">
                        <xsl:value-of select="/webservice/functions/function[1]/return/result"/>
                    </xsl:element>
                </xsl:element>        
            </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
    </xsl:template>
</xsl:stylesheet>