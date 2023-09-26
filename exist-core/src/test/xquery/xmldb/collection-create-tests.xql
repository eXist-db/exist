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
declare variable $t:path-collection := "/path/to/new-collection";

declare
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", $t:parent-collection-name)
};

declare
    %test:tearDown
function t:cleanup() {
    xmldb:remove($t:parent-collection)
};

declare
    %test:assertEquals("/db/path/to/new-collection")
function t:fnDocAvailableOnHiddenResource() {
    let $collection := xmldb:create-collection($t:path-collection)
    return (
         $collection
    )
};
