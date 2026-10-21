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
 : Analyzers configured as a chain of Lucene's analysis components: a tokenizer and filters, named by their SPI
 : names, with the component's other attributes as its arguments; and analyzers with one chain to index with and
 : another to query with. The fields' names are this test's own, as all collections share one index.
 :)
module namespace chain="http://exist-db.org/xquery/lucene/analyzers-chains/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $chain:COLLECTION_NAME := "lucene-test-analyzers-chains";
declare variable $chain:COLLECTION := "/db/" || $chain:COLLECTION_NAME;
declare variable $chain:CONFIG := "/db/system/config/db/" || $chain:COLLECTION_NAME;

declare variable $chain:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <!-- case-sensitive, without possessives, folded -->
                <analyzer id="cased">
                    <tokenizer name="standard"/>
                    <filter name="englishPossessive"/>
                    <filter name="asciiFolding"/>
                </analyzer>
                <!-- words as written, without the punctuation around them -->
                <analyzer id="literal">
                    <tokenizer name="whitespace"/>
                    <filter name="patternReplace" pattern="^\p{{Punct}}+|\p{{Punct}}+$" replacement=""/>
                </analyzer>
                <!-- F-16, F16 and "F 16" alike -->
                <analyzer id="delimited">
                    <index>
                        <tokenizer name="whitespace"/>
                        <filter name="englishPossessive"/>
                        <filter name="wordDelimiterGraph" preserveOriginal="1" catenateWords="1" catenateNumbers="1" catenateAll="1"/>
                        <filter name="flattenGraph"/>
                        <filter name="lowercase"/>
                        <filter name="asciiFolding"/>
                    </index>
                    <query>
                        <tokenizer name="whitespace"/>
                        <filter name="englishPossessive"/>
                        <filter name="wordDelimiterGraph" preserveOriginal="1"/>
                        <filter name="lowercase"/>
                        <filter name="asciiFolding"/>
                    </query>
                </analyzer>
                <!-- lowercased when indexed, not in a query: shows which chain a query uses -->
                <analyzer id="split">
                    <index>
                        <tokenizer name="standard"/>
                        <filter name="lowercase"/>
                    </index>
                    <query>
                        <tokenizer name="standard"/>
                    </query>
                </analyzer>
                <!-- stemmed -->
                <analyzer id="stemmed">
                    <tokenizer name="standard"/>
                    <filter name="lowercase"/>
                    <filter name="porterStem"/>
                </analyzer>
                <text qname="p">
                    <field name="chain-cased" expression="." analyzer="cased"/>
                    <field name="chain-literal" expression="." analyzer="literal"/>
                    <field name="chain-delimited" expression="." analyzer="delimited"/>
                    <field name="chain-split" expression="." analyzer="split"/>
                    <field name="chain-stemmed" expression="." analyzer="stemmed"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $chain:XML as document-node() :=
    document {
        <doc>
            <p id="goa">Goa</p>
            <p id="GOA">GOA</p>
            <p id="goa-comma">They arrived at Goa, then left.</p>
            <p id="f-16">The F-16 fighter</p>
            <p id="s-s">The S/S Normandie</p>
            <p id="banquo-s">Banquo's ghost</p>
            <p id="banquo-curly">Banquo’s ghost</p>
            <p id="banquo">Banquo appears</p>
            <p id="cafe">Café</p>
            <p id="flying">Flying boats</p>
            <p id="flywheel">The flywheel</p>
        </doc>
    };

declare
    %test:setUp
function chain:setUp() {
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $chain:COLLECTION_NAME),
    xmldb:store($chain:CONFIG, "collection.xconf", $chain:XCONF),
    xmldb:create-collection("/db", $chain:COLLECTION_NAME),
    xmldb:store($chain:COLLECTION, "doc.xml", $chain:XML)
};

declare
    %test:tearDown
function chain:tearDown() {
    xmldb:remove($chain:COLLECTION),
    xmldb:remove($chain:CONFIG)
};

declare %private function chain:found($query as xs:string) as xs:string* {
    collection($chain:COLLECTION)//p[ft:query(., $query)]/@id/string()
};

declare
    %test:args("chain-cased:Goa")
    %test:assertEquals("goa", "goa-comma")
    %test:args("chain-cased:GOA")
    %test:assertEquals("GOA")
    %test:args("chain-cased:goa")
    %test:assertEmpty
    %test:args("chain-cased:Banquo")
    %test:assertEquals("banquo-s", "banquo-curly", "banquo")
    %test:args("chain-cased:Cafe")
    %test:assertEquals("cafe")
    %test:args("chain-cased:Café")
    %test:assertEquals("cafe")
function chain:cased($query as xs:string) {
    chain:found($query)
};

declare
    %test:args("chain-literal:Goa")
    %test:assertEquals("goa", "goa-comma")
    %test:args("chain-literal:F-16")
    %test:assertEquals("f-16")
    %test:args("chain-literal:F16")
    %test:assertEmpty
    %test:args("chain-literal:S\/S")
    %test:assertEquals("s-s")
    %test:args("chain-literal:Banquo's")
    %test:assertEquals("banquo-s")
    %test:args("chain-literal:Banquo")
    %test:assertEquals("banquo")
function chain:literal($query as xs:string) {
    chain:found($query)
};

declare
    %test:args("chain-delimited:goa")
    %test:assertEquals("goa", "GOA", "goa-comma")
    %test:args("chain-delimited:F-16")
    %test:assertEquals("f-16")
    %test:args("chain-delimited:F16")
    %test:assertEquals("f-16")
    %test:args('chain-delimited:"F 16"')
    %test:assertEquals("f-16")
    %test:args("chain-delimited:S\/S")
    %test:assertEquals("s-s")
    %test:args("chain-delimited:SS")
    %test:assertEquals("s-s")
    %test:args("chain-delimited:Banquo")
    %test:assertEquals("banquo-s", "banquo-curly", "banquo")
function chain:index-and-query-chains($query as xs:string) {
    chain:found($query)
};

declare
    %test:args("chain-split:goa")
    %test:assertEquals("goa", "GOA", "goa-comma")
    %test:args("chain-split:Goa")
    %test:assertEmpty
function chain:query-uses-its-chain($query as xs:string) {
    chain:found($query)
};

declare
    %test:args("GOA")
    %test:assertEquals("GOA")
    %test:args("goa")
    %test:assertEmpty
function chain:query-field($query as xs:string) {
    collection($chain:COLLECTION)//p[ft:query-field("chain-cased", $query)]/@id/string()
};

declare
    %test:args("Goa")
    %test:assertEquals("goa", "GOA", "goa-comma")
function chain:class-analyzer-beside-chains($query as xs:string) {
    chain:found($query)
};

declare
    %test:args("chain-cased:Go*")
    %test:assertEquals("goa", "goa-comma")
    %test:args("chain-cased:GO*")
    %test:assertEquals("GOA")
    %test:args("chain-cased:Caf*")
    %test:assertEquals("cafe")
    %test:args("chain-cased:Café*")
    %test:assertEquals("cafe")
    %test:args("chain-delimited:GO*")
    %test:assertEquals("goa", "GOA", "goa-comma")
    %test:args("chain-stemmed:Fly*")
    %test:assertEquals("flying", "flywheel")
function chain:prefix($query as xs:string) {
    chain:found($query)
};
