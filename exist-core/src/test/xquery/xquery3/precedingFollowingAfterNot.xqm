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

(:~
 : Regression test for https://github.com/eXist-db/exist/issues/3684.
 :
 : A FLWOR whose `for` clause filtered with a `not(@attr)` predicate, then
 : traversed the `preceding::` or `following::` axis, returned wrong results
 : when the source was a persistent (stored) document but worked correctly
 : in-memory. The bug no longer reproduces on develop; this XQSuite pins
 : the reproducer so the in-mem / persistent asymmetry cannot return
 : silently.
 :
 : Adapted verbatim from the report by @joewiz (#3684, 2021).
 :)
module namespace t="http://exist-db.org/xquery/test/preceding-following-after-not";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $t:XML :=
    document {
        <root>
            <foo/>
            <bar>
                <bar/>
            </bar>
            <foo/>
        </root>
    };

declare
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", "test-3684"),
    xmldb:store("/db/test-3684", "test.xml", $t:XML)
};

declare
    %test:tearDown
function t:tearDown() {
    xmldb:remove("/db/test-3684")
};

declare
    %test:assertEquals("true", "true")
function t:preceding-with-predicate-db() {
    for $bar in doc("/db/test-3684/test.xml")//bar[not(@baz)]
    return
        exists($bar/preceding::foo)
};

declare
    %test:assertEquals("true", "true")
function t:preceding-without-predicate-db() {
    for $bar in doc("/db/test-3684/test.xml")//bar
    return
        exists($bar/preceding::foo)
};

declare
    %test:assertEquals("true", "true")
function t:preceding-with-predicate-in-mem() {
    for $bar in $t:XML//bar[not(@baz)]
    return
        exists($bar/preceding::foo)
};

declare
    %test:assertEquals("true", "true")
function t:preceding-without-predicate-in-mem() {
    for $bar in $t:XML//bar
    return
        exists($bar/preceding::foo)
};

declare
    %test:assertEquals("true", "true")
function t:following-with-predicate-db() {
    for $bar in doc("/db/test-3684/test.xml")//bar[not(@baz)]
    return
        exists($bar/following::foo)
};

declare
    %test:assertEquals("true", "true")
function t:following-without-predicate-db() {
    for $bar in doc("/db/test-3684/test.xml")//bar
    return
        exists($bar/following::foo)
};

declare
    %test:assertEquals("true", "true")
function t:following-with-predicate-in-mem() {
    for $bar in $t:XML//bar[not(@baz)]
    return
        exists($bar/following::foo)
};

declare
    %test:assertEquals("true", "true")
function t:following-without-predicate-in-mem() {
    for $bar in $t:XML//bar
    return
        exists($bar/following::foo)
};
