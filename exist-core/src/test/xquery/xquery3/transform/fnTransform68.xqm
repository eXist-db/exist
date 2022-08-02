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
                    <xsl:param name='v'/>
                    <xsl:template name='main'>
                      <out><xsl:value-of select='$v'/></out>
                    </xsl:template>
                </xsl:stylesheet> };

declare
    %test:assertError("FOXT0001")
function testTransform:transform-68-supports-dynamic-evaluation() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "stylesheet-params": map { QName("","v"): "2" },
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','supports-dynamic-evaluation'):true()}})
    return contains($result?output,">2</out>")
};

declare
    %test:assertError("FOXT0001")
function testTransform:transform-68-supports-xalan() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "stylesheet-params": map { QName("","v"): "2" },
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','product-name'):"Xalan"}})
    return contains($result?output,">2</out>")
};

declare
    %test:assertTrue
function testTransform:transform-68-supports-saxon() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "stylesheet-params": map { QName("","v"): "2" },
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','product-name'):"SAXON"}})
    return contains($result?output,">2</out>")
};

declare
    %test:assertTrue
function testTransform:transform-68-vendor-saxonica() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "stylesheet-params": map { QName("","v"): "2" },
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','vendor'):"Saxonica"}})
    return contains($result?output,">2</out>")
};

declare
    %test:assertError("XPTY0004")
function testTransform:transform-68-vendor-empty() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "stylesheet-params": map { QName("","v"): "2" },
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','vendor'):()}})
    return contains($result?output,">2</out>")
};

declare
     %test:assertError("FOXT0001")
function testTransform:transform-68-unknown-property() {
    let $xsl := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map{
        "stylesheet-node":$xsl,
        "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "stylesheet-params": map { QName("","v"): "2" },
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','wookie'):"Chewbacca"}})
    return contains($result?output,">2</out>")
};
