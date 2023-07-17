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

module namespace t = "http://exist-db.org/xquery/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace array = "http://www.w3.org/2005/xpath-functions/array";
import module namespace util = "http://exist-db.org/xquery/util";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";


declare %private variable $t:test-collection-path := "/db/test-multi-collection-search";

declare %private variable $t:test-data :=
        document {
            <root>
                <foo bar="baz"/>
            </root>
        };

declare %private variable $t:old-range-xconf :=
        document {
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <create qname="@bar" type="xs:string"/>
                </index>
            </collection>
        };

declare %private variable $t:new-range-xconf :=
        document {
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <range>
                        <create qname="@bar" type="xs:string"/>
                    </range>
                </index>
            </collection>
        };

(:~
 : 3 Collections, one for each type of index that we want to involve in the test.
 :)
declare %private variable $t:test-collections :=
        array {
            map { "collection-name": "no-range-index",  "data": $t:test-data },
            map { "collection-name": "old-range-index", "data": $t:test-data, "configuration": $t:old-range-xconf },
            map { "collection-name": "new-range-index", "data": $t:test-data, "configuration": $t:new-range-xconf }
        };


declare
    %test:setUp
function t:set-up() {
    (: Create and configure the test collections :)
    array:for-each($t:test-collections, function($test-collection) {
        let $collection-path := $t:test-collection-path || "/" || $test-collection?collection-name
        let $config-collection-path := "/db/system/config" || $collection-path
        return
            (
                if (fn:exists($test-collection?configuration))
                then
                    (
                        t:mkcol($config-collection-path),
                        xmldb:store($config-collection-path, "collection.xconf", $test-collection?configuration)
                    )
                else (),
                t:mkcol($collection-path),
                xmldb:store($collection-path, "test.xml", $test-collection?data)
            )
    }),

    (: Reindex the test collections :)
    xmldb:reindex($t:test-collection-path)
};

declare
    %test:tearDown
function t:tear-down() {
    xmldb:remove($t:test-collection-path),
    xmldb:remove("/db/system/config" || $t:test-collection-path)
};

declare
    %test:assertEquals(3)
function t:structural-index-all-collections() {
    count(collection($t:test-collection-path)//foo/@bar)
};

declare
    %test:assertEquals(3)
function t:eq-all-collections() {
    count(collection($t:test-collection-path)//foo[@bar eq "baz"])
};

declare
    %test:assertEquals(3)
function t:matches-all-collections() {
    count(collection($t:test-collection-path)//foo[matches(@bar, "^b")])
};

declare
    %test:assertEquals(3)
function t:contains-all-collections() {
    count(collection($t:test-collection-path)//foo[contains(@bar, "b")])
};

declare
    %test:assertEquals(3)
function t:starts-with-all-collections() {
    count(collection($t:test-collection-path)//foo[starts-with(@bar, "b")])
};

declare
    %test:assertEquals(1)
function t:eq-new-range-index-only() {
    count(collection($t:test-collection-path || "/new-range-index")//foo[@bar eq "baz"])
};

declare
    %test:assertEquals(1)
function t:eq-old-range-index-only() {
    count(collection($t:test-collection-path || "/old-range-index")//foo[@bar eq "baz"])
};

declare
    %test:assertEquals(1)
function t:eq-no-range-index-only() {
    count(collection($t:test-collection-path || "/no-range-index")//foo[@bar eq "baz"])
};

declare
    %test:assertEquals(1)
function t:matches-new-range-index-only() {
    count(collection($t:test-collection-path || "/new-range-index")//foo[matches(@bar, "^b")])
};

declare
    %test:assertEquals(1)
function t:matches-old-range-index-only() {
    count(collection($t:test-collection-path || "/old-range-index")//foo[matches(@bar, "^b")])
};

declare
    %test:assertEquals(1)
function t:matches-no-range-index-only() {
    count(collection($t:test-collection-path || "/no-range-index")//foo[matches(@bar, "^b")])
};

declare
    %test:assertEquals(1)
function t:contains-new-range-index-only() {
    count(collection($t:test-collection-path || "/new-range-index")//foo[contains(@bar, "b")])
};

declare
    %test:assertEquals(1)
function t:contains-old-range-index-only() {
    count(collection($t:test-collection-path || "/old-range-index")//foo[contains(@bar, "b")])
};

declare
    %test:assertEquals(1)
function t:contains-no-range-index-only() {
    count(collection($t:test-collection-path || "/no-range-index")//foo[contains(@bar, "b")])
};

declare
    %test:assertEquals(1)
function t:starts-with-new-range-index-only() {
    count(collection($t:test-collection-path || "/new-range-index")//foo[starts-with(@bar, "b")])
};

declare
    %test:assertEquals(1)
function t:starts-with-old-range-index-only() {
    count(collection($t:test-collection-path || "/old-range-index")//foo[starts-with(@bar, "b")])
};

declare
    %test:assertEquals(1)
function t:starts-with-no-range-index-only() {
    count(collection($t:test-collection-path || "/no-range-index")//foo[starts-with(@bar, "b")])
};

declare
    %test:assertTrue
function t:eq-all-collections-has-hit-from-new-range-index() {
    some $hit in collection($t:test-collection-path)//foo[@bar eq "baz"] satisfies
        util:collection-name($hit) eq $t:test-collection-path || "/new-range-index"
};

declare
    %test:assertTrue
function t:eq-all-collections-has-hit-from-old-range-index() {
    some $hit in collection($t:test-collection-path)//foo[@bar eq "baz"] satisfies
        util:collection-name($hit) eq $t:test-collection-path || "/old-range-index"
};

declare
    %test:assertTrue
function t:eq-all-collections-has-hit-from-no-range-index() {
    some $hit in collection($t:test-collection-path)//foo[@bar eq "baz"] satisfies
        util:collection-name($hit) eq $t:test-collection-path || "/no-range-index"
};

(:~
 : Creates a collection path hierarchy.
 :
 : @param $path the path of the collection hierarchy to create.
 : @return the path as sent in $path
 :)
declare
    %private
function t:mkcol($path as xs:string) as xs:string {
    let $recursive-mkcol-fn := function($base-collection-path as xs:string, $relative-path as xs:string?, $self) {
        if (fn:not(xmldb:collection-available($base-collection-path)))
        then
            fn:error("t:mkcol - $base-collection-path does not already exist: " || $base-collection-path)
        else
            let $path-parts := fn:tokenize($relative-path, "/")[fn:string-length(.) gt 0]
            return
                if (fn:empty($path-parts))
                then
                    $base-collection-path
                else
                    let $new-base-collection-path := xmldb:create-collection($base-collection-path, fn:head($path-parts))
                    let $new-relative-path := fn:string-join(fn:tail($path-parts), "/")
                    return
                        $self($new-base-collection-path, $new-relative-path, $self)
    }
    let $_ := $recursive-mkcol-fn("/db", fn:substring-after($path, "/db/"), $recursive-mkcol-fn)
    return
        $path
};