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

declare variable $testTransform:books-4 := xs:string("<books>
    <book>
        <title>XSLT Programmer?s Reference</title>
        <author>Michael H. Kay</author>
    </book>
    <book>
        <title>XSLT</title>
        <author>Doug Tidwell</author>
        <author>Simon St. Laurent</author>
        <author>Robert Romano</author>
    </book>
</books>");

declare variable $testTransform:style-4 := <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html"/>
    <xsl:template match="/">
        <html>
            <body>
                <div>
                    <xsl:for-each select="books/book">
                        <b><xsl:value-of select="title"/></b>: <xsl:value-of select="author"
                        /><br/>
                    </xsl:for-each>
                </div>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>;

declare variable $testTransform:style-4s :=
xs:string('<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:output method="html"/>
    <xsl:template match="/">
        <html>
            <body>
                <div>
                    <xsl:for-each select="books/book">
                        <b><xsl:value-of select="title"/></b>: <xsl:value-of select="author"
                        /><br/>
                    </xsl:for-each>
                </div>
            </body>
        </html>
    </xsl:template>
</xsl:stylesheet>');

declare
    %test:assertTrue
function testTransform:transform-4() {
    let $in := $testTransform:books-4,
    $style := $testTransform:style-4
    let $trn := transform(map{"source-node":fn:parse-xml($in), "stylesheet-node":$style, "serialization-params": map{"indent": true()} } )("output")
    return $trn//b = 'XSLT'
};

declare
    %test:assertTrue
function testTransform:transform-5() {
    let $in := $testTransform:books-4,
    $style := $testTransform:style-4s
    let $trn := transform(map{"source-node":fn:parse-xml($in), "stylesheet-text":$style, "serialization-params": map{"indent": true()} } )("output")
    return $trn//b = 'XSLT'
};
