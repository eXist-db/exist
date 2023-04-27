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

module namespace tas="http://exist-db.org/xquery/test/analyze-string";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("")
    %test:assertError("err:FORX0003")
    %test:args(".?")
    %test:assertError("err:FORX0003")
    %test:args(".*")
    %test:assertError("err:FORX0003")
    %test:args("(.*)")
    %test:assertError("err:FORX0003")
function tas:empty-match-fails($p as xs:string) {
    analyze-string("",$p,"")
};

declare
    %test:assertEmpty
function tas:analyze-string-empty() {
    analyze-string((), "\w")//(fn:match|fn:non-match)
};

declare
    %test:assertEquals("a")
    %test:args("a", "[A-Z]", "")
    %test:assertEquals("a")
    %test:args("a1", "\d", "")
    %test:assertEquals("a")
function tas:analyze-string-non-matches($v as xs:string, $p as xs:string, $f as xs:string) {
    analyze-string($v, $p, $f)//fn:non-match/string()
};

declare
    %test:args("a1", "\d", "")
    %test:assertEquals("1")
    %test:args("a", "\w", "")
    %test:assertEquals("a")
    %test:args("a", "[A-Z]", "i")
    %test:assertEquals("a")
function tas:analyze-string-matches($v as xs:string, $p as xs:string, $f as xs:string) {
    analyze-string($v, $p, $f)//fn:match/string()
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
function tas:invalid-pattern($regex as xs:string) {
    analyze-string("",$regex,"")
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
function tas:invalid-flag($flag as xs:string) {
    analyze-string("",".+",$flag)
};
