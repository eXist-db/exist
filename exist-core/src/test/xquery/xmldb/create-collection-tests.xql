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

module namespace t="http://exist-db.org/testsuite/create-collection";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $t:collection-name := "create-collection-test";
declare variable $t:collection := "/db/" || $t:collection-name;

declare
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", $t:collection-name)
};

declare
    %test:tearDown
function t:cleanup() {
    xmldb:remove($t:collection)
};

declare
    %test:assertEquals("/db/create-collection-test/child")
function t:create-single-segment() {
    xmldb:create-collection($t:collection, "child")
};

declare
    %test:assertEquals("/db/create-collection-test/a/b/c/d")
function t:create-nested-path() {
    xmldb:create-collection($t:collection, "a/b/c/d")
};

declare
    %test:assertTrue
function t:create-nested-path-creates-intermediates() {
    let $created := xmldb:create-collection($t:collection, "p/q/r")
    return
        xmldb:collection-available($t:collection || "/p")
        and xmldb:collection-available($t:collection || "/p/q")
        and xmldb:collection-available($t:collection || "/p/q/r")
        and $created eq $t:collection || "/p/q/r"
};

declare
    %test:assertEquals("/db/create-collection-test/x/y")
function t:create-nested-path-twice() {
    let $_ := xmldb:create-collection($t:collection, "x/y")
    return xmldb:create-collection($t:collection, "x/y")
};
