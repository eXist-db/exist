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

declare variable $testTransform:transform-52-xsl := document {
    <xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='3.0'>
                    <xsl:param name='static-param' static='yes' select='&quot;success&quot;'/>
                    <xsl:param name='alt-param' static='yes' select='upper-case($static-param)'/>
                    <xsl:template match='/'>
                        <out><xsl:value-of select='$static-param'/><xsl:value-of select='$alt-param'/></out>
                    </xsl:template>
                </xsl:stylesheet> };

declare
    %test:assertEquals("\'HelloWorld\'")
function testTransform:transform-52() {
    let $xsl := $testTransform:transform-52-xsl
    let $result := fn:transform(map{"stylesheet-node":$xsl, "source-node":parse-xml("<doc>this</doc>"),
                "static-params":map{QName('','static-param'):"Hello", QName('','alt-param'):"World"}
                })
    return $result?output//out
};

