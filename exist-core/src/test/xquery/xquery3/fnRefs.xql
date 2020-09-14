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

module namespace fnRefs="http://exist-db.org/xquery/test/function_reference";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xpf = "http://www.w3.org/2005/xpath-functions";

declare
    %test:assertEquals("/db")
function fnRefs:castFunctionReference() {
    let $as-uri := xs:anyURI(?)
    return
        $as-uri("/db")
};

declare
    %test:assertError("err:XPST0017")
function fnRefs:concat-arity0() {
    fn:concat#0
};

declare
    %test:assertError("err:XPST0017")
function fnRefs:concat-arity1() {
    fn:concat#1
};

declare
    %test:assertEquals(1)
function fnRefs:concat-arity2() {
    count(
        fn:concat#2
    )
};
