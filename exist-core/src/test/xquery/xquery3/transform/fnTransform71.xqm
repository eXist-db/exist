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

declare variable $testTransform:transform-71-xsl := document {
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
            xmlns:xs='http://www.w3.org/2001/XMLSchema'
            xmlns:chrono='http://chronology.com/' version='2.0'>
            <xsl:import-schema>
              <xs:schema targetNamespace='http://chronology.com/'>
                <xs:simpleType name='c4'>
                  <xs:restriction base='xs:string'>
                    <xs:pattern value='....'/>
                  </xs:restriction>
                </xs:simpleType>
              </xs:schema>
            </xsl:import-schema>
            <xsl:template name='main'>
              <out><xsl:value-of select="chrono:c4('abcd')"/></out>
            </xsl:template>
        </xsl:stylesheet> };

declare
    %test:assertError("XTSE1650")
function testTransform:transform-71() {
    let $xsl := $testTransform:transform-71-xsl
    let $result := fn:transform(map{
    "stylesheet-node":$xsl,
                "source-node": parse-xml($xsl),
                "initial-template": fn:QName('','main'),
                    "delivery-format" : "serialized",
                    "requested-properties" : map{fn:QName('http://www.w3.org/1999/XSL/Transform','is-schema-aware'):false()}
                    })
    return $result("output")
};
