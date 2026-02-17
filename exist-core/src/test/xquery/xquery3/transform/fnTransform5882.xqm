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

module namespace testTransform="http://exist-db.org/xquery/test/function_transform";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";

(:~
 : Regression test for PR 5882: fn:transform with MemTree nodes.
 : A bare element constructor (element root { ... }) creates a MemTree node whose
 : getParentNode() returns null (see issue #1463). We use initial-match-selection
 : because that path calls Convert.ofNode/treeIndex. Without the TreeUtils fix:
 : - For root: treeIndex returns [], xdmNodeAtIndex returns doc (wrong but works)
 : - For second child: treeIndex returns [1] (missing leading 0 for root), doc has
 :   only one child, so xdmNodeAtIndex(doc, [1]) returns null, causing failure.
 : @see https://github.com/eXist-db/exist/pull/5882
 :)
declare variable $testTransform:transform-5882-simple-xsl := <xsl:stylesheet version='1.0'>
    <xsl:template match='*'>
        <out><xsl:value-of select='.'/></out>
    </xsl:template>
</xsl:stylesheet>;

declare variable $testTransform:transform-5882-map-array-xsl := <xsl:stylesheet version='3.0'>
    <xsl:param name='m' as='map(*)'/>
    <xsl:param name='a' as='array(*)'/>
    <xsl:template name='main'>
        <out>
            <xsl:attribute name='map-val' select='$m("k")'/>
            <xsl:attribute name='array-val' select='$a(2)'/>
        </out>
    </xsl:template>
</xsl:stylesheet>;

declare variable $testTransform:transform-5882-stored-doc := <root id="attr"><child>stored-val</child></root>;

declare variable $testTransform:transform-5882-coll := "/db/fn-transform-5882-stored";

declare
    %test:setUp
function testTransform:setup() {
    xmldb:create-collection("/db", "fn-transform-5882-stored"),
    xmldb:store($testTransform:transform-5882-coll, "doc.xml", $testTransform:transform-5882-stored-doc)
};

declare
    %test:tearDown
function testTransform:tearDown() {
    xmldb:remove($testTransform:transform-5882-coll)
};

(:~
 : Select the second child of a MemTree root. Without the fix, treeIndex(b) = [1]
 : (missing index 0 for root), but doc has only one child; xdmNodeAtIndex returns null.
 :)
declare
    %test:assertEquals('<out>second</out>')
function testTransform:memtree-second-child() {
    let $source := element root { <a>first</a>, <b>second</b> }
    let $selection := $source/b
    let $result := (fn:transform(map{
        "initial-match-selection": $selection,
        "stylesheet-node": $testTransform:transform-5882-simple-xsl
    }))?output
    return $result
};

(:~
 : PR 5882 adds Convert.ofMap/ofArray for stylesheet params. Without it, map/array
 : params cause XPTY0004. XSLT 3.0 required for map(*) and array(*) param types.
 : @see https://github.com/eXist-db/exist/pull/5882
 :)
declare
    %test:assertEquals('v', 'y')
function testTransform:stylesheet-params-map-array() {
    let $result := fn:transform(map{
        "stylesheet-node": $testTransform:transform-5882-map-array-xsl,
        "initial-template": QName('','main'),
        "stylesheet-params": map {
            QName('','m'): map{'k':'v'},
            QName('','a'): ['x','y','z']
        }
    })?output
    return
       ($result/out/@map-val/string(), $result/out/@array-val/string())

};

(:~
 : PR 5882 fixes TreeUtils.previousSiblingNotAttribute for StoredNode: attributes
 : appear as previous siblings of element children. Store a doc with attrs, select
 : the child element as initial-match-selection.
 : @see https://github.com/eXist-db/exist/pull/5882
 :)
declare
    %test:assertEquals('<out>stored-val</out>')
function testTransform:stored-doc-with-attributes() {
    let $selection := doc($testTransform:transform-5882-coll || "/doc.xml")/root/child
    let $result := (fn:transform(map{
        "initial-match-selection": $selection,
        "stylesheet-node": $testTransform:transform-5882-simple-xsl
    }))?output
    return $result
};
