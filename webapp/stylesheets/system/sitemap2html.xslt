<?xml version="1.0"?>

<xsl:stylesheet version="1.0"
  xmlns:map="http://apache.org/cocoon/sitemap/1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

 <xsl:template match="/">
  <html>
   <head>
    <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1"/>
    <meta name="Author" content="{author}"/>
    <meta name="Version" content="{version}"/>
    <title>The Sitemap</title>
   </head>
   <body>
    <xsl:apply-templates/>
   </body>
  </html>
 </xsl:template>

 <xsl:template match="map:sitemap">
  <h1>The Sitemap</h1>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:components">
  <h2>Components</h2>
  <table border="0" width="100%" bgcolor="navy" cellspacing="0" cellpadding="0">
   <tr>
    <td>
     <table border="0" width="100%" cellspacing="2" cellpadding="0">
      <tr>
       <td bgcolor="lightgrey">
        <table border="0" width="100%" cellspacing="2" cellpadding="0">
         <xsl:apply-templates select="map:generators"/>
         <xsl:apply-templates select="map:transformers"/>
         <xsl:apply-templates select="map:readers"/>
         <xsl:apply-templates select="map:serializers"/>
         <xsl:apply-templates select="map:selectors"/>
         <xsl:apply-templates select="map:matchers"/>
        </table>
       </td>
      </tr>
     </table>
    </td>
   </tr>
  </table>
 </xsl:template>

 <xsl:template match="map:views">
  <h2>Views</h2>
  <table border="0" width="100%" bgcolor="navy" cellspacing="0" cellpadding="0">
   <tr>
    <td>
     <table border="0" width="100%" cellspacing="2" cellpadding="0">
      <tr>
       <td bgcolor="lightgrey">
        <table border="0" width="100%" cellspacing="2" cellpadding="0">
         <tr>
          <td colspan="2" bgcolor="white"><b>Name</b></td>
          <td bgcolor="white"><b>Arguments</b></td>
         </tr>
         <xsl:apply-templates select="map:match|map:select|map:redirect-to|map:generate|map:transform|map:select|map:read"/>
        </table>
       </td>
      </tr>
     </table>
    </td>
   </tr>
  </table>
 </xsl:template>

 <xsl:template match="map:resources">
  <h2>Resources</h2>
  <table border="0" width="100%" bgcolor="navy" cellspacing="0" cellpadding="0">
   <tr>
    <td>
     <table border="0" width="100%" cellspacing="2" cellpadding="0">
      <tr>
       <td bgcolor="lightgrey">
        <table border="0" width="100%" cellspacing="2" cellpadding="0">
         <tr>
          <td colspan="3" bgcolor="white"><b>Name</b></td>
         </tr>
         <xsl:apply-templates/>
        </table>
       </td>
      </tr>
     </table>
    </td>
   </tr>
  </table>
 </xsl:template>

 <xsl:template match="map:pipelines">
  <h2>Pipelines</h2>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:generators">
  <xsl:call-template name="show-components">
   <xsl:with-param name="type">Generators</xsl:with-param>
   <xsl:with-param name="comp-type">generator</xsl:with-param>
   <xsl:with-param name="default" select="@default"/>
   <xsl:with-param name="components" select="./*"/>
   <xsl:with-param name="label">true</xsl:with-param>
  </xsl:call-template>
 </xsl:template>

 <xsl:template match="map:transformers">
  <xsl:call-template name="break"/>
  <xsl:call-template name="show-components">
   <xsl:with-param name="type">Transformers</xsl:with-param>
   <xsl:with-param name="comp-type">transformer</xsl:with-param>
   <xsl:with-param name="default" select="@default"/>
   <xsl:with-param name="components" select="./*"/>
   <xsl:with-param name="label">true</xsl:with-param>
   <xsl:with-param name="break">true</xsl:with-param>
  </xsl:call-template>
 </xsl:template>

 <xsl:template match="map:readers">
  <xsl:call-template name="break"/>
  <xsl:call-template name="show-components">
   <xsl:with-param name="type">Readers</xsl:with-param>
   <xsl:with-param name="comp-type">reader</xsl:with-param>
   <xsl:with-param name="default" select="@default"/>
   <xsl:with-param name="components" select="./*"/>
   <xsl:with-param name="break">true</xsl:with-param>
  </xsl:call-template>
 </xsl:template>

 <xsl:template match="map:serializers">
  <xsl:call-template name="break"/>
  <xsl:call-template name="show-components">
   <xsl:with-param name="type">Serializers</xsl:with-param>
   <xsl:with-param name="comp-type">serializer</xsl:with-param>
   <xsl:with-param name="default" select="@default"/>
   <xsl:with-param name="components" select="./*"/>
   <xsl:with-param name="break">true</xsl:with-param>
  </xsl:call-template>
 </xsl:template>

 <xsl:template match="map:selectors">
  <xsl:call-template name="break"/>
  <xsl:call-template name="show-components">
   <xsl:with-param name="type">Selectors</xsl:with-param>
   <xsl:with-param name="comp-type">selector</xsl:with-param>
   <xsl:with-param name="default" select="@default"/>
   <xsl:with-param name="components" select="./*"/>
   <xsl:with-param name="break">true</xsl:with-param>
  </xsl:call-template>
 </xsl:template>

 <xsl:template match="map:matchers">
  <xsl:call-template name="break"/>
  <xsl:call-template name="show-components">
   <xsl:with-param name="type">Matchers</xsl:with-param>
   <xsl:with-param name="comp-type">matcher</xsl:with-param>
   <xsl:with-param name="default" select="@default"/>
   <xsl:with-param name="components" select="./*"/>
   <xsl:with-param name="break">true</xsl:with-param>
  </xsl:call-template>
 </xsl:template>

 <xsl:template match="map:view">
  <xsl:if test="preceding-sibling::map:view">
   <xsl:call-template name="break"/>
  </xsl:if>
  <tr>
   <td colspan="2" bgcolor="white"><a href=""><xsl:value-of select="@name"/></a></td>
   <td bgcolor="white">generate-from="<xsl:value-of select="@generate-from"/>"</td>
  </tr>
  <tr>
   <td bgcolor="white">&#160;</td>
   <td colspan="2" bgcolor="white">
    <xsl:apply-templates/>
   </td>
  </tr>
 </xsl:template>

 <xsl:template match="map:resource">
  <xsl:if test="preceding-sibling::map:resource">
   <xsl:call-template name="break"/>
  </xsl:if>
  <tr>
   <td colspan="3" bgcolor="white"><a href=""><xsl:value-of select="@name"/></a></td>
  </tr>
  <tr>
   <td bgcolor="white">&#160;</td>
   <td colspan="2" bgcolor="white">
    <xsl:apply-templates/>
   </td>
  </tr>
 </xsl:template>

 <xsl:template match="map:pipeline">
  <table border="0" width="100%" bgcolor="navy" cellspacing="0" cellpadding="0">
   <tr>
    <td>
     <table border="0" width="100%" cellspacing="2" cellpadding="0">
      <tr>
       <td bgcolor="lightgrey">
        <table border="0" width="100%" cellspacing="2" cellpadding="0">
         <xsl:apply-templates/>
        </table>
       </td>
      </tr>
     </table>
    </td>
   </tr>
  </table>
  <br/>
 </xsl:template>

 <xsl:template match="map:match">
  <xsl:choose>
   <xsl:when test="ancestor::*[self::map:pipeline]">
    <tr>
     <td bgcolor="white">
      <xsl:call-template name="indent"/>
      <a href="">match</a>
      <xsl:if test="@type">
        type="<xsl:value-of select="@type"/>"
      </xsl:if>
      pattern="<xsl:value-of select="@pattern"/>"
      <br/>
      <xsl:apply-templates/>
     </td>
    </tr>
   </xsl:when>
   <xsl:otherwise>
    <xsl:call-template name="indent"/>
    <a href="">match</a>
    <xsl:if test="@type">
      type="<xsl:value-of select="@type"/>"
    </xsl:if>
    pattern="<xsl:value-of select="@pattern"/>"
    <br/>
    <xsl:apply-templates/>
   </xsl:otherwise>
  </xsl:choose>
 </xsl:template>

 <xsl:template match="map:select">
  <xsl:call-template name="indent"/>
  <a href="">select</a>
  <xsl:if test="@type">
    type="<xsl:value-of select="@type"/>"
  </xsl:if>
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:when">
  <xsl:call-template name="indent"/>
  <a href="">when</a> test="<xsl:value-of select="@test"/>"<br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:otherwise">
  <xsl:call-template name="indent"/>
  <a href="">otherwise</a><br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:generate">
  <xsl:call-template name="indent"/>
  <a href="">generate</a>
  <xsl:if test="@type">
    type="<xsl:value-of select="@type"/>"
  </xsl:if>
  src="<xsl:value-of select="@src"/>"
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:transform">
  <xsl:call-template name="indent"/>
  <a href="">transform</a>
  <xsl:if test="@type">
    type="<xsl:value-of select="@type"/>"
  </xsl:if>
  src="<xsl:value-of select="@src"/>"
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:read">
  <xsl:call-template name="indent"/>
  <a href="">read</a>
  <xsl:if test="@type">
    type="<xsl:value-of select="@type"/>"
  </xsl:if>
  src="<xsl:value-of select="@src"/>"
  <xsl:if test="@mime-type">
    mime-type="<xsl:value-of select="@mime-type"/>"
  </xsl:if>
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:redirect-to">
  <xsl:call-template name="indent"/>
  <a href="">redirect-to</a>
  <xsl:choose>
    <xsl:when test="@uri">
      uri="<xsl:value-of select="@uri"/>"
    </xsl:when>
    <xsl:when test="@resource">
      resource="<xsl:value-of select="@resource"/>"
    </xsl:when>
  </xsl:choose>
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:serialize">
  <xsl:call-template name="indent"/>
  <a href="">serialize</a>
  <xsl:if test="@type">
    type="<xsl:value-of select="@type"/>"
  </xsl:if>
  <xsl:if test="@mime-type">
    mime-type="<xsl:value-of select="@mime-type"/>"
  </xsl:if>
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:mount">
  <xsl:call-template name="indent"/>
  <a href="">mount</a> src="<xsl:value-of select="@src"/>" uri-prefix="<xsl:value-of select="@uri-prefix"/>"<br/>
  <xsl:if test="@check-reload">
    check-reload="<xsl:value-of select="@check-reload"/>"
  </xsl:if>
  <br/>
  <xsl:apply-templates/>
 </xsl:template>

 <xsl:template match="map:handle-errors">
  <tr>
   <td bgcolor="white">
    <xsl:call-template name="indent"/>
    <a href="">handle-errors</a>
    <br/>
    <xsl:apply-templates/>
   </td>
  </tr>
 </xsl:template>

 <!-- named templates -->

 <xsl:template name="show-components">
  <xsl:param name="type"/>
  <xsl:param name="comp-type"/>
  <xsl:param name="default"/>
  <xsl:param name="components"/>
  <xsl:param name="label"/>
  <tr>
   <td colspan="3" bgcolor="white">
    <span class="h3"><xsl:value-of select="$type"/> (default=<i><xsl:value-of select="$default"/></i>)</span>
   </td>
  </tr>
  <tr>
   <td bgcolor="white"><b>Name</b></td>
   <xsl:choose>
    <xsl:when test="$label">
     <td bgcolor="white"><b>Class</b></td>
     <td bgcolor="white"><b>Label</b></td>
    </xsl:when>
    <xsl:otherwise>
     <td colspan="2" bgcolor="white"><b>Class</b></td>
    </xsl:otherwise>
   </xsl:choose>
  </tr>
  <xsl:for-each select="$components">
   <tr>
    <td bgcolor="white"><a href="sitemap-component?component={$comp-type}&amp;name={@name}"><xsl:value-of select="@name"/></a></td>
    <xsl:choose>
     <xsl:when test="$label">
      <xsl:choose>
       <xsl:when test="@src">
        <td bgcolor="white"><xsl:value-of select="@src"/></td>
       </xsl:when>
       <xsl:otherwise>
        <td bgcolor="white"><i><xsl:value-of select="@factory"/></i></td>
       </xsl:otherwise>
      </xsl:choose>
      <td bgcolor="white"><xsl:value-of select="@label"/>&#160;</td>
     </xsl:when>
     <xsl:otherwise>
      <xsl:choose>
       <xsl:when test="@src">
        <td colspan="2" bgcolor="white"><xsl:value-of select="@src"/></td>
       </xsl:when>
       <xsl:otherwise>
        <td colspan="2" bgcolor="white"><i><xsl:value-of select="@factory"/></i></td>
       </xsl:otherwise>
      </xsl:choose>
     </xsl:otherwise>
    </xsl:choose>
   </tr>
   <xsl:if test="./*">
<!--
    <xsl:call-template name="show-config"/>
-->
   </xsl:if>
  </xsl:for-each>
 </xsl:template>

 <xsl:template name="show-config">
  <xsl:param name="indent"/>
  <xsl:choose>
   <xsl:when test="string-length($indent)=0">
    <tr>
     <td align="right">Configuration</td>
     <td colspan="3">
      <xsl:for-each select="./*">
       &#160;<xsl:value-of select="name()"/>
       <xsl:if test="./*">
        <xsl:call-template name="show-config">
         <xsl:with-param name="indent">&#160;<xsl:value-of select="$indent"/></xsl:with-param>
        </xsl:call-template>
       </xsl:if>
      </xsl:for-each>
     </td>
    </tr>
   </xsl:when>
   <xsl:otherwise>
    <tr>
     <td align="right">&#160;</td>
     <td colspan="3">
      <xsl:for-each select="./*">
       &#160;<xsl:value-of select="name()"/>=<xsl:value-of select="text()"/>
       <xsl:if test="./*">
        <xsl:call-template name="show-config">
         <xsl:with-param name="indent">&#160;<xsl:value-of select="$indent"/></xsl:with-param>
        </xsl:call-template>
       </xsl:if>
      </xsl:for-each>
     </td>
    </tr>
   </xsl:otherwise>
  </xsl:choose>
 </xsl:template>

 <xsl:template name="break">
  <tr>
   <td colspan="3" bgcolor="white"><hr color="navy"/></td>
  </tr>
 </xsl:template>

 <xsl:template name="indent">
  <xsl:for-each select="ancestor::*">
   <xsl:choose>
    <xsl:when test="local-name(.)='pipeline'">
    </xsl:when>
    <xsl:when test="local-name(.)='pipelines'">
    </xsl:when>
    <xsl:when test="local-name(.)='sitemap'">
    </xsl:when>
    <xsl:when test="local-name(.)='resource'">
    </xsl:when>
    <xsl:when test="local-name(.)='resources'">
    </xsl:when>
    <xsl:when test="local-name(.)='view'">
    </xsl:when>
    <xsl:when test="local-name(.)='views'">
    </xsl:when>
    <xsl:otherwise>
     &#160;&#160;
    </xsl:otherwise>
   </xsl:choose>
  </xsl:for-each>
 </xsl:template>

 <xsl:template match="*|@*|text()|comment()|processing-instruction()" priority="-1"/>

</xsl:stylesheet>
