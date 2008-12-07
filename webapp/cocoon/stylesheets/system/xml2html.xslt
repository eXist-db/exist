<?xml version="1.0"?>

<!--+
    | XSLT REC Compliant Version of IE5 Default Stylesheet
    |
    | Original version by Jonathan Marsh (jmarsh@microsoft.com)
    | Conversion to XSLT 1.0 REC Syntax by Steve Muench (smuench@oracle.com)
    | Added script support by Andrew Timberlake (andrew@timberlake.co.za)
    | Cleaned up and ported to standard DOM by Stefano Mazzocchi (stefano@apache.org)
    |
    | CVS $Id: xml2html.xslt 89 2003-07-01 07:25:44Z wolfgang_m $
    +-->
    
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

   <xsl:template match="/">
      <HTML>
         <HEAD>
            <STYLE>
              BODY  {background-color: white; color: black; font: monospace;}
                .b  {cursor:pointer; color:red; font-weight:bold; text-decoration:none}
                .e  {border: 0px; padding: 0px; margin: 0px 2em 0px 2em; text-indent:-1em;}
                .en {color:#000088; font-weight:bold;}
                .an {color:#880000}
                .av {color:#888888}
                .c  {color:#008800}
                .t  {color:black}
                .m  {color:navy}
                .pi {color:red}
                PRE {margin:0px; display:inline}
                DIV {border:0; padding:0; margin:0;}
            </STYLE>
            <SCRIPT><xsl:comment><![CDATA[

function click(event) {

    var mark = event.target;

    while ((mark.className != "b") && (mark.nodeName != "BODY")) {
        mark = mark.parentNode
    }
    
    var e = mark;
    
    while ((e.className != "e") && (e.nodeName != "BODY")) {
        e = e.parentNode
    }
    
    if (mark.childNodes[0].nodeValue == "+") {
        mark.childNodes[0].nodeValue = "-";
        for (var i = 2; i < e.childNodes.length; i++) {
            var name = e.childNodes[i].nodeName;
            if (name != "#text") {
                if (name == "PRE" || name == "SPAN") {
                   window.status = "inline";
                   e.childNodes[i].style.display = "inline";
                } else {
                   e.childNodes[i].style.display = "block";
                }
            }
        }
    } else if (mark.childNodes[0].nodeValue == "-") {
        mark.childNodes[0].nodeValue = "+";
        for (var i = 2; i < e.childNodes.length; i++) {
            if (e.childNodes[i].nodeName != "#text") {
                e.childNodes[i].style.display = "none";
            }
        }
    }
}  
  
]]></xsl:comment>
         </SCRIPT>
         </HEAD>
         <BODY>
            <xsl:apply-templates/>
         </BODY>
      </HTML>
   </xsl:template>

   <!-- match processing instructions -->
   <xsl:template match="processing-instruction()">
      <DIV class="e">
         <SPAN class="m">
            <xsl:text>&lt;?</xsl:text>
         </SPAN>
         <SPAN class="pi">
            <xsl:value-of select="name(.)"/>
            <xsl:value-of select="."/>
         </SPAN>
         <SPAN class="m">
            <xsl:text>?></xsl:text>
         </SPAN>
      </DIV>
   </xsl:template>

   <!-- match text -->
   <xsl:template match="text()">
      <DIV class="e">
         <SPAN class="t">
            <xsl:value-of select="."/>
         </SPAN>
      </DIV>
   </xsl:template>

   <!-- match comments -->
   <xsl:template match="comment()">
      <DIV class="e">
         <SPAN class="b" onclick="click(event)">-</SPAN>
         <SPAN class="m">
            <xsl:text>&lt;!--</xsl:text>
         </SPAN>
         <SPAN class="c">
            <PRE>
               <xsl:value-of select="."/>
            </PRE>
         </SPAN>
         <SPAN class="m">
            <xsl:text>--></xsl:text>
         </SPAN>
      </DIV>
   </xsl:template>

   <!-- match attributes -->
   <xsl:template match="@*">
      <SPAN class="an">
         <xsl:value-of select="name(.)"/>
      </SPAN>
      <SPAN class="m">="</SPAN>
      <SPAN class="av">
         <xsl:value-of select="."/>
      </SPAN>
      <SPAN class="m">"</SPAN>
      <xsl:if test="position()!=last()">
         <xsl:text> </xsl:text>
      </xsl:if>
   </xsl:template>
   
   <!-- match empty nodes -->
   <xsl:template match="*">
      <DIV class="e">
        <SPAN class="m">&lt;</SPAN>
        <SPAN class="en">
           <xsl:value-of select="name(.)"/>
        </SPAN>
        <xsl:if test="@*">
           <xsl:text> </xsl:text>
        </xsl:if>
        <xsl:apply-templates select="@*"/>
        <SPAN class="m">
           <xsl:text>/></xsl:text>
        </SPAN>
      </DIV>
   </xsl:template>

   <xsl:template match="*[node()]">
      <DIV class="e">
         <DIV>
            <SPAN class="b" onclick="click(event)">-</SPAN>
            <SPAN class="m">&lt;</SPAN>
            <SPAN class="en">
               <xsl:value-of select="name(.)"/>
            </SPAN>
            <xsl:if test="@*">
               <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:apply-templates select="@*"/>
            <SPAN class="m">
               <xsl:text>></xsl:text>
            </SPAN>
         </DIV>
         <DIV>
            <xsl:apply-templates/>
            <DIV>
               <SPAN class="m">
                  <xsl:text>&lt;/</xsl:text>
               </SPAN>
               <SPAN class="en">
                  <xsl:value-of select="name(.)"/>
               </SPAN>
               <SPAN class="m">
                  <xsl:text>></xsl:text>
               </SPAN>
            </DIV>
         </DIV>
      </DIV>
   </xsl:template>

   <xsl:template match="*[text() and not (comment() or processing-instruction())]">
      <DIV class="e">
        <SPAN class="m">
           <xsl:text>&lt;</xsl:text>
        </SPAN>
        <SPAN class="en">
           <xsl:value-of select="name(.)"/>
        </SPAN>
        <xsl:if test="@*">
           <xsl:text> </xsl:text>
        </xsl:if>
        <xsl:apply-templates select="@*"/>
        <SPAN class="m">
           <xsl:text>></xsl:text>
        </SPAN>
        <SPAN class="t">
           <xsl:value-of select="."/>
        </SPAN>
        <SPAN class="m">&lt;/</SPAN>
        <SPAN class="en">
           <xsl:value-of select="name(.)"/>
        </SPAN>
        <SPAN class="m">
           <xsl:text>></xsl:text>
        </SPAN>
      </DIV>
   </xsl:template>

   <xsl:template match="*[*]" priority="20">
      <DIV class="e">
         <DIV>
            <SPAN class="b" onclick="click(event)">-</SPAN>
            <SPAN class="m">&lt;</SPAN>
            <SPAN class="en">
               <xsl:value-of select="name(.)"/>
            </SPAN>
            <xsl:if test="@*">
               <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:apply-templates select="@*"/>
            <SPAN class="m">
               <xsl:text>></xsl:text>
            </SPAN>
         </DIV>
         <DIV>
            <xsl:apply-templates/>
            <DIV>
               <SPAN class="m">
                  <xsl:text>&lt;/</xsl:text>
               </SPAN>
               <SPAN class="en">
                  <xsl:value-of select="name(.)"/>
               </SPAN>
               <SPAN class="m">
                  <xsl:text>></xsl:text>
               </SPAN>
            </DIV>
         </DIV>
      </DIV>
   </xsl:template>

</xsl:stylesheet>
