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
 : Document-level security for the scope functions: ft:query-scope and ft:search-scope must never
 : return nodes (or hits) from documents the caller cannot read. They resolve their scope through
 : broker.allDocs(...) and materialize hits as persistent nodes through the broker, both of which
 : enforce read permissions -- the same guarantee any collection()//x query honors. This pins that
 : guarantee (the DLS layer existdb-openapi's field-permission model relies on), mirroring the
 : visibility checks ft-search-binary.xqm already makes for the legacy ft:search.
 :)
module namespace dls = "http://exist-db.org/xquery/lucene/test/scope-dls";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $dls:COLLECTION := "/db/lucene-test-scope-dls";
declare variable $dls:CONFIG := "/db/system/config/db/lucene-test-scope-dls";

declare variable $dls:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="para"><field name="content" expression="."/></text>
            </lucene>
        </index>
    </collection>;

(: "widget" is in BOTH docs; "public"/"secret" each in exactly one :)
declare variable $dls:PUBLIC := <article><para>public widget content, visible to everyone</para></article>;
declare variable $dls:SECRET := <article><para>secret widget content, for admin only</para></article>;

declare
    %test:setUp
function dls:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", "lucene-test-scope-dls")
    let $col := xmldb:create-collection("/db", "lucene-test-scope-dls")
    return (
        xmldb:store($conf, "collection.xconf", $dls:XCONF),
        xmldb:store($col, "public.xml", $dls:PUBLIC),
        xmldb:store($col, "secret.xml", $dls:SECRET),
        sm:chmod(xs:anyURI($dls:COLLECTION || "/public.xml"), "rw-rw-rw-"),
        sm:chmod(xs:anyURI($dls:COLLECTION || "/secret.xml"), "rw-------"),
        xmldb:reindex($col)
    )
};

declare
    %test:tearDown
function dls:tearDown() {
    if (xmldb:collection-available($dls:COLLECTION)) then xmldb:remove($dls:COLLECTION) else (),
    if (xmldb:collection-available($dls:CONFIG)) then xmldb:remove($dls:CONFIG) else ()
};

(: ---- ft:query-scope honors document read permissions ---- :)

(: a term indexed only in secret.xml is not reachable by a guest who cannot read that document :)
declare
    %test:assertEquals(0)
function dls:query-scope-guest-cannot-see-secret() {
    system:as-user("guest", "guest", count(ft:query-scope($dls:COLLECTION, "content:(secret)")))
};

(: admin (the owner) does see it :)
declare
    %test:assertEquals(1)
function dls:query-scope-admin-sees-secret() {
    system:as-user("admin", "", count(ft:query-scope($dls:COLLECTION, "content:(secret)")))
};

(: the public document remains visible to the guest :)
declare
    %test:assertEquals(1)
function dls:query-scope-guest-sees-public() {
    system:as-user("guest", "guest", count(ft:query-scope($dls:COLLECTION, "content:(public)")))
};

(: a term in BOTH documents: the guest gets only the readable one, admin gets both :)
declare
    %test:assertEquals(1)
function dls:query-scope-guest-widget-only-public() {
    system:as-user("guest", "guest", count(ft:query-scope($dls:COLLECTION, "content:(widget)")))
};

declare
    %test:assertEquals(2)
function dls:query-scope-admin-widget-both() {
    system:as-user("admin", "", count(ft:query-scope($dls:COLLECTION, "content:(widget)")))
};

(: ---- ft:search-scope honors document read permissions (total and hits) ---- :)

(: total reflects only the documents the caller can read :)
declare
    %test:assertEquals(1)
function dls:search-scope-guest-total-excludes-secret() {
    system:as-user("guest", "guest", ft:search-scope($dls:COLLECTION, "content:(widget)")?total)
};

declare
    %test:assertEquals(2)
function dls:search-scope-admin-total-both() {
    system:as-user("admin", "", ft:search-scope($dls:COLLECTION, "content:(widget)")?total)
};

(: the one guest-visible hit is the public document, never the secret one :)
declare
    %test:assertTrue
function dls:search-scope-guest-hit-is-public() {
    system:as-user("guest", "guest",
        let $hits := ft:search-scope($dls:COLLECTION, "content:(widget)")?hits
        return array:size($hits) = 1 and ends-with($hits(1)?uri, "/public.xml"))
};
