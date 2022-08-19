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
    %test:assertTrue
function testTransform:transform-33() {
    let $xsl := $testTransform:transform-33-xsl
    let $xml := $testTransform:transform-33-xml
    let $result := fn:transform(map {"stylesheet-text": $xsl, "source-node": parse-xml($xml),
                "base-output-uri" : resolve-uri("transform/sandbox/fn-transform-33.xml", "http://www.w3.org/fots/fn/transform/staticbaseuri.xsl"),
                "delivery-format":"serialized"})
    return (contains(string-join(map:keys($result)),"section2"))
};
