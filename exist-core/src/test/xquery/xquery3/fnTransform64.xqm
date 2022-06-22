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

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $testTransform:transform-64-xsl := document {
    <xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
                  xmlns:xs='http://www.w3.org/2001/XMLSchema'
                  xmlns:my='http://www.w3.org/fots/fn/transform/myfunctions' version='3.0'>
                    <xsl:function name='my:user-function' as='xs:integer*' visibility='public'>
                        <xsl:param name='param1'/>
                        <xsl:param name='param2'/>
                        <xsl:sequence select='$param1 to $param2'/>
                    </xsl:function>
                </xsl:stylesheet> };

declare
    %test:assertEquals(1,2,3,4,5)
function testTransform:transform-64-xsl() {
    let $xsl := $testTransform:transform-64-xsl
    let $result := fn:transform(map{"stylesheet-node":$xsl,
    "initial-function": fn:QName('http://www.w3.org/fots/fn/transform/myfunctions','user-function'),
                                     "function-params": [1,5],
                                     "delivery-format": "raw" })
    return $result?output
};
