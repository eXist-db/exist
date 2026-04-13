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

module namespace analyze="http://exist-db.org/xquery/lucene/test/analyzers";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $analyze:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene diacritics="no">
                <parser class="org.exist.indexing.lucene.StandardQueryParserWrapper"/>
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

(: Flat collection for isolation; no shared /db/lucene-test parent. :)
declare variable $analyze:COLLECTION_PATH := "/db/lucene-test-analyzers";
declare variable $analyze:CONFIG_PATH := "/db/system/config/db/lucene-test-analyzers";

declare variable $analyze:XCONF2 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene diacritics="yes">
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function analyze:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"),
        xmldb:create-collection("/db", "lucene-test-analyzers"), xmldb:create-collection("/db/system/config/db", "lucene-test-analyzers"))
    let $testCol1 := xmldb:create-collection($analyze:COLLECTION_PATH, "test1")
    let $testCol2 := xmldb:create-collection($analyze:COLLECTION_PATH, "test2")
    let $confCol1 := xmldb:create-collection($analyze:CONFIG_PATH, "test1")
    let $confCol2 := xmldb:create-collection($analyze:CONFIG_PATH, "test2")
    return (
        xmldb:store($confCol1, "collection.xconf", $analyze:XCONF1),
        xmldb:store($testCol1, "test.xml",
            <test>
                <p>Rüsselsheim</p>
                <p>Russelsheim</p>
                <p>Māori</p>
                <p>Maori</p>
                <!--Syriac, eastern, western and no vowels. -->
                <p>ܦܘܼܪܫܵܢܵܐ</p>
                <p>ܦܘܽܪܫܳܢܳܐ</p>
                <p>ܦܘܪܫܢܐ</p>
                <!-- Hebrew with and without vowels -->
                <p>פָּעַל</p>
                <p>פעל</p>
                <!-- Arabic with and without vowels -->
                <p>بَردَيصان</p>
                <p>برديصان</p>
                <!-- Pinyin tone marks (complex) -->
                <p>nǚ</p>
                <p>nü</p>
                <p>nu</p>
                <!-- Pinyin tone marks (simple) -->
                <p>zìyóu</p>
                <p>ziyou</p>
            </test>
        ),
        xmldb:store($confCol2, "collection.xconf", $analyze:XCONF2),
        xmldb:store($testCol2, "test.xml",
            <test>
                <p>Rüsselsheim</p>
                <p>Russelsheim</p>
                <p>Māori</p>
                <p>Maori</p>
                <!--Syriac, eastern, western and no vowels. -->
                <p>ܦܘܼܪܫܵܢܵܐ</p>
                <p>ܦܘܽܪܫܳܢܳܐ</p>
                <p>ܦܘܪܫܢܐ</p>
                <!-- Hebrew with and without vowels-->
                <p>פָּעַל</p>
                <p>פעל</p>
                <!-- Arabic with and without vowels -->
                <p>بَردَيصان</p>
                <p>برديصان</p>
                <!-- Pinyin tone marks (complex) -->
                <p>nǚ</p>
                <p>nü</p>
                <p>nu</p>
                <!-- Pinyin tone marks (simple) -->
                <p>zìyóu</p>
                <p>ziyou</p>
            </test>
        ),
        xmldb:reindex($analyze:COLLECTION_PATH)
    )
};

declare
    %test:args("russelsheim")
    %test:assertEquals(2)
    %test:args("rüsselsheim")
    %test:assertEquals(2)
    %test:args("maori")
    %test:assertEquals(2)
    %test:args("Māori")
    %test:assertEquals(2)
    %test:args("ܦܘܼܪܫܵܢܵܐ")
    %test:assertEquals(3)
    %test:args("ܦܘܽܪܫܳܢܳܐ")
    %test:assertEquals(3)
    %test:args("ܦܘܪܫܢܐ")
    %test:assertEquals(3)
    %test:args("פָּעַל")
    %test:assertEquals(2)
    %test:args("פעל")
    %test:assertEquals(2)
    %test:args("بَردَيصان")
    %test:assertEquals(2)
    %test:args("برديصان")
    %test:assertEquals(2)
    %test:args("nǚ")
    %test:assertEquals(3)
    %test:args("nü")
    %test:assertEquals(3)
    %test:args("nu")
    %test:assertEquals(3)
    %test:args("zìyóu")
    %test:assertEquals(2)
    %test:args("ziyou")
    %test:assertEquals(2)
function analyze:no-diacrictics($term as xs:string) {
    count(collection($analyze:COLLECTION_PATH || "/test1")//p[ft:query(., $term)])
};

declare
    %test:args("russelsheim")
    %test:assertEquals(1)
    %test:args("rüsselsheim")
    %test:assertEquals(1)
    %test:args("maori")
    %test:assertEquals(1)
    %test:args("Māori")
    %test:assertEquals(1)
    %test:args("ܦܘܼܪܫܵܢܵܐ")
    %test:assertEquals(1)
    %test:args("ܦܘܽܪܫܳܢܳܐ")
    %test:assertEquals(1)
    %test:args("ܦܘܪܫܢܐ")
    %test:assertEquals(1)
    %test:args("פָּעַל")
    %test:assertEquals(1)
    %test:args("פעל")
    %test:assertEquals(1)
    %test:args("بَردَيصان")
    %test:assertEquals(1)
    %test:args("برديصان")
    %test:assertEquals(1)
    %test:args("nu")
    %test:assertEquals(1)
    %test:args("nü")
    %test:assertEquals(1)
    %test:args("nǚ")
    %test:assertEquals(1)
    %test:args("ziyou")
    %test:assertEquals(1)
    %test:args("zìyóu")
    %test:assertEquals(1)
function analyze:diacrictics($term as xs:string) {
    count(collection($analyze:COLLECTION_PATH || "/test2")//p[ft:query(., $term)])
};

declare
    %test:args("rüssels*")
    %test:assertEquals(2)
    %test:args("russels*")
    %test:assertEquals(2)
    %test:args("maor*")
    %test:assertEquals(2)
    %test:args("Māor*")
    %test:assertEquals(2)
    %test:args("ܦܘܪ*")
    %test:assertEquals(3)
    %test:args("פע*")
    %test:assertEquals(2)
    %test:args("بردي*")
    %test:assertEquals(2)
    %test:args("ziy*")
    %test:assertEquals(2)
    %test:args("n*")
    %test:assertEquals(3)
function analyze:query-parser($term as xs:string) {
    count(collection($analyze:COLLECTION_PATH || "/test1")//p[ft:query(., $term)])
};

(:~
 : Prefix queries via XML syntax (<query><prefix>...</prefix></query>).
 : Expects the same hits as string prefix: prefix text is normalized by the index
 : analyzer (e.g. diacritics stripped) so it matches indexed terms.
 : New test for Lucene 10; counterpart: analyze:query-parser (prefix args).
 :)
declare
    %test:args("rüssels")
    %test:assertEquals(2)
    %test:args("russels")
    %test:assertEquals(2)
    %test:args("maor")
    %test:assertEquals(2)
    %test:args("ziy")
    %test:assertEquals(2)
function analyze:query-parser-prefix-xml($prefix as xs:string) {
    count(collection($analyze:COLLECTION_PATH || "/test1")//p[ft:query(., <query><prefix>{$prefix}</prefix></query>)])
};

declare
    %test:tearDown
function analyze:tearDown() {
    xmldb:remove($analyze:COLLECTION_PATH),
    xmldb:remove($analyze:CONFIG_PATH)
};
