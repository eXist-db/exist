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

module namespace npt="http://exist-db.org/test/nested-positional-predicate";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $npt:DATA :=
    document {
        <xml>
            <a>
                <b>B1</b>
            </a>
            <a>
                <b>B2</b>
                <c>correct</c>
            </a>
            <a>
                <b>B3</b>
                <c>wrong</c>
            </a>
        </xml>
    };

declare
    %test:setUp
function npt:setup() {
    xmldb:create-collection("/db", "test"),
    xmldb:store("/db/test", "test.xml", $npt:DATA)
};

declare
    %test:tearDown
function npt:cleanup() {
    xmldb:remove("/db/test")
};

declare
    %test:assertEquals("<result><c>correct</c><c>wrong</c></result>")
function npt:in-memory() {
    <result>{$npt:DATA//c[../preceding-sibling::a]}</result>
};

declare
    %test:assertEquals("<result><c>correct</c><c>wrong</c></result>")
function npt:in-database() {
    <result>{doc("/db/test/test.xml")//c[../preceding-sibling::a]}</result>
};


declare
    %test:assertEquals("<result><c>correct</c><c>wrong</c></result>")
function npt:in-memory-predicate() {
    <result>{$npt:DATA//c[../preceding-sibling::a[1]]}</result>
};

declare
    %test:assertEquals("<result><c>correct</c><c>wrong</c></result>")
function npt:in-database-predicate() {
    <result>{doc("/db/test/test.xml")//c[../preceding-sibling::a[1]]}</result>
};

declare
    %test:assertEquals("<result><c>correct</c><c>wrong</c></result>")
function npt:in-memory-position() {
    <result>{$npt:DATA//c[../preceding-sibling::a[position() eq 1]]}</result>
};

declare
    %test:assertEquals("<result><c>correct</c><c>wrong</c></result>")
function npt:in-database-position() {
    <result>{doc("/db/test/test.xml")//c[../preceding-sibling::a[position() eq 1]]}</result>
};

declare
    %test:assertEquals("<result><c>correct</c></result>")
function npt:in-memory-predicate-and-path() {
    <result>{$npt:DATA//c[../preceding-sibling::a[1]/b = 'B1']}</result>
};

declare
    %test:assertEquals("<result><c>correct</c></result>")
function npt:in-database-predicate-and-path() {
    <result>{doc("/db/test/test.xml")//c[../preceding-sibling::a[1]/b = 'B1']}</result>
};

declare
    %test:assertEquals("<result><c>correct</c></result>")
function npt:in-memory-position-and-path() {
    <result>{$npt:DATA//c[../preceding-sibling::a[position() eq 1]/b = 'B1']}</result>
};

declare
    %test:assertEquals("<result><c>correct</c></result>")
function npt:in-database-position-and-path() {
    <result>{doc("/db/test/test.xml")//c[../preceding-sibling::a[position() eq 1]/b = 'B1']}</result>
};
