<?xml version="1.0" encoding="UTF-8"?>
<!--
  Copyright 1999-2004 The Apache Software Foundation

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--><!--+
    | XSLT REC Compliant Version of IE5 Default Stylesheet
    |
    | Original version by Jonathan Marsh (jmarsh@microsoft.com)
    | Conversion to XSLT 1.0 REC Syntax by Steve Muench (smuench@oracle.com)
    | Added script support by Andrew Timberlake (andrew@timberlake.co.za)
    | Cleaned up and ported to standard DOM by Stefano Mazzocchi (stefano@apache.org)
    |
    | CVS $Id$
    +-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:template match="/">
        <HTML>
            <xsl:call-template name="head"/>
            <BODY>
                <xsl:apply-templates/>
            </BODY>
        </HTML>
    </xsl:template>
    <xsl:template name="head">
        <HEAD>
            <STYLE>
              BODY  {background-color: white; color: black; font-family: monospace;}
                .b  {cursor:pointer; color:red; font-weight:bold; text-decoration:none; padding-right: 2px;}
                .e  {border: 0px; padding: 0px; margin: 0px 0px 0px 2em; text-indent:-1em;}
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
            <SCRIPT>
                <xsl:comment><![CDATA[
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
    </xsl:template>

   <!-- match processing instructions -->
    <xsl:template match="processing-instruction()">
        <DIV class="e">
            <SPAN class="m">&lt;?</SPAN>
            <SPAN class="pi">
                <xsl:value-of select="name(.)"/>
                <xsl:text> </xsl:text>
                <xsl:value-of select="."/>
            </SPAN>
            <SPAN class="m">?&gt;</SPAN>
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
            <SPAN class="m">&lt;!--</SPAN>
            <SPAN class="c">
                <PRE>
                    <xsl:value-of select="."/>
                </PRE>
            </SPAN>
            <SPAN class="m">--&gt;</SPAN>
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
   
   <!-- match empty elements -->
    <xsl:template match="*[not(node())]">
        <DIV class="e">
            <SPAN class="m">&lt;</SPAN>
            <SPAN class="en">
                <xsl:value-of select="name(.)"/>
            </SPAN>
            <xsl:if test="@*">
                <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="." mode="namespace"/>
            <SPAN class="m">/&gt;</SPAN>
        </DIV>
    </xsl:template>

   <!-- match elements with only text(), they are not closeable -->
    <xsl:template match="*[text()][not(* or comment() or processing-instruction())]" priority="10">
        <DIV class="e">
            <SPAN class="m">&lt;</SPAN>
            <SPAN class="en">
                <xsl:value-of select="name(.)"/>
            </SPAN>
            <xsl:if test="@*">
                <xsl:text> </xsl:text>
            </xsl:if>
            <xsl:apply-templates select="@*"/>
            <xsl:apply-templates select="." mode="namespace"/>
            <SPAN class="m">
                <xsl:text>&gt;</xsl:text>
            </SPAN>
            <SPAN class="t">
                <xsl:value-of select="."/>
            </SPAN>
            <SPAN class="m">&lt;/</SPAN>
            <SPAN class="en">
                <xsl:value-of select="name(.)"/>
            </SPAN>
            <SPAN class="m">
                <xsl:text>&gt;</xsl:text>
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
                <xsl:apply-templates select="." mode="namespace"/>
                <SPAN class="m">
                    <xsl:text>&gt;</xsl:text>
                </SPAN>
            </DIV>
            <DIV>
                <xsl:apply-templates/>
                <DIV>
                    <SPAN class="m">&lt;/</SPAN>
                    <SPAN class="en">
                        <xsl:value-of select="name(.)"/>
                    </SPAN>
                    <SPAN class="m">
                        <xsl:text>&gt;</xsl:text>
                    </SPAN>
                </DIV>
            </DIV>
        </DIV>
    </xsl:template>
    <xsl:template match="*" mode="namespace">
        <xsl:variable name="context" select="."/>
        <xsl:for-each select="namespace::node()">
            <xsl:variable name="nsuri" select="."/>
            <xsl:variable name="nsprefix" select="name()"/>
            <xsl:choose>
                <xsl:when test="$nsprefix = 'xml'">
          <!-- xml namespace -->
                </xsl:when>
                <xsl:when test="$context/../namespace::node()[name() = $nsprefix and . = $nsuri]">
          <!-- namespace already declared on the parent -->
                </xsl:when>
                <xsl:otherwise>
                    <xsl:text> </xsl:text>
                    <SPAN class="an">
                        <xsl:text>xmlns</xsl:text>
                        <xsl:if test="$nsprefix">
                            <xsl:text>:</xsl:text>
                            <xsl:value-of select="$nsprefix"/>
                        </xsl:if>
                    </SPAN>
                    <SPAN class="m">="</SPAN>
                    <SPAN class="av">
                        <xsl:value-of select="."/>
                    </SPAN>
                    <SPAN class="m">"</SPAN>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>
</xsl:stylesheet>