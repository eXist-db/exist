<?xml version="1.0" encoding="UTF-8"?>
<!-- 
    
    NOTE - XSLT Processor dependency 
    
    Xalan and Saxon <8.2 uses distinct() from EXSLT
    Saxon 8.2+ uses distinct-values() from XSLT 2.0
    
    We default to Xalan here as that is the default eXist XSLT Processor
    
    Version 20070413
    
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns="http://schemas.xmlsoap.org/wsdl/" xmlns:set="http://exslt.org/sets" version="2.0">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" media-type="text/xml" omit-xml-declaration="no"/>
    <xsl:template match="/webservice">
        <xsl:variable name="webserviceName" select="name" as="xs:string"/>
        <xsl:variable name="webserviceURL" select="URL" as="xs:string"/>
        <definitions name="{$webserviceName}" targetNamespace="{$webserviceURL}">
            <types>
                <xs:schema elementFormDefault="qualified" targetNamespace="{$webserviceURL}">
                    <!-- creates array types for parameters and returns -->
                    <xsl:for-each select="set:distinct(functions/function/parameters/parameter[cardinality &gt;= 4]/type | functions/function/return[cardinality &gt;= 4]/type)">
                        <xs:complexType name="{concat('arrayOf', translate(., ':', '_'))}">
                            <xs:sequence>
                                <xs:element minOccurs="0" maxOccurs="unbounded" name="{substring-after(., ':')}" nillable="true" type="{.}"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xsl:for-each>
                    <xsl:for-each select="functions/function">
                        <xsl:variable name="funName" select="name" as="xs:string"/>
                        <xs:element name="{$funName}">
                            <xsl:if test="parameters/parameter">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xsl:for-each select="parameters/parameter">
                                            <xsl:variable name="name">
                                                <xsl:choose>
                                                    <xsl:when test="name != ''">
                                                        <xsl:value-of select="name"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <xsl:value-of select="concat('arg', position())"/>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </xsl:variable>
                                            <xsl:variable name="cardinality" select="cardinality"/>
                                            <xsl:variable name="type">
                                                <xsl:choose>
                                                    <xsl:when test="type = 'item'">
                                                        <xsl:value-of select="'xs:anyType'"/>
                                                    </xsl:when>
                                                    <xsl:when test="type = 'element'">
                                                        <xsl:value-of select="'xs:anyType'"/>
                                                    </xsl:when>
                                                    <xsl:when test="type = 'node'">
                                                        <xsl:value-of select="'xs:anyType'"/>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <xsl:value-of select="type"/>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </xsl:variable>
                                            <xsl:choose>
                                                <xsl:when test="$cardinality &lt; 3">
                                                    <!-- ZERO, ONE  -->
                                                    <xs:element name="{$name}" type="{$type}"/>
                                                </xsl:when>
                                                <xsl:when test="$cardinality = 3">
                                                    <!-- ZERO_OR_ONE -->
                                                    <xs:element name="{$name}" type="{$type}" minOccurs="0" maxOccurs="1"/>
                                                </xsl:when>
                                                <xsl:when test="$cardinality = 4 or $cardinality = 6">
                                                    <!-- MANY, ONE_OR_MANY -->
                                                    <xs:element name="{$name}" type="tns:arrayOf{translate($type,':','_')}" minOccurs="1" maxOccurs="1"/>
                                                </xsl:when>
                                                <xsl:when test="$cardinality = 7">
                                                    <!-- ZERO_OR_MORE -->
                                                    <xs:element name="{$name}" type="tns:arrayOf{translate($type,':','_')}" minOccurs="0" maxOccurs="1"/>
                                                </xsl:when>
                                            </xsl:choose>
                                        </xsl:for-each>
                                    </xs:sequence>
                                </xs:complexType>
                            </xsl:if>
                        </xs:element>
                        <xs:element name="{concat($funName, 'Response')}">
                            <xs:complexType>
                                <xs:sequence>
                                    <xsl:variable name="name" select="concat($funName, 'Result')"/>
                                    <xsl:variable name="cardinality" select="return/cardinality"/>
                                    <xsl:variable name="type">
                                        <xsl:choose>
                                            <xsl:when test="return/type = 'item'">
                                                <xsl:value-of select="'xs:anyType'"/>
                                            </xsl:when>
                                            <xsl:when test="return/type = 'element'">
                                                <xsl:value-of select="'xs:anyType'"/>
                                            </xsl:when>
                                            <xsl:when test="return/type = 'node'">
                                                <xsl:value-of select="'xs:anyType'"/>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:value-of select="return/type"/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:variable>
                                    <xsl:choose>
                                        <xsl:when test="$cardinality &lt; 3">
                                            <!-- ZERO, ONE  -->
                                            <xs:element name="{$name}" type="{$type}"/>
                                        </xsl:when>
                                        <xsl:when test="$cardinality = 3">
                                            <!-- ZERO_OR_ONE -->
                                            <xs:element name="{$name}" type="{$type}" minOccurs="0" maxOccurs="1"/>
                                        </xsl:when>
                                        <xsl:when test="$cardinality = 4 or $cardinality = 6">
                                            <!-- MANY, ONE_OR_MANY -->
                                            <xs:element name="{$name}" type="tns:arrayOf{translate($type,':','_')}" minOccurs="1" maxOccurs="1"/>
                                        </xsl:when>
                                        <xsl:when test="$cardinality = 7">
                                            <!-- ZERO_OR_MORE -->
                                            <xs:element name="{$name}" type="tns:arrayOf{translate($type,':','_')}" minOccurs="0" maxOccurs="1"/>
                                        </xsl:when>
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