xquery version "3.0";

(:~
 : Test all kinds of XQuery expressions to see if optimizer does properly
 : analyze them and indexes are used in fully optimized manner.
 :
 : Expressions use the @test:stats annotation to retrieve execution statistics
 : for each test function.
 :)

module namespace fto="http://exist-db.org/xquery/ft-optimizer/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $fto:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="name"/>
            </lucene>
            <create qname="name" type="xs:string"/>
        </index>
    </collection>;

declare variable $fto:DATA :=
    <test>
        <address id="muh">
            <name>Berta Muh</name>
            <street>Wiesenweg 14</street>
            <city code="65463">Almweide</city>
        </address>
        <address id="rüssel">
            <name>Rudi Rüssel</name>
            <street>Elefantenweg 67</street>
            <city code="65428">Rüsselsheim</city>
        </address>
        <address id="amsel">
            <name>Albert Amsel</name>
            <street>Birkenstraße 77</street>
            <city code="76878">Waldstadt</city>
        </address>
        <address id="reh">
            <name>Pü Reh</name>
            <street>Am Waldrand 4</street>
            <city code="89283">Wiesental</city>
        </address>
    </test>;

declare variable $fto:COLLECTION_NAME := "ft-optimizertest";
declare variable $fto:COLLECTION := "/db/" || $fto:COLLECTION_NAME;

declare
    %test:setUp
function fto:setup() {
    xmldb:create-collection("/db/system/config/db", $fto:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $fto:COLLECTION_NAME, "collection.xconf", $fto:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $fto:COLLECTION_NAME),
    xmldb:store($fto:COLLECTION, "test.xml", $fto:DATA)
};

declare
    %test:tearDown
function fto:cleanup() {
    xmldb:remove($fto:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $fto:COLLECTION_NAME)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@calls = 1]")
function fto:optimize-grouped-context($name as xs:string) {
    collection($fto:COLLECTION)//(name)[ft:query(., $name)]
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@calls = 1]")
function fto:optimize-grouped-context2($name as xs:string) {
    collection($fto:COLLECTION)//(name|foo)[ft:query(., $name)]
};

declare %private function fto:collection-helper($path as xs:string) {
    collection($path)//address
};

declare
    %test:args("Rudi Rüssel")
    %test:assertEquals("Rüsselsheim")
function fto:do-not-simplify($name as xs:string) {
    fto:collection-helper($fto:COLLECTION)[ft:query(name, $name)]/city/string()
};
