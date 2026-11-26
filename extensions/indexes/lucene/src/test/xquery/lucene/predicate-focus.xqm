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
 : A let clause in a predicate that uses ft:query is evaluated per candidate, with the
 : candidate as its focus. See https://github.com/eXist-db/exist/issues/6832.
 :)
module namespace t="http://exist-db.org/xquery/test/predicate-focus-lucene";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $t:COLL := "/db/predicate-focus-lucene";
declare variable $t:CONF := "/db/system/config/db/predicate-focus-lucene";

declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="name"/>
            </lucene>
        </index>
    </collection>;

declare %test:setUp function t:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db", "predicate-focus-lucene"),
    xmldb:create-collection("/db/system/config/db", "predicate-focus-lucene"),
    xmldb:store($t:CONF, "collection.xconf", $t:xconf),
    xmldb:store($t:COLL, "a.xml", <article><name>alpha</name></article>),
    xmldb:store($t:COLL, "b.xml", <article><name>beta</name></article>),
    xmldb:store($t:COLL, "c.xml", <article><name>gamma</name></article>),
    xmldb:reindex($t:COLL)
};

declare %test:tearDown function t:teardown() {
    xmldb:remove($t:COLL),
    xmldb:remove($t:CONF)
};

declare
    %test:assertEquals(3)
function t:let-in-ft-query-predicate() {
    count(collection($t:COLL)//article[let $q := string(name) return ft:query(name, $q)])
};

declare
    %test:assertEquals(3)
function t:let-in-predicate-control() {
    count(collection($t:COLL)//article[let $q := string(name) return $q = name])
};
