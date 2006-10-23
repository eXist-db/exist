<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns="http://schemas.xmlsoap.org/wsdl/" version="2.0">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" media-type="text/xml" omit-xml-declaration="no"/>
    <xsl:template match="/webservice">
        <xsl:variable name="webserviceName" select="name" as="xs:string"/>
        <xsl:variable name="webserviceURL" select="URL" as="xs:string"/>
        <definitions name="{$webserviceName}" targetNamespace="{$webserviceURL}">
            <types>
                <xs:schema elementFormDefault="qualified" targetNamespace="{$webserviceURL}">
                    <xsl:for-each select="functions/function">
                        <xsl:variable name="funName" select="name" as="xs:string"/>
                        <xs:element name="{$funName}">
                            <xsl:for-each select="parameters/parameter">
                            <xsl:variable name="cardinality" select="cardinality"/>
                                <xsl:variable name="type" select="type"/>
                                <xs:complexType>
                                    <xs:sequence>
                                    <xsl:choose>
                                        <xsl:when test="$cardinality &lt; 4">
                                             <!-- single value of type -->
                                              <xs:element name="{concat('arg', position())}" type="{$type}"/>   
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <!-- array of type -->
                                            <xs:element name="{concat('arg', position())}" type="{$type}" minOccurs="1" maxOccurs="unbounded"/>
                                        </xsl:otherwise>
                                        </xsl:choose>
                                    </xs:sequence>
                                </xs:complexType>
                            </xsl:for-each>
                        </xs:element>
                        <xs:element name="{concat($funName, 'Response')}">
                            <xs:complexType>
                                <xs:sequence>
                                    <xsl:variable name="cardinality" select="return/cardinality"/>
                                    <xsl:variable name="type" select="return/type"/>
                                    <xsl:choose>
                                        <xsl:when test="$cardinality &lt; 4">
                                            <!-- single value of type -->
                                            <xs:element name="{concat($funName, 'Result')}" type="{$type}"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <!-- array of type -->
                                            <xs:element name="result" type="{$type}" minOccurs="1" maxOccurs="unbounded"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xs:sequence>
                            </xs:complexType>
                        </xs:element>
                    </xsl:for-each>
                </xs:schema>
            </types>
            <xsl:for-each select="functions/function">
                <xsl:variable name="funName" select="name" as="xs:string"/>
                <message name="{concat($funName, 'SoapRequest')}">
                    <part name="parameters" element="{concat('tns:',$funName)}"/>
                </message>
                <message name="{concat($funName, 'SoapResponse')}">
                    <part name="parameters" element="{concat('tns:',$funName,'Response')}"/>
                </message>
            </xsl:for-each>
            <portType name="{concat($webserviceName, 'SoapType')}">
                <xsl:for-each select="functions/function">
                    <xsl:variable name="funName" select="name" as="xs:string"/>
                    <operation name="{$funName}">
                        <documentation>
                            <xsl:value-of select="description"/>
                        </documentation>
                        <input message="{concat('tns:', $funName ,'SoapRequest')}"/>
                        <output message="{concat('tns:', $funName ,'SoapResponse')}"/>
                    </operation>
                </xsl:for-each>
            </portType>
            <binding name="{concat($webserviceName, 'SoapBinding')}" type="{concat('tns:', $webserviceName, 'SoapType')}">
                <soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/>
                <xsl:for-each select="functions/function">
                    <xsl:variable name="funName" select="name" as="xs:string"/>
                    <operation name="{$funName}">
                        <soap:operation soapAction="" style="document"/>
                        <input>
                            <soap:body use="literal"/>
                        </input>
                        <output>
                            <soap:body use="literal"/>
                        </output>
                    </operation>
                </xsl:for-each>
            </binding>
            <service name="{$webserviceName}">
                <documentation>
                    <xsl:value-of select="description"/>
                </documentation>
                <port name="{concat($webserviceName, 'Soap')}" binding="tns:{concat($webserviceName, 'SoapBinding')}">
                    <soap:address location="{$webserviceURL}"/>
                </port>
            </service>
        </definitions>
    </xsl:template>
</xsl:stylesheet>