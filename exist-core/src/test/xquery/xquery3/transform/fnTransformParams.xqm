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

declare variable $testTransform:transform-68-xsl := document { <xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
                    xmlns:xs='http://www.w3.org/2001/XMLSchema'
                    xmlns:saxon='http://saxon.sf.net/'
                    xmlns:my='http://www.w3.org/fots/fn/transform/myfunctions' version='2.0'>
                    <xsl:param name='v'/>
                    <xsl:template name='main'>
                      <out><xsl:value-of select='$v'/></out>
                    </xsl:template>
                </xsl:stylesheet> };

declare variable $testTransform:transform-68-xsl-text := "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
                    xmlns:xs='http://www.w3.org/2001/XMLSchema'
                    xmlns:saxon='http://saxon.sf.net/'
                    xmlns:my='http://www.w3.org/fots/fn/transform/myfunctions' version='2.0'>
                    <xsl:param name='v'/>
                    <xsl:template name='main'>
                      <out><xsl:value-of select='$v'/></out>
                    </xsl:template>
                </xsl:stylesheet>";

declare variable $testTransform:transform-33-xsl := "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='3.0'>
                                                                    <xsl:template match='/'> <xsl:for-each select='//section'>
                                                                    <xsl:result-document href='section{position()}.html'> <!-- instructions content here -->
                                                                    </xsl:result-document> </xsl:for-each>
                                                                    </xsl:template> </xsl:stylesheet>";

declare variable $testTransform:transform-33-xml := "<doc>
                                                                                                       <section>sect1</section>
                                                                                                       <section>sect2</section>
                                                                                                       <section>sect3</section>
                                                                                                       </doc>";
declare
    %test:assertError("err:FODC0002")
function testTransform:transform-stylesheet-location() {
    let $result := fn:transform(map {"stylesheet-location" : "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl",
                                                   "initial-template" : QName('','main'),
                                                   "stylesheet-base-uri": "http://www.example.com"})
    return $result?output
};

declare
    %test:assertError("err:FODC0002")
function testTransform:transform-stylesheet-location-relative() {
    let $result := fn:transform(map {"stylesheet-location" : "transform/staticbaseuri.xsl",
                                                   "initial-template" : QName('','main'),
                                                   "stylesheet-base-uri": "http://www.example.com"})
    return $result?output
};

declare
    %test:assertTrue
function testTransform:transform-stylesheet-ok() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "base-output-uri" : resolve-uri("transform/sandbox/fn-transform-33.xml", "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl"),
                "delivery-format":"serialized"})
    return (contains(string-join(map:keys($result)),"section2"))
};

declare
    %test:assertError("err:XPTY0004")
function testTransform:transform-stylesheet-bad-xslt-version() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "xslt-version": "hi",
                "base-output-uri" : resolve-uri("transform/sandbox/fn-transform-33.xml", "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl"),
                "delivery-format":"serialized"})
    return (contains(string-join(map:keys($result)),"section2"))
};

declare
    %test:assertError("err:FOXT0001")
function testTransform:transform-stylesheet-invalid-xslt-version() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "xslt-version": 4.0,
                "base-output-uri" : resolve-uri("transform/sandbox/fn-transform-33.xml", "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl"),
                "delivery-format":"serialized"})
    return (contains(string-join(map:keys($result)),"section2"))
};

declare
    %test:assertError("err:FOXT0002")
function testTransform:transform-stylesheet-source-node-and-initial-match() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "initial-match-selection" : 1 to 5,
                "delivery-format":"serialized"})
    return (contains(string-join(map:keys($result)),"section2"))
};

(: cannot have stylesheet-node and stylesheet-text :)
declare
    %test:assertError("err:FOXT0002")
function testTransform:transform-stylesheet-node-and-text() {
    let $xsl := $testTransform:transform-68-xsl
    let $xsl-text := $testTransform:transform-68-xsl-text
    let $result := fn:transform(map {"stylesheet-node" : $xsl,
    "stylesheet-text" : $xsl-text,
                                                   "initial-template" : QName('','main'),
                                                   "stylesheet-base-uri": "http://www.example.com"})
    return $result?output
};

(: need to supply a stylesheet, error if we don't :)
declare
    %test:assertError("err:FOXT0002")
function testTransform:transform-stylesheet-not-provided() {
    let $result := fn:transform(map {"initial-template" : QName('','main'),
                                                   "stylesheet-base-uri": "http://www.example.com"})
    return $result?output
};

(: a bad requested property type :)
declare
    %test:assertError("err:XPTY0004")
function testTransform:transform-stylesheet-bad-delivery() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "base-output-uri" : resolve-uri("transform/sandbox/fn-transform-33.xml", "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl"),
                "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','supports-dynamic-evaluation'): 2.5 }})
    return (contains(string-join(map:keys($result)),"section2"))
};


(: a bad requested property key :)
declare
    %test:assertError("err:XPTY0004")
function testTransform:transform-stylesheet-bad-delivery-key-not-qname() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "base-output-uri" : resolve-uri("transform/sandbox/fn-transform-33.xml", "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl"),
                "requested-properties" : map{'supports-dynamic-evaluation': false() }})
    return (contains(string-join(map:keys($result)),"section2"))
};



