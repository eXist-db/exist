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

declare variable $testTransform:render := <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                                    <xsl:param name="debug" select="false()"/>
                                    <xsl:template match="/">
                                        <xsl:if test="$debug">
                                            <xsl:message>STARTED</xsl:message>
                                        </xsl:if>
                                        <body>
                                            <xsl:copy-of select="works"/>
                                        </body>
                                    </xsl:template>
                                </xsl:stylesheet>;

declare variable $testTransform:doc := <doc><works>
                                  <employee name="Jane Doe 1" gender="female">
                                   <empnum>E1</empnum>
                                   <pnum>P1</pnum>
                                   <hours>40</hours>
                                  </employee>
                                  <employee name = "John Doe 2" gender="male">
                                   <empnum>E1</empnum>
                                   <pnum>P2</pnum>
                                   <hours>70</hours>
                                   <hours>20</hours>Text data from Employee[2]
                                  </employee>
                                  <employee name = "Jane Doe 3" gender="female">
                                   <empnum>E1</empnum>
                                   <pnum>P3</pnum>
                                   <hours>80</hours>
                                  </employee>
                                  <employee name= "John Doe 4" gender="male">
                                   <empnum>E1</empnum>
                                   <pnum>P4</pnum>
                                   <hours>20</hours>
                                   <hours>40</hours>Text data from Employee[4]
                                  </employee>
                                  <employee name= "Jane Doe 5" gender="female">
                                   <empnum>E1</empnum>
                                   <pnum>P5</pnum>
                                   <hours>20</hours>
                                   <hours>30</hours>
                                  </employee>
                                  <employee name= "John Doe 6" gender="male">
                                   <empnum>E1</empnum>
                                   <pnum>P6</pnum>
                                   <hours>12</hours>
                                  </employee>
                                  <employee name= "Jane Doe 7" gender="female">
                                   <empnum>E2</empnum>
                                   <pnum>P1</pnum>
                                   <hours>40</hours>
                                  </employee>
                                  <employee name= "John Doe 8" gender="male">
                                   <empnum>E2</empnum>
                                   <pnum>P2</pnum>
                                   <hours>80</hours>
                                  </employee>
                                  <employee name= "Jane Doe 9" gender="female">
                                   <empnum>E3</empnum>
                                   <pnum>P2</pnum>
                                   <hours>20</hours>
                                  </employee>
                                  <employee name= "John Doe 10" gender="male">
                                   <empnum>E3</empnum>
                                   <pnum>P2</pnum>
                                   <hours>20</hours>
                                  </employee>
                                  <employee name= "Jane Doe 11" gender="female">
                                   <empnum>E4</empnum>
                                   <pnum>P2</pnum>
                                   <hours>20</hours>
                                  </employee>
                                  <employee name= "John Doe 12" gender="male">
                                   <empnum>E4</empnum>
                                   <pnum>P4</pnum>
                                   <hours>40</hours>
                                   <overtime>
                                     <day>Monday</day>
                                     <day>Tuesday</day>
                                   </overtime>
                                  </employee>
                                  <employee name= "Jane Doe 13" gender="female" type="FT">
                                   <empnum>E4</empnum>
                                   <pnum>P5</pnum>
                                   <hours>80</hours>
                                   <status>active</status>
                                  </employee>
                                 </works></doc>;

declare
    %test:assertTrue
function testTransform:transform-1() {
    let $result := fn:transform(map {"stylesheet-node" : $testTransform:render, "source-node" : $testTransform:doc})
    return $result?output instance of node()
};

declare
    %test:assertTrue
function testTransform:transform-1a() {
    let $result := fn:transform(map {"stylesheet-node" : $testTransform:render, "source-node" : $testTransform:doc, "delivery-format" : "serialized"})
    return $result?output instance of xs:string
};

declare
    %test:assertTrue
function testTransform:transform-1b() {
    let $result := fn:transform(map {"stylesheet-node" : $testTransform:render, "source-node" : $testTransform:doc, "serialized" : "serialized"})
    return $result?output instance of node()
};

declare variable $testTransform:xsl-transform-2  := "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='2.0'
                                                                 xmlns:app='http://www.example.com'>
   <xsl:template name='app:main'>
      <xsl:for-each select='//section'>
         <xsl:result-document href='section{position()}.html'>
            <xsl:value-of select='.' />
         </xsl:result-document>
      </xsl:for-each>
   </xsl:template>
</xsl:stylesheet>";

declare variable $testTransform:xml-transform-2 :=
"<doc>
  <section>sect1</section>
  <section>sect2</section>
</doc>";

declare variable $testTransform:result-transform-2 := element {xs:QName('html')} {
    element {xs:QName('body')} {
        for $x in fn:transform(
                    map{"xslt-version"    : 2.0,
                        "stylesheet-text" : $testTransform:xsl-transform-2,
                        "base-output-uri" : "http://www.w3.org/fots/fn/transform/output-doc.xml",
                        "initial-template": fn:QName('http://www.example.com','main'),
                        "source-node"     : fn:parse-xml($testTransform:xml-transform-2)})?*
        return $x }
    };

declare
    %test:assertTrue
function testTransform:transform-2() {
    let $result := fn:contains(string($testTransform:result-transform-2//body),'sect2')
    return $result
};

declare
    %test:assertFalse
function testTransform:transform-2b() {
    let $result := fn:contains(string($testTransform:result-transform-2//body),'sect3')
    return $result
};

declare variable $testTransform:doc-3 := <xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>
    <xsl:param name='v'/>
    <xsl:template match='/'>
        <v><xsl:value-of select='$v'/></v>
    </xsl:template>
</xsl:stylesheet>;

declare
    %test:assertEquals("<v>2</v>")
function testTransform:transform-3() {
    let $in := parse-xml("<dummy/>")
    let $style := $testTransform:doc-3
    let $result := ( fn:transform(map{"source-node":$in, "stylesheet-node":$style, "stylesheet-params": map { QName("","v"): "2" } } ) )?output
    return $result
};

declare
    %test:assertEquals("<v>2</v>")
function testTransform:transform-3a() {
    let $in := parse-xml("<dummy/>")
    let $style := $testTransform:doc-3
    let $result := ( fn:transform(map{"source-node":$in, "stylesheet-node":$style, "stylesheet-params": map { QName("","v"): "2" } } ) )("output")
    return $result
};
