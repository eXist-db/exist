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
 : XQSuite regression test for GitHub #4110: case insensitive range index on
 : attributes (case="no") should match regardless of case.
 : Expected: [@lemma="aaa"], [@lemma="AaA"], [@lemma="AAA"] all return 3 items.
 :
 : @see https://github.com/eXist-db/exist/issues/4110
 :)
module namespace i4110 = "http://exist-db.org/xquery/range/issue-4110/test";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $i4110:DATA := document {
    <debug>
        <a lemma="Aaa" lemma-strict="OnlyThis"/>
        <a lemma="AAA" lemma-strict="OnlyThis"/>
        <a lemma="aaa" lemma-strict="OnlyThis"/>
    </debug>
};

declare variable $i4110:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <range>
                <create qname="@lemma" type="xs:string" case="no"/>
                <create qname="@lemma-strict" type="xs:string" case="yes"/>
            </range>
        </index>
    </collection>;

declare variable $i4110:COLLECTION := "i4110";
declare variable $i4110:COLLECTION_PATH := "/db/" || $i4110:COLLECTION;

declare
    %test:setUp
function i4110:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i4110:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i4110:COLLECTION),
      xmldb:store("/db/" || $i4110:COLLECTION, "test.xml", $i4110:DATA),
      xmldb:store("/db/system/config/db/" || $i4110:COLLECTION, "collection.xconf", $i4110:XCONF),
      xmldb:reindex("/db/" || $i4110:COLLECTION) )
};

declare
    %test:tearDown
function i4110:tearDown() {
    xmldb:remove($i4110:COLLECTION_PATH),
    xmldb:remove("/db/system/config/db/" || $i4110:COLLECTION)
};

(: #4110: Case insensitive @lemma – lowercase query returns all 3. :)
declare
    %test:assertEquals(3)
function i4110:attr-case-insensitive-lower() {
    count(collection($i4110:COLLECTION_PATH)//a[@lemma = "aaa"])
};

(: #4110: Case insensitive @lemma – mixed case query returns all 3. :)
declare
    %test:assertEquals(3)
function i4110:attr-case-insensitive-mixed() {
    count(collection($i4110:COLLECTION_PATH)//a[@lemma = "AaA"])
};

(: #4110: Case insensitive @lemma – uppercase query returns all 3. :)
declare
    %test:assertEquals(3)
function i4110:attr-case-insensitive-upper() {
    count(collection($i4110:COLLECTION_PATH)//a[@lemma = "AAA"])
};

(: Non-matching value returns 0 (sanity check). :)
declare
    %test:assertEquals(0)
function i4110:attr-case-insensitive-no-match() {
    count(collection($i4110:COLLECTION_PATH)//a[@lemma = "different"])
};

(: case="yes" (default): exact case match required; lowercase returns 0. :)
declare
    %test:assertEquals(0)
function i4110:attr-case-sensitive-lower-no-match() {
    count(collection($i4110:COLLECTION_PATH)//a[@lemma-strict = "onlythis"])
};

(: case="yes": exact case match returns 3. :)
declare
    %test:assertEquals(3)
function i4110:attr-case-sensitive-exact-match() {
    count(collection($i4110:COLLECTION_PATH)//a[@lemma-strict = "OnlyThis"])
};
