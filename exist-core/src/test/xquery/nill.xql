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
xquery version "3.0";

(:~ Additional tests for the fn:nill function :)
module namespace nill="http://exist-db.org/xquery/test/nill";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";


declare 
    %test:assertEquals("false")
function nill:nilfalse() {
    let $a := <a xsi:nil="false"></a>
    return
    nilled($a)
};

declare
%test:assertEquals("false")
    function nill:niltrue() {
    let $a := <a xsi:nil="true"></a>
    return
    nilled($a)
};

declare
%test:assertEquals("true")
function nill:empty() {
    let $a := ()
    return
      empty( nilled($a) )
};

declare
%test:assertEquals("true")
function nill:noelementnode() {
    let $a := <a b="c"/>
    
    return
    empty( nilled($a/@b) )
};