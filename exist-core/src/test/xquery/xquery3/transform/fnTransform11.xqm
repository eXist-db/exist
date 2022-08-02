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

declare variable $testTransform:transform-11-xsl := <xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='2.0'
            xmlns:app='http://www.example.com'>
            <xsl:template match='/'>
                <x><xsl:value-of select='.' /></x>
            </xsl:template>
                <xsl:template name='app:main'>
                    <out>that</out>
                </xsl:template>
            </xsl:stylesheet>;

declare
    %test:assertTrue
function testTransform:transform-11-2() {
    let $style := $testTransform:transform-11-xsl
    let $result := transform(map{"stylesheet-node":$style, "initial-template": fn:QName('http://www.example.com','main') })
    return map:contains($result, "output")
};

declare
    %test:assertTrue
function testTransform:transform-11-3() {
    let $style := $testTransform:transform-11-xsl
    let $result := transform(map{"stylesheet-node":$style, "initial-template": fn:QName('http://www.example.com','main') })
    return $result?output instance of node()
};

declare
    %test:assertTrue
function testTransform:transform-11-4() {
    let $style := $testTransform:transform-11-xsl
    let $result := transform(map{"stylesheet-node":$style, "initial-template": fn:QName('http://www.example.com','main') })
    return $result?output//out = 'that'
};

