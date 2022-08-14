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

declare variable $testTransform:transform-87-xsl :=
"<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'
             version='1.0'>
             <xsl:template match='/'>
               <xsl:variable name='in' select='.'/>
               <xsl:for-each select='1 to 3'>
                 <xsl:result-document href='output{.}' omit-xml-declaration='yes'>
                    <xsl:sequence select='number($in/a/b) + .'/>
                 </xsl:result-document>
               </xsl:for-each>
             </xsl:template>
         </xsl:stylesheet>";

declare
    %test:assertEquals("96")
function testTransform:transform-87() {
    let $xsl := $testTransform:transform-87-xsl
    let $trans-result := fn:transform(map{"stylesheet-text" : $xsl,
                                                                   "delivery-format" : "raw",
                                                                   "base-output-uri" : "http://example.com/",
                                                                   "source-node"     : parse-xml('<a><b>89</b></a>'),
                                                                   "post-process"    : function($uri, $val) { $val + 5 }
                                                                  })
    return $trans-result("http://example.com/output2")
};
