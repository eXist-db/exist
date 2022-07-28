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

declare variable $testTransform:transform-68-xsl-text := document { <xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
                    xmlns:xs='http://www.w3.org/2001/XMLSchema'
                    xmlns:saxon='http://saxon.sf.net/'
                    xmlns:my='http://www.w3.org/fots/fn/transform/myfunctions' version='2.0'>
                    <xsl:template name='main'>
                      <out><xsl:value-of select="saxon:in-summer-time(xs:dateTime('2016-07-01T00:00:00'), 'Europe/London')"/></out>
                    </xsl:template>
                </xsl:stylesheet> };

declare
    %test:assertTrue
function testTransform:transform-68() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','vendor'):'Saxonica'}})
    return contains($result?output,">true</out>")
};

