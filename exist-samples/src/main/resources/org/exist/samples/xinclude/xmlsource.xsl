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

<xsl:stylesheet 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">
  
<!-- the following templates pretty-print xml source code.
     All xml content contained between the xml-source tags
     is pretty printed.
-->
<xsl:template match="xml-source">
  <div style="font-family=Courier,monospace">
    <xsl:apply-templates mode="xmlsrc"/>
  </div>
</xsl:template>

<xsl:template match="text()" mode="xmlsrc">
  <xsl:value-of select="."/>
</xsl:template>

<xsl:template match="processing-instruction()" mode="xmlsrc">
  <dd>
    <font color="darkred">&lt;?<xsl:value-of select="."/>?&gt;</font>
  </dd>
</xsl:template>

<xsl:template match="comment()" mode="xmlsrc">
  <dd>
    <font color="grey">&lt;-- <xsl:value-of select="."/> --&gt;</font>
  </dd>
</xsl:template>

<xsl:template match="@*" mode="xmlsrc">
  <xsl:text> </xsl:text>
  <xsl:choose>
    <xsl:when test="not(namespace-uri(.)='')">
	  <font color="purple">
        <xsl:value-of select="name(.)"/>
      </font>
    </xsl:when>
    <xsl:otherwise>
		<font color="red">
        	<xsl:value-of select="name(.)"/>
      	</font>
    </xsl:otherwise>
  </xsl:choose>
  ="<font color="lime">
  	<xsl:call-template name="highlight">
  		<xsl:with-param name="string"><xsl:value-of select="."/></xsl:with-param>
  	</xsl:call-template>
  </font>"
</xsl:template>

<xsl:template match="*" mode="xmlsrc">
  <div style="margin-left: 20px">
    <font color="navy">
      <xsl:text>&lt;</xsl:text>
    </font>
    <xsl:choose>
      <xsl:when test="not(namespace-uri()='')">
        <font color="green">
          <xsl:value-of select="name()"/>
        </font>
      </xsl:when>
      <xsl:otherwise>
        <font color="navy">
          <xsl:value-of select="name()"/>
        </font>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:apply-templates select="@*" mode="xmlsrc"/>
    <xsl:choose>
      <xsl:when test="text()">
      	<font color="navy">
          &gt;
        </font>
        <xsl:apply-templates mode="xmlsrc"/>
        <font color="navy">
          &lt;/
          <xsl:value-of select="name()"/>
          &gt;
        </font>
      </xsl:when>
      <xsl:when test="*">
        <font color="navy">
          &gt;
        </font>
        <div>
          <xsl:apply-templates select="node()" mode="xmlsrc"/>
        </div>
        <font color="navy">
          &lt;/
        </font>
        <xsl:choose>
          <xsl:when test="not(namespace-uri()='')">
            <font color="green">
              <xsl:value-of select="name()"/>
            </font>
          </xsl:when>
          <xsl:otherwise>
            <font color="navy">
              <xsl:value-of select="name()"/>
            </font>
          </xsl:otherwise>
        </xsl:choose>
        <font color="navy">
          &gt;
        </font>
      </xsl:when>
      <xsl:otherwise>
        <font color="navy">
          /&gt;
        </font>
      </xsl:otherwise>
    </xsl:choose>
  </div>
</xsl:template>

<xsl:template name="highlight">
	<xsl:param name="string"/>
	<xsl:choose>
		<xsl:when test="contains($string, '||')">
			<xsl:variable name="before" select="substring-before($string, '||')"/>
			<xsl:variable name="after" select="substring-after($string, '||')"/>
			<xsl:value-of select="$before"/>
			<span style="background-color: #FFFF00">
				<xsl:value-of select="substring-before($after, '||')"/>
			</span>
			<xsl:call-template name="highlight">
				<xsl:with-param name="string">
					<xsl:value-of select="substring-after($after, '||')"/>
				</xsl:with-param>
			</xsl:call-template>
		</xsl:when>
		<xsl:otherwise>
			<xsl:value-of select="$string"/>
		</xsl:otherwise>
	</xsl:choose>
</xsl:template>

</xsl:stylesheet>
