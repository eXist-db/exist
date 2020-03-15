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

module namespace luct="http://exist-db.org/xquery/lucene/test";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $luct:INDEX_DATA :=
    <doc>
        <field name="title">Lorem ipsum dolor</field>
    </doc>;

declare variable $luct:INDEX_DATA_SUBCOLL :=
    <doc>
        <field name="title">Only admin can read this</field>
    </doc>;
    
declare
    %test:setUp
function luct:setup() {
    xmldb:create-collection("/db", "lucenetest"),
    xmldb:store("/db/lucenetest", "test.txt", "Lorem ipsum", "text/text"),
    sm:chmod(xs:anyURI("/db/lucenetest/test.txt"), "rw-------"),
    ft:index("/db/lucenetest/test.txt", $luct:INDEX_DATA),
    
    xmldb:create-collection("/db/lucenetest", "sub"),
    sm:chmod(xs:anyURI("/db/lucenetest/sub"), "rwx------"),
    xmldb:store("/db/lucenetest/sub", "test.txt", "Lorem ipsum", "text/text"),
    sm:chmod(xs:anyURI("/db/lucenetest/sub/test.txt"), "rw-rw-rw-"),
    ft:index("/db/lucenetest/sub/test.txt", $luct:INDEX_DATA_SUBCOLL)
};

declare
    %test:tearDown
function luct:cleanup() {
    xmldb:remove("/db/lucenetest")
};

declare
    %test:assertEmpty
function luct:check-visibility-fail() {
    system:as-user("guest", "guest", 
        ft:search("/db/lucenetest/", "title:ipsum")/search/@uri/string()
    )
};

declare
    %test:assertEquals("/db/lucenetest/test.txt")
function luct:check-visibility-pass() {
    system:as-user("admin", "", 
        ft:search("/db/lucenetest/", "title:ipsum")/search/@uri/string()
    )
};

declare
    %test:assertEmpty
function luct:check-visibility-collection-fail() {
    system:as-user("guest", "guest", 
        ft:search("/db/lucenetest/sub/", "title:admin")/search/@uri/string()
    )
};

declare
    %test:assertEquals("/db/lucenetest/sub/test.txt")
function luct:check-visibility-collection-pass() {
    system:as-user("admin", "", 
        ft:search("/db/lucenetest/sub/", "title:admin")/search/@uri/string()
    )
};

declare
    %test:assertEquals("/db/lucenetest/test.txt")
function luct:check-leading-wildcard() {
    system:as-user("admin", "",
        ft:search("/db/lucenetest/", "title:*rem", (), <options><leading-wildcard>yes</leading-wildcard></options>)/search/@uri/string()
    )
};
