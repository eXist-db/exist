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

declare variable $testTransform:transform-82e-xsl := document {
<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>
    <xsl:variable name='v' select="."/>
    <xsl:template match='.'>
        <out root-is-doc="{$v instance of document-node()}" this-is-doc="{. instance of document-node()}" xslt-version="{util:system-property('xsl:version')}">
            <xsl:value-of select='name($v)'/>
        </out>
    </xsl:template>
</xsl:stylesheet> };

declare
    %test:assertEquals(2)
function testTransform:transform-82e() {
    let $xsl := $testTransform:transform-82e-xsl
    let $in := parse-xml("<dummy/>")
    let $result := fn:transform(map{
              "source-node": $in/*,
              "global-context-item": $in/*,
              "stylesheet-node": $xsl,
              "xslt-version": 2.0
           })
    return $result?output
};
