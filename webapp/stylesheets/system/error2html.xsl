<?xml version="1.0"?>
<!-- Author: Nicola Ken Barozzi "barozzi@nicolaken.com" -->

<xsl:stylesheet version="1.0"
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 xmlns:error="http://apache.org/cocoon/error/2.0">

<xsl:template match="error:notify">
 <html>
  <head>
   <title>
    <xsl:value-of select="@error:type"/>:<xsl:value-of select="error:title"/>
   </title>
   <style><![CDATA[
   <!--
      H1{font-family : sans-serif,Arial,Tahoma;color : white;background-color : #0086b2;} 
      BODY{font-family : sans-serif,Arial,Tahoma;color : black;background-color : white;} 
      TABLE{font-family : sans-serif,Arial,Tahoma;color : black;background-color : black;} 
      B{color : white;background-color : #0086b2;}
      HR{color : #0086b2;} 
   //-->]]>
   </style>
   <script language="JavaScript1.2"><![CDATA[
    <!--
      var head="display:''"
      function expand(whatToExpand)
      {
        var head=whatToExpand.style
        if (head.display=="none"){
          head.display=""
        }
        else{
          head.display="none"
        }
      }
     //-->]]>
   </script>
  </head>
  <body>
     <table align="center" border="0" bgcolor="#000000" cellpadding="0" cellspacing="0">
    <tbody>
     <tr>
      <td>
      
      
   <table align="center" border="0" bgcolor="#000000" cellpadding="2" cellspacing="2">
    <tbody>
     <tr>
      <td bgcolor="#0086b2" colspan="2">
       <font color="#ffffff" size="+2">
        <xsl:value-of select="error:title"/>
       </font>
      </td>
     </tr>

     <tr>
      <td bgcolor="#ffffff" colspan="2" valign="top">
       <font color="#000000">
        The <xsl:value-of select="@error:sender"/> notifies that 
        <xsl:value-of select="error:source"/> says:<br/><br/>
        <i><xsl:call-template name="returns2br">
           <xsl:with-param name="string" select="error:message"/>
         </xsl:call-template></i><br/><br/>
        More precisely:<br/><br/>
        <i><xsl:call-template name="returns2br">
           <xsl:with-param name="string" select="error:description"/>
         </xsl:call-template></i><br/>
        <br/>
       </font>
      </td>
     </tr>
     
<!--     
     
     <tr>
      <td bgcolor="#0086b2" valign="top">
       <font color="#ffffff" size="+1">
        <xsl:value-of select="@error:type"/>
       </font>
      </td>
      <td bgcolor="#ffffff" >
       <xsl:apply-templates select="error:message"/>
      </td>
     </tr>

     <tr>
      <td bgcolor="#0086b2" valign="top" colspan="2">
       <font color="#ffffff" size="+1">details</font>
      </td>
     </tr>

     <tr>
      <td bgcolor="#0086b2" valign="top">
       <font color="#ffffff">from</font>
      </td>
      <td bgcolor="#ffffff">
       <font face="arial,helvetica,sanserif">
        <xsl:value-of select="@error:sender"/>
       </font>
      </td>
     </tr>

     <tr>
      <td bgcolor="#0086b2" valign="top">
       <font color="#ffffff">source</font>
      </td>
      <td bgcolor="#ffffff">
       <font face="arial,helvetica,sanserif">
        <xsl:value-of select="error:source"/>
       </font>
      </td>
     </tr>

     <xsl:apply-templates select="error:description"/>
-->
     <tr>
      <td bgcolor="#0086b2" valign="top" colspan="2">
       <font color="#ffffff" size="+1">extra info</font>
      </td>
     </tr>

     <xsl:apply-templates select="error:extra"/>
     
     <tr>
      <td bgcolor="#ffffff" colspan="2" valign="top">
       <font color="#000000" size="-1">
       <br/>
        If you need help and this information is not enough, you
        are invited to read the <a href="http://xml.apache.org/cocoon/faq.html">cocoon faq</a>.<br/>
        If you still don't find the answers you need,
        can send a mail to the 
        <a>
        <xsl:attribute name="href">mailto:cocoon-users@xml.apache.org?subject=[HELP]<xsl:value-of select="error:message"/>&amp;body=Description:<xsl:value-of select="error:description"/></xsl:attribute>
        Cocoon users mailing list</a>,
        remembering to
        <ul>
          <li> specify the version of Cocoon you're using, or we suppose that you
          are talking about the latest version;</li>
          <li>specify the taglibs and sitemap components that are pertinent;</li>
          <li>specify the platform-operating system-version-servlet container version;</li>
          <li>send any pertinent error message;</li>
          <li>send pertinent log snippets;</li>
          <li>send pertinent sitemap snippets;</li>
          <li>send pertinent parts of the page that gives you problems.</li>
        </ul>
        For more detailed technical information, take a look at the log
        files in the log directory of cocoon, which is <code>/WEB-INF/logs</code> by default.<br/>
        Logging configuration is by default in <code>/WEB-INF/logkit.xconf.</code><br/><br/>
        If you think you found a bug, please report it to 
        <a href="http://nagoya.apache.org/bugzilla/">Apache's Bugzilla</a>;
        a message will be sent to the developer mailing list.<br/>
       </font>
      </td>
     </tr>
       
    </tbody>
   </table>
   
   
   
         </td>
     </tr>
       
    </tbody>
   </table>
   
   
  </body>
 </html>
</xsl:template>

 <xsl:template match="error:description">
  <tr>
   <td bgcolor="#0086b2" valign="top">
    <font color="#ffffff" face="arial,helvetica,sanserif">description</font>
   </td>
   <td bgcolor="#ffffff">
         <xsl:call-template name="returns2br">
           <xsl:with-param name="string" select="."/>
         </xsl:call-template>
   </td>
  </tr>
 </xsl:template>

 <xsl:template match="error:message">
         <xsl:call-template name="returns2br">
           <xsl:with-param name="string" select="."/>
         </xsl:call-template>
 </xsl:template>

 <xsl:template match="error:extra">
  <tr>
   <td bgcolor="#0086b2" valign="top">
    <font color="#ffffff">
     <xsl:value-of select="@error:description"/>
    </font>
   </td>   
   <td bgcolor="#ffffff">
    <font size="-1">
		<xsl:choose>
			<xsl:when test="contains(@error:description,'stacktrace')">
       <!-- degrade gracefully on Netscape-->
       <a href="javascript:" onclick="expand(document.all[this.sourceIndex+2])"><script>if(document.all){document.write('show');}</script></a>
       <div style="display:'none';">
         <xsl:call-template name="returns2br">
           <xsl:with-param name="string" select="."/>
         </xsl:call-template>
       </div>
			</xsl:when>
			<xsl:otherwise>
         <xsl:call-template name="returns2br">
           <xsl:with-param name="string" select="."/>
         </xsl:call-template>
			</xsl:otherwise>
		</xsl:choose>
    </font>
   </td>
  </tr>
 </xsl:template>
 
  <xsl:template name="returns2br">
    <xsl:param name="string"/>
    <xsl:variable name="return" select="'&#xa;'"/>
    <xsl:choose>
      <xsl:when test="contains($string,$return)">
        <xsl:value-of select="substring-before($string,$return)"/>
        <br/>
        <xsl:call-template name="returns2br">
          <xsl:with-param name="string" select="substring-after($string,$return)"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:value-of select="$string"/>
      </xsl:otherwise>
   </xsl:choose>
  </xsl:template>
  
</xsl:stylesheet>
