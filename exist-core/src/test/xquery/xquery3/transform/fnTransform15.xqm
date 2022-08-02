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

declare
    %test:assertEquals('<out><v>2</v><w>3</w><x>4</x><y>5</y></out>')
function testTransform:transform-15() {
    let $in := parse-xml("<dummy/>")/*,
                        $style := parse-xml("<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>
                        <xsl:param name='v'/>
                        <xsl:param name='w'/>
                        <xsl:param name='x'/>
                        <xsl:param name='y'/>
                        <xsl:template match='dummy'>
                        <out>
                            <v><xsl:value-of select='$v'/></v>
                            <w><xsl:value-of select='$w'/></w>
                            <x><xsl:value-of select='$x'/></x>
                            <y><xsl:value-of select='$y'/></y>
                            </out>
                        </xsl:template>
                    </xsl:stylesheet>")
    return (transform(map{"source-node":$in, "stylesheet-node":$style, "stylesheet-params": map { QName("","v"): "2", QName("","w"): "3", QName("","y"): "5", QName("","x"): "4" } } ) )("output")
};
