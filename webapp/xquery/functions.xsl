<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

	<xsl:param name="now"/>

	<xsl:template match="document">
		<html>
			<head>
				<title><xsl:value-of select="title"/></title>
				<style type="text/css">
					body {
						margin: 20px;
					}
					h1 {
						background-color: #0000FF;
						color: white;
						padding-left: 15px;
						padding-right: 15px;
						padding-top: 5px;
						padding-bottom: 5px;
						border: 1px solid black;
					}
					div.description {
					}
				</style>
			</head>
			<body>
				<h1><xsl:value-of select="title"/></h1>
				<xsl:apply-templates/>
				<p><small>Served at <xsl:value-of select="$now"/>.</small></p>
			</body>
		</html>
	</xsl:template>

	<xsl:template match="para">
		<p><xsl:apply-templates/></p>
	</xsl:template>

    <xsl:template match="title"/>

	<xsl:template match="builtin-functions">
        <table border="0" cellpadding="5" cellspacing="5">
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    XPath/XQuery Core Library Functions (http://www.w3.org/2003/05/xpath-functions)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://www.w3.org/2003/05/xpath-functions']"/>
            
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    Utility Extension Functions (http://exist-db.org/xquery/util)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/util']"/>
            
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    XMLDB Extension Functions (http://exist-db.org/xquery/xmldb)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/xmldb']"/>
            
            <tr bgcolor="#0000FF">
                <th style="color: #FFFFFF" align="left" colspan="2">
                    HTTP Request Extension Functions (http://exist-db.org/xquery/request)
                </th>
            </tr>
            <xsl:apply-templates select="function[@module='http://exist-db.org/xquery/request']"/>
			<tr bgcolor="#0000FF">
				<th style="color: #FFFFFF" align="left" colspan="2">
					Transformation Functions (http://exist-db.org/xquery/transform)
				</th>
			</tr>
			<xsl:apply-templates select="function[@module='http://exist-db.org/xquery/transform']"/>
        </table>
		
    </xsl:template>
    
    <xsl:template match="function">
        <tr>
            <td valign="top" colspan="2">
                <a name="{@name}">
                    <b>
                        <xsl:value-of select="signature"/>
                    </b>
                </a>
            </td>
        </tr>
        <xsl:if test="description">
            <tr>
                <td width="10%"></td>
                <td width="90%">
					<div class="description">
						<xsl:value-of select="description"/>
					</div>
				</td>
            </tr>
        </xsl:if>
    </xsl:template>
    
</xsl:stylesheet>
