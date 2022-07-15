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

declare variable $testTransform:xsl-16 := "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
                                     xmlns:my='http://www.w3.org/fots/fn/transform/myfunctions' version='3.0'>
                                         <xsl:function name='my:user-function'>
                                             <xsl:param name='param1'/>
                                             <xsl:param name='param2'/>
                                             <this>
                                             <xsl:value-of select='$param1'/><xsl:value-of select='$param2'/>
                                             </this>
                                         </xsl:function>
                                         <xsl:template name='main'>
                                             <out>
                                                 <xsl:value-of select='.' />
                                             </out>
                                         </xsl:template>
                                     </xsl:stylesheet>";

declare
    %test:assertError("err:FOXT0002")
function testTransform:transform-err-16() {
    let $xsl := $testTransform:xsl-16
    let $result := fn:transform(map{"stylesheet-text":$xsl, "source-node":parse-xml("<doc>this</doc>"),
            "initial-function": fn:QName('http://www.w3.org/fots/fn/transform/myfunctions','user-function')
            })
    return $result?output
};
