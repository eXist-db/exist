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

module namespace t="http://exist-db.org/testsuite/collection-create";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $t:parent-collection-name := "/parent-collection";
declare variable $t:parent-collection := "/db" || $t:parent-collection-name;
declare variable $t:path-collection := $t:parent-collection || "/path/to/new-collection";
declare variable $t:path-collection-from-root := "/db/path/to/new-collection-from-root";
declare variable $t:wrong-path-collection := "/wrong/path-to-collection";

declare
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", $t:parent-collection-name)
};

declare
    %test:tearDown
function t:cleanup() {
    xmldb:remove($t:parent-collection),
    xmldb:remove($t:path-collection-from-root)
};

declare
    %test:assertEquals("/db/parent-collection/path/to/new-collection")
function t:fn-create-new-recursive-collection() {
    let $collection := xmldb:create-collection($t:path-collection)
    return $collection
};

declare
    %test:assertEquals("/db/path/to/new-collection-from-root")
function t:fn-create-new-recursive-collection-from-root() {
    let $collection := xmldb:create-collection($t:path-collection-from-root)
    return $collection
};

declare
    %test:assertError
function t:fn-create-new-recursive-collection-with-wrong-path() {
    xmldb:create-collection($t:wrong-path-collection)
};

