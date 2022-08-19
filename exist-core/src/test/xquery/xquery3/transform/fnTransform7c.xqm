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

declare variable $testTransform:transform-7c-xsl := "<out xmlns:xsl='http://www.w3.org/1999/XSL/Transform' xsl:version='2.0'>
                          <xsl:value-of select='.' />
                         </out>";

declare
    %test:assertEquals('<out>this</out>')
function testTransform:transform-7c() {
    let $style := $testTransform:transform-7c-xsl
    let $result := fn:transform(map{"stylesheet-node":parse-xml($style)/*, "source-node":parse-xml("<doc>this</doc>") })
   return $result?output
};
