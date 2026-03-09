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

module namespace doc-as-parameter="http://exist-db.org/xquery/test/doc-as-parameter";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";

declare variable $doc-as-parameter:collection-name := "doc-as-parameter";
declare variable $doc-as-parameter:collection := "/db/" || $doc-as-parameter:collection-name;

declare variable $doc-as-parameter:source-doc-name := "source-document.xml";


declare variable $doc-as-parameter:stylesheet-unnamed-template :=
<xsl:stylesheet version='1.0'>
    <xsl:output omit-xml-declaration="yes"/>
    <xsl:param name='v'/>
    <xsl:template match='/'>
        <v><xsl:value-of select='$v'/></v>
    </xsl:template>
</xsl:stylesheet>
;

declare variable $doc-as-parameter:stylesheet-named-template :=
<xsl:stylesheet version='1.0'>
    <xsl:output omit-xml-declaration="yes"/>
    <xsl:param name='v'/>
    <xsl:template name='named-template' match='/'>
        <v><xsl:value-of select='$v'/></v>
    </xsl:template>
</xsl:stylesheet>
;

declare variable $doc-as-parameter:document :=
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
function doc-as-parameter:setup() {
    xmldb:create-collection("/db", $doc-as-parameter:collection-name),
    xmldb:store($doc-as-parameter:collection, "unnamed-template.xsl", $doc-as-parameter:stylesheet-unnamed-template, "application/xslt+xml"),
    xmldb:store($doc-as-parameter:collection, "with-named-template.xsl", $doc-as-parameter:stylesheet-named-template, "application/xslt+xml"),
    xmldb:store($doc-as-parameter:collection, $doc-as-parameter:source-doc-name, $doc-as-parameter:document, "application/document")
};

declare
    %test:tearDown
function doc-as-parameter:cleanup() {
    xmldb:remove($doc-as-parameter:collection)
};

declare
    %test:assertEquals("<v>2</v>")
function doc-as-parameter:unnamed-template-integer() {
    transform(map{
        "source-node": document { <dummy/> },
        "stylesheet-node": doc($doc-as-parameter:collection || "/unnamed-template.xsl"),
        "stylesheet-params": map { xs:QName("v"): "2" },
        "delivery-format": "serialized"
    })?output
};

declare
    %test:assertEquals("<v>2</v>")
function doc-as-parameter:named-template-integer() {
    transform(map{
        "source-node": document { <dummy/> },
        "stylesheet-node": doc($doc-as-parameter:collection || "/with-named-template.xsl"),
        "initial-template": xs:QName('named-template'),
        "stylesheet-params": map { xs:QName("v"): "2" },
        "delivery-format": "serialized"
    })?output
};

declare
    %test:assertEquals("<v>Gambardella, MatthewRalls, Kim</v>")
function doc-as-parameter:unnamed-template-document() {
    transform(map{
        "source-node": document { <dummy/> },
        "stylesheet-node": doc($doc-as-parameter:collection || "/unnamed-template.xsl"),
        "stylesheet-params": map {
            xs:QName("v"): doc($doc-as-parameter:collection || "/" || $doc-as-parameter:source-doc-name)
        },
        "delivery-format": "serialized"
    })?output
};

declare
    %test:assertEquals("<v>Gambardella, MatthewRalls, Kim</v>")
function doc-as-parameter:named-template-document() {
    transform(map{
        "source-node": document { <dummy/> },
        "stylesheet-node": doc($doc-as-parameter:collection || "/with-named-template.xsl"),
        "initial-template": xs:QName('named-template'),
        "global-context-item" : doc($doc-as-parameter:collection || $doc-as-parameter:source-doc-name),
        "stylesheet-params": map {
            xs:QName('v'): doc($doc-as-parameter:collection || "/" || $doc-as-parameter:source-doc-name)
        },
        "delivery-format": "serialized"
    })?output
};
