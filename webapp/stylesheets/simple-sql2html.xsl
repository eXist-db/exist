<?xml version="1.0"?>

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:sql="http://apache.org/cocoon/SQL/2.0">


  <xsl:import href="page/simple-page2html.xsl"/>

  <xsl:template match="sql:rowset">
   <xsl:choose>
    <xsl:when test="ancestor::sql:rowset">
     <tr>
      <td>
       <table border="1">
        <xsl:apply-templates/>
       </table>
      </td>
     </tr>
    </xsl:when>
    <xsl:otherwise>
     <table border="1">
      <xsl:apply-templates/>
     </table>
    </xsl:otherwise>
   </xsl:choose>
  </xsl:template>

  <xsl:template match="sql:row">
   <tr>
    <xsl:apply-templates/>
   </tr>
  </xsl:template>

  <xsl:template match="sql:name">
   <td><xsl:value-of select="."/></td>  
  </xsl:template>

  <xsl:template match="sql:id">
   <!-- ignore -->
  </xsl:template>

</xsl:stylesheet>
