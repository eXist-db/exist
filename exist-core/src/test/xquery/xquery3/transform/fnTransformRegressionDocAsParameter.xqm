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

declare variable $testTransform:stylesheet := <xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>
    <xsl:param name='v'/>
    <xsl:template match='/'>
        <v><xsl:value-of select='$v'/></v>
    </xsl:template>
</xsl:stylesheet>;

declare variable $testTransform:document := <document>
    <catalog>
        <book id="bk101">
           <author>Gambardella, Matthew</author>
           <title>XML Developer's Guide</title>
           <genre>Computer</genre>
           <price>44.95</price>
           <publish_date>2000-10-01</publish_date>
           <description>An in-depth look at creating applications
           with XML.</description>
        </book>
        <book id="bk102">
           <author>Ralls, Kim</author>
           <title>Midnight Rain</title>
           <genre>Fantasy</genre>
           <price>5.95</price>
           <publish_date>2000-12-16</publish_date>
           <description>A former architect battles corporate zombies,
           an evil sorceress, and her own childhood to become queen
           of the world.</description>
        </book>
    </catalog>
</document>;

declare
    %test:setUp
function testTransform:setup() {
    let $coll := xmldb:create-collection("/db", "regression-test")
    let $storeStylesheet := xmldb:store($coll, "stylesheet.xsl", $testTransform:stylesheet, "application/xslt+xml")
    return ( xmldb:store($coll, "document.xml", $testTransform:document, "application/document")
    )
};

declare
    %test:tearDown
function testTransform:cleanup() {
    xmldb:remove("/db/regression-test")
};

declare
    %test:assertEquals("<v>Gambardella, MatthewXML Developer's GuideComputer44.952000-10-01An in-depth look at creating applications
           with XML.Ralls, KimMidnight RainFantasy5.952000-12-16A former architect battles corporate zombies,
           an evil sorceress, and her own childhood to become queen
           of the world.</v>")
function testTransform:regression-test-1() {
    let $in := parse-xml("<dummy/>")
    let $result := ( fn:transform(map{
        "source-node":$in,
        "stylesheet-node":doc("/db/regression-test/stylesheet.xsl"),
        "stylesheet-params": map { QName("","v"): doc("/db/regression-test/document.xml") } } ) )?output
    return $result
};

