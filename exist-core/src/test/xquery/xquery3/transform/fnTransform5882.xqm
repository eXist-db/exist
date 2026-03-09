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

(:~
 : Pass in-memory and persistent nodes as well as function types to fn:transform
 : in order to ensure proper type conversions between eXist-db's and Saxon's
 : implementations.
 : @see https://github.com/eXist-db/exist/pull/5882
 :)
module namespace fnTransform5882="http://exist-db.org/xquery/test/function_transform";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";

declare variable $fnTransform5882:simple-xsl :=
    <xsl:stylesheet version='1.0'>
        <xsl:output omit-xml-declaration="yes"/>
        <xsl:template match='*'>
            <out><xsl:value-of select='.'/></out>
        </xsl:template>
    </xsl:stylesheet>
;

declare variable $fnTransform5882:map-array-xsl :=
    <xsl:stylesheet version='3.0'>
        <xsl:output omit-xml-declaration="yes"/>
        <xsl:param name='map-parameter' as='map(*)'/>
        <xsl:param name='array-parameter' as='array(*)'/>
        <xsl:template name='main'>
            <out>
                <xsl:attribute name='value-of-k-in-map' select='$map-parameter("k")'/>
                <xsl:attribute name='second-array-member' select='$array-parameter(2)'/>
            </out>
        </xsl:template>
    </xsl:stylesheet>
;

declare variable $fnTransform5882:collection-name := "fn-transform-5882-stored";
declare variable $fnTransform5882:collection := "/db/" || $fnTransform5882:collection-name;

declare variable $fnTransform5882:direct-element-constructor := <root attr="val"><a>first</a><b>second</b></root>;
declare variable $fnTransform5882:computed-element-constructor := element root { element a { "first" }, element b { "second" } };

declare
    %test:setUp
function fnTransform5882:setup() {
    xmldb:create-collection("/db", $fnTransform5882:collection-name),
    xmldb:store($fnTransform5882:collection, "doc.xml", $fnTransform5882:direct-element-constructor)
};

declare
    %test:tearDown
function fnTransform5882:tearDown() {
    xmldb:remove($fnTransform5882:collection)
};

(:~
 : Select the second child of the root element of an in-memory tree constructed using a direct-element constructor
 : org.exist.dom.memtree.NodeImpl.getParentNode() can return null for a non-document node, if it was constructed.
 : This was introduced in 4ce606a08e719b9aabd730a9af6e05ea7485f38e to fix
 : @see https://github.com/eXist-db/exist/issues/1463
 :)
declare
    %test:assertEquals('<out>second</out>')
function fnTransform5882:direct-element-constructor-second-child() {
    fn:transform(map{
        "initial-match-selection": $fnTransform5882:direct-element-constructor/b,
        "stylesheet-node": $fnTransform5882:simple-xsl,
        "delivery-format": "serialized"
    })?output
};

(:~
 : Select the second child of the root element of an in-memory tree constructed using a computed-element constructor
 : org.exist.dom.memtree.NodeImpl.getParentNode() can return null for a non-document node, if it was constructed.
 :)
declare
    %test:assertEquals('<out>second</out>')
function fnTransform5882:computed-element-constructor-second-child() {
    fn:transform(map{
        "initial-match-selection": $fnTransform5882:computed-element-constructor/b,
        "stylesheet-node": $fnTransform5882:simple-xsl,
        "delivery-format": "serialized"
    })?output
};

(:~
 : Select the first child of a root element with an attribute in a persistent node tree
 : to ensure proper translation to Saxon's implementation in TreeUtils.treeIndex
 :)
declare
    %test:assertEquals('<out>first</out>')
function fnTransform5882:stored-doc-with-attributes() {
    fn:transform(map{
        "initial-match-selection": doc($fnTransform5882:collection || "/doc.xml")/root/a,
        "stylesheet-node": $fnTransform5882:simple-xsl,
        "delivery-format": "serialized"
    })?output
};

(:~
 : Pass function types to fn:transform and retrieve them in the stylesheet to ensure these types
 : are properly translated to the datatypes used by Saxon
 :)
declare
    %test:assertEquals('<out value-of-k-in-map="v" second-array-member="y"/>')
function fnTransform5882:stylesheet-params-map-array() {
    fn:transform(map{
        "stylesheet-node": $fnTransform5882:map-array-xsl,
        "initial-template": xs:QName('main'),
        "stylesheet-params": map {
            xs:QName('map-parameter'): map{ 'k': 'v' },
            xs:QName('array-parameter'): [ 'x','y','z' ]
        },
        "delivery-format": "serialized"
    })?output
};
