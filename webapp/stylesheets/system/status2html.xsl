<?xml version="1.0"?>
<!-- Author: Nicola Ken Barozzi "barozzi@nicolaken.com" -->
<xsl:stylesheet version="1.0"
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 xmlns:status="http://apache.org/cocoon/status/2.0">

  <xsl:template match="status:statusinfo">
    <html>
      <head>
        <title>Cocoon2 status [<xsl:value-of select="@status:host"/>]</title>
      </head>
      <body bgcolor="white">

      <table bgcolor="#000000" cellspacing="0" cellpadding="2" width="97%">
        <tr>
          <td>
    
           <table border="0" bgcolor="#000000" cellpadding="0" cellspacing="0" width="100%">
             <tr>
              <td>   
              
                <table bgcolor="#ffffff" noshade="noshade" cellspacing="0" cellpadding="6" width="100%">
                  <tr>
                    <td bgcolor="#0086b2" valign="top" align="left">
                      <img src="images/cocoon.gif" border="0"/>
                    </td>
                    <td bgcolor="#0086b2" valign="top" align="right">
                     <FONT face="arial,helvetica,sanserif" color="#ffffff">
                       [<xsl:value-of select="@status:host"/>] <xsl:value-of select="@status:date"/>
                     </FONT>
                    </td>
                  </tr>
                 </table>

                </td>
               </tr>
             </table>      
        
            </td>
          </tr>
         </table>
 
      <xsl:call-template name="spacer">
       <xsl:with-param name="height" select="number(10)"/>
      </xsl:call-template>
      
      <xsl:apply-templates />
        
      </body>
    </html>
  </xsl:template>

  <xsl:template match="status:group">
       <table border="0" bgcolor="#000000" cellpadding="0" cellspacing="0" width="100%">
         <tr>
          <td>    
          
           <table bgcolor="#000000" border="0" cellspacing="2" cellpadding="6" width="100%">
            <tr>
              <td bgcolor="#0086b2" valign="top" align="left" colspan="2">
               <FONT color="#ffffff" face="arial,helvetica,sanserif" size="+1">
                -<xsl:value-of select="@status:name"/>
               </FONT>
              </td>
            </tr>
            <tr>
   
          <td bgcolor="#ffffff">
      
            <table border="0" bgcolor="#000000" cellpadding="0" cellspacing="0" width="100%">
             <tr>
              <td>    
 
                <table width="100%" bgcolor="#000000" border="0" cellspacing="2" cellpadding="6">
                  <xsl:apply-templates select="status:value"/>
                  <xsl:call-template name="spacer">
                   <xsl:with-param name="height" select="number(8)"/>
                  </xsl:call-template>
                  <xsl:apply-templates select="status:group"/>
                </table>
        
             </td>
            </tr>
          </table>    
        
        </td>
      
      
       </tr>
     </table>

    </td>
   </tr>
 </table>

   
  <xsl:call-template name="spacer">
   <xsl:with-param name="height" select="number(6)"/>
  </xsl:call-template>

  </xsl:template>

	<xsl:template match="status:value">
   <tr>
    <td bgcolor="#0086b2" valign="top" align="left" width="1%">
     <font face="arial,helvetica,sanserif" color="#ffffff">
        <xsl:value-of select="@status:name"/>
     </font>
    </td>
    
    <td bgcolor="#eeeeee" width="100%">
      
    <xsl:choose>
      <xsl:when test="../@status:name='memory' and ( @status:name='total' or @status:name='free')">
          <font face="arial,helvetica,sanserif">
          <xsl:call-template name="suffix">
            <xsl:with-param 
              name="bytes" 
              select="number(.)"/>
          </xsl:call-template>
          </font>
      </xsl:when>    
      <xsl:when test="count(status:line) &lt;= 1">
          <font face="arial,helvetica,sanserif">
            <xsl:value-of select="status:line" />
          </font>
      </xsl:when>
      <xsl:otherwise>
          <ul>
            <xsl:apply-templates />
          </ul>
      </xsl:otherwise>
    </xsl:choose>
    </td>
   </tr>
  </xsl:template>

	<xsl:template match="status:line">
		<li>
			<font face="arial,helvetica,sanserif">
				<xsl:value-of select="." />
			</font>
		</li>
	</xsl:template>

	<xsl:template name="suffix">
		<xsl:param name="bytes"/>
		<xsl:choose>
			<!-- More than 4 MB (=4194304) -->
			<xsl:when test="$bytes &gt;= 4194304">
				<xsl:value-of select="round($bytes div 10485.76) div 100"/> MB
				<small>(<xsl:value-of select="$bytes"/>)</small>
			</xsl:when>
			<!-- More than 4 KB (=4096) -->
			<xsl:when test="$bytes &gt; 4096">
				<xsl:value-of select="round($bytes div 10.24) div 100"/> KB
				<small>(<xsl:value-of select="$bytes"/>)</small>
			</xsl:when>
			<!-- Less -->
			<xsl:otherwise>
				<xsl:value-of select="$bytes"/> B
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
  
  <xsl:template name="spacer">
  	<xsl:param name="height"/>
    <table bgcolor="#ffffff" cellspacing="0" cellpadding="2" width="100%">
      <tr>
        <td bgcolor="#ffffff"> 
              
          <table bgcolor="#ffffff" cellspacing="0" cellpadding="2" width="100%">
           <tr>
            <td width="100%" bgcolor="#ffffff" valign="top" align="left" height="$height"> 
            </td>
          </tr>
         </table>
    
        </td>
      </tr>
    </table>
	</xsl:template>


</xsl:stylesheet>

