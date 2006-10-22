<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" version="2.0">
    <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" doctype-public="-//W3C//DTD XHTML 1.1//EN" doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd" media-type="text/xhtml" omit-xml-declaration="no"/>
    <xsl:param name="function" as="xs:string"/>
    <xsl:template match="/webservice">
        <xsl:variable name="webserviceName" select="name" as="xs:string"/>
        <xsl:variable name="webserviceURL" select="URL" as="xs:string"/>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
            <head>
                <style type="text/css">
                    BODY { color: #000000; background-color: white; font-family: Verdana; margin-left: 0px; margin-top: 0px; }
                    #content { margin-left: 30px; font-size: .70em; padding-bottom: 2em; }
                    A:link { color: #336699; font-weight: bold; text-decoration: underline; }
                    A:visited { color: #6699cc; font-weight: bold; text-decoration: underline; }
                    A:active { color: #336699; font-weight: bold; text-decoration: underline; }
                    A:hover { color: cc3300; font-weight: bold; text-decoration: underline; }
                    P { color: #000000; margin-top: 0px; margin-bottom: 12px; font-family: Verdana; }
                    pre { background-color: #e5e5cc; padding: 5px; font-family: Courier New; font-size: x-small; margin-top: -5px; border: 1px #f0f0e0 solid; }
                    td { color: #000000; font-family: Verdana; font-size: .7em; }
                    h2 { font-size: 1.5em; font-weight: bold; margin-top: 25px; margin-bottom: 10px; border-top: 1px solid #003366; margin-left: -15px; color: #003366; }
                    h3 { font-size: 1.1em; color: #000000; margin-left: -15px; margin-top: 10px; margin-bottom: 10px; }
                    ul { margin-top: 10px; margin-left: 20px; }
                    ol { margin-top: 10px; margin-left: 20px; }
                    li { margin-top: 10px; color: #000000; }
                    font.value { color: darkblue; font: bold; }
                    font.key { color: darkgreen; font: bold; }
                    .heading1 { color: #ffffff; font-family: Tahoma; font-size: 26px; font-weight: normal; background-color: #003366; margin-top: 0px; margin-bottom: 0px; margin-left: -30px; padding-top: 10px; padding-bottom: 3px; padding-left: 15px; width: 105%; }
                    .button { background-color: #dcdcdc; font-family: Verdana; font-size: 1em; border-top: #cccccc 1px solid; border-bottom: #666666 1px solid; border-left: #cccccc 1px solid; border-right: #666666 1px solid; }
                    .frmheader { color: #000000; background: #dcdcdc; font-family: Verdana; font-size: .7em; font-weight: normal; border-bottom: 1px solid #dcdcdc; padding-top: 2px; padding-bottom: 2px; }
                    .frmtext { font-family: Verdana; font-size: .7em; margin-top: 8px; margin-bottom: 0px; margin-left: 32px; }
                    .frmInput { font-family: Verdana; font-size: 1em; }
                    .intro { margin-left: -15px; }
                </style>
                <title>eXist Web Service - <xsl:value-of select="$webserviceName"/>
                </title>
            </head>
            <body>
                <div id="content">
                    <p class="heading1">
                        <xsl:value-of select="$webserviceName"/>
                    </p>
                    <br/>
                    <span>
                        <p class="intro">Click <a href="{$webserviceURL}">here</a> for a complete list of operations.</p>
                        <h2>
                            <xsl:value-of select="functions/function[name = $function]/name"/>
                        </h2>
                        <p class="intro">
                            <xsl:value-of select="function/function[name = $function]/description"/>
                        </p>
                        <p>The following is a sample SOAP request and response.  The <font class="value">placeholders</font> shown need to be replaced with actual values.</p>
                        <pre>POST <xsl:value-of select="path"/> HTTP/1.1
Host: <xsl:value-of select="host"/>
Content-Type: text/xml; charset=utf-8
Content-Length: <font class="value">length</font>
SOAPAction: ""

&lt;?xml version="1.0" encoding="utf-8"?&gt;
&lt;soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"&gt;
    &lt;soap:Body&gt;
        &lt;<xsl:value-of select="functions/function[name = $function]/name"/> xmlns="<xsl:value-of select="$webserviceURL"/>"&gt;
            <xsl:for-each select="functions/function[name = $function]/parameters/parameter">
                                <xsl:choose>
                                    <xsl:when test="name =''">&lt;arg<xsl:value-of select="position()"/>&gt;</xsl:when>
                                    <xsl:otherwise>&lt;<xsl:value-of select="name"/>&gt;</xsl:otherwise>
                                </xsl:choose>
                                <xsl:value-of select="type"/>
                                <xsl:choose>
                                    <xsl:when test="name = ''">&lt;/arg<xsl:value-of select="position()"/>&gt;</xsl:when>
                                    <xsl:otherwise>&lt;/<xsl:value-of select="name"/>&gt;</xsl:otherwise>
                                </xsl:choose>
                            </xsl:for-each>
        &lt;/<xsl:value-of select="functions/function[name = $function]/name"/>&gt;
    &lt;/soap:Body&gt;
&lt;/soap:Envelope&gt;</pre>
                        <pre>HTTP/1.1 200 OK
Content-Type: text/xml; charset=utf-8
Content-Length: <font class="value">length</font>

&lt;?xml version="1.0" encoding="utf-8"?&gt;
&lt;soap:Envelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"&gt;
    &lt;soap:Body&gt;
        &lt;<xsl:value-of select="functions/function[name = $function]/name"/>Response xmlns="<xsl:value-of select="$webserviceURL"/>"&gt;
            &lt;<xsl:value-of select="functions/function[name = $function]/name"/>Result&gt;<xsl:value-of select="functions/function[name = $function]/return/type"/>&lt;/<xsl:value-of select="functions/function[name = $function]/name"/>Result&gt;
        &lt;/<xsl:value-of select="functions/function[name = $function]/name"/>Response&gt;
    &lt;/soap:Body&gt;
&lt;/soap:Envelope&gt;</pre>
                    </span>
                </div>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>