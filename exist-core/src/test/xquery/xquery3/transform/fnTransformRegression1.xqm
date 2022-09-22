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

declare
    %test:setUp
function testTransform:setup() {
    let $coll := xmldb:create-collection("/db", "regression-test-1")
    return (
        xmldb:store($coll, "stylesheet.xsl", $testTransform:stylesheet, "application/xslt+xml")
    )
};

declare
    %test:tearDown
function testTransform:cleanup() {
    xmldb:remove("/db/regression-test-1")
};

declare
    %test:assertEquals("<v>2</v>")
function testTransform:regression-test-1() {
    let $in := parse-xml("<dummy/>")
    let $result := ( fn:transform(map{
        "source-node":$in,
        "stylesheet-node":doc("/db/regression-test-1/stylesheet.xsl"),
        "stylesheet-params": map { QName("","v"): "2" } } ) )?output
    return $result
};

