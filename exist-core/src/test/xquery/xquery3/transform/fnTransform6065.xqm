(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

module namespace t6065="http://exist-db.org/xquery/test/t6065";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";

declare variable $t6065:collection-name := "test-issue-6065";
declare variable $t6065:collection := "/db/" || $t6065:collection-name;

declare variable $t6065:source-doc-name := "source-document.xml";


declare variable $t6065:stylesheet-unnamed-template :=
<xsl:stylesheet version='1.0'>
    <xsl:output omit-xml-declaration="yes"/>
    <xsl:param name='v'/>
    <xsl:template match='/'>
        <v><xsl:value-of select='$v'/></v>
    </xsl:template>
</xsl:stylesheet>
;

declare variable $t6065:stylesheet-named-template :=
<xsl:stylesheet version='1.0'>
    <xsl:output omit-xml-declaration="yes"/>
    <xsl:param name='v'/>
    <xsl:template name='named-template' match='/'>
        <v><xsl:value-of select='$v'/></v>
    </xsl:template>
</xsl:stylesheet>
;

declare variable $t6065:document :=
<document>
    <catalog>
        <book id="bk101">
           <author>Gambardella, Matthew</author>
        </book>
        <book id="bk102">
           <author>Ralls, Kim</author>
        </book>
    </catalog>
</document>
;

declare
    %test:setUp
function t6065:setup() {
    xmldb:create-collection("/db", $t6065:collection-name),
    xmldb:store($t6065:collection, "unnamed-template.xsl", $t6065:stylesheet-unnamed-template, "application/xslt+xml"),
    xmldb:store($t6065:collection, "with-named-template.xsl", $t6065:stylesheet-named-template, "application/xslt+xml"),
    xmldb:store($t6065:collection, $t6065:source-doc-name, $t6065:document, "application/document")
};

declare
    %test:tearDown
function t6065:cleanup() {
    xmldb:remove($t6065:collection)
};

declare
    %test:assertEquals("<v>Gambardella, MatthewRalls, Kim</v>")
function t6065:test-1() {
    transform(map{
        "source-node": document { <dummy/> },
        "stylesheet-node": doc($t6065:collection || "/unnamed-template.xsl"),
        "stylesheet-params": map {
            xs:QName("v"): doc($t6065:collection || "/" || $t6065:source-doc-name)
        }
(:        ,"delivery-format": "serialized":)
    })?output
(:    ,():)
};

declare
    %test:assertEquals("<v>2</v>")
function t6065:test-2() {
    transform(map{
        "source-node": document { <dummy/> },
        "stylesheet-node": doc($t6065:collection || "/unnamed-template.xsl"),
        "stylesheet-params": map { xs:QName("v"): "2" }
(:        ,"delivery-format": "serialized":)
    })?output
(:    ,():)
};

