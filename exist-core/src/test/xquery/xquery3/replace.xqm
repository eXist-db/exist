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
xquery version "4.0";

module namespace rt="http://exist-db.org/xquery/test/replace";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("")
    %test:assertEquals("")
    %test:args(".?")
    %test:assertEquals("")
    %test:args(".*")
    %test:assertEquals("")
    %test:args("(.*)")
    %test:assertEquals("")
function rt:empty-match-allowed($p as xs:string) {
    (: XQ4: empty-matching regex no longer raises FORX0003 :)
    replace("",$p,"")
};

declare
    %test:args("a", "[A-Z]", "")
    %test:assertEquals("a")
    %test:args("a1", "\d", "")
    %test:assertEquals("a")
    %test:args("a", "\w", "")
    %test:assertEquals("")
    %test:args("a", "[A-Z]", "")
    %test:assertEquals("a")
function rt:replace-string($v as xs:string, $p as xs:string, $r as xs:string) {
    replace($v, $p, $r)
};

declare
    %test:args("\")
    %test:assertError("err:FORX0002")
    %test:args("(")
    %test:assertError("err:FORX0002")
    %test:args("[")
    %test:assertError("err:FORX0002")
    %test:args("{")
    %test:assertError("err:FORX0002")
    %test:args("?")
    %test:assertError("err:FORX0002")
function rt:invalid-pattern($regex as xs:string) {
    replace("",$regex,"")
};

declare
    %test:args("1")
    %test:assertError("err:FORX0001")
    %test:args("?")
    %test:assertError("err:FORX0001")
    %test:args("[")
    %test:assertError("err:FORX0001")
    %test:args("p")
    %test:assertError("err:FORX0001")
function rt:invalid-flag($flag as xs:string) {
    replace("",".+","", $flag)
};

(: XQ4: function replacement :)
declare
    %test:assertEquals("C")
function rt:function-replacement-basic() {
    replace("c", "c", function($k, $g) { upper-case($k) })
};

declare
    %test:assertEquals("")
function rt:function-replacement-empty() {
    replace("b", "b", function($k, $g) { })
};

declare
    %test:assertEquals("ddee")
function rt:function-replacement-duplicate() {
    replace("de", ".", function($k, $g) { $k || $k })
};

(: XQ4: empty replacement arg :)
declare
    %test:assertEquals("")
function rt:empty-replacement-arg() {
    replace("abc", "abc", ())
};
