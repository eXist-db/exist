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
 : fn:collection() is stable within a query: a collection resolved earlier in the query may be reused, but
 : not after the query itself has written to the database, and not for another user.
 :)
module namespace fncr="http://exist-db.org/xquery/test/function_collection_reuse";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fncr:COLLECTION := "/db/fn-collection-reuse";

declare
    %test:setUp
function fncr:setup() {
    let $collection := xmldb:create-collection("/db", "fn-collection-reuse")
    let $_ := xmldb:store($collection, "public.xml", <doc n="public"/>)
    let $private := xmldb:store($collection, "private.xml", <doc n="private"/>)
    return sm:chmod(xs:anyURI($private), "rw-------")
};

declare
    %test:tearDown
function fncr:cleanup() {
    xmldb:remove($fncr:COLLECTION)
};

declare
    %test:assertTrue
function fncr:same-nodes-twice() {
    let $first := collection($fncr:COLLECTION)
    let $second := collection($fncr:COLLECTION)
    return count($first) eq count($second) and (every $i in 1 to count($first) satisfies $first[$i] is $second[$i])
};

declare
    %test:assertEquals(2, 3, 2)
function fncr:sees-the-query-s-own-writes() {
    let $before := count(collection($fncr:COLLECTION))
    let $stored := xmldb:store($fncr:COLLECTION, "added.xml", <doc n="added"/>)
    let $after-store := count(collection($fncr:COLLECTION))
    let $_ := xmldb:remove($fncr:COLLECTION, "added.xml")
    let $after-remove := count(collection($fncr:COLLECTION))
    return ($before, $after-store, $after-remove)
};

declare
    %test:assertEquals(2, 1, 2)
function fncr:depends-on-the-user() {
    let $as-admin := count(collection($fncr:COLLECTION))
    let $as-guest := system:as-user("guest", "guest", count(collection($fncr:COLLECTION)))
    let $as-admin-again := count(collection($fncr:COLLECTION))
    return ($as-admin, $as-guest, $as-admin-again)
};
