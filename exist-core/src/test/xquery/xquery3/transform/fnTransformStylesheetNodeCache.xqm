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
 : Compiled stylesheets are cached. A stylesheet-node must only be found in the cache if it is the same
 : stylesheet: in-memory stylesheets that share a base URI are different stylesheets, and a stored
 : stylesheet that has been changed must be compiled again.
 :)
module namespace testTransform="http://exist-db.org/xquery/test/function_transform";
import module namespace xmldb="http://exist-db.org/xquery/xmldb";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $testTransform:collection := "/db/transform-stylesheet-node-cache";

declare function testTransform:stylesheet($output as xs:string) as element() {
    <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
        <xsl:template name="main"><out>{$output}</out></xsl:template>
    </xsl:stylesheet>
};

declare function testTransform:run($stylesheet as node(), $options as map(*)) as xs:string {
    string(transform(map:merge((
        $options,
        map { "stylesheet-node": $stylesheet, "initial-template": QName("", "main") }
    )))?output)
};

declare
    %test:setUp
function testTransform:setUp() {
    xmldb:create-collection("/db", "transform-stylesheet-node-cache")
};

declare
    %test:tearDown
function testTransform:tearDown() {
    xmldb:remove($testTransform:collection)
};

declare
    %test:assertEquals("A", "B")
function testTransform:in-memory-stylesheets-without-base-uri() {
    testTransform:run(testTransform:stylesheet("A"), map {}),
    testTransform:run(testTransform:stylesheet("B"), map {})
};

declare
    %test:assertEquals("A", "B")
function testTransform:in-memory-stylesheets-with-the-same-base-uri() {
    let $options := map { "stylesheet-base-uri": "http://example.com/stylesheet.xsl" }
    return (
        testTransform:run(testTransform:stylesheet("A"), $options),
        testTransform:run(testTransform:stylesheet("B"), $options)
    )
};

declare
    %test:assertEquals("A", "B")
function testTransform:stored-stylesheet-changed() {
    let $path := xmldb:store($testTransform:collection, "stylesheet.xsl", testTransform:stylesheet("A"))
    let $first := testTransform:run(doc($path)/*, map {})
    let $_ := xmldb:store($testTransform:collection, "stylesheet.xsl", testTransform:stylesheet("B"))
    return ($first, testTransform:run(doc($path)/*, map {}))
};
