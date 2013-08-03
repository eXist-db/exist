xquery version "3.0";

(:~
 : Test all kinds of XQuery expressions to see if optimizer does properly 
 : analyze them and indexes are used in fully optimized manner.
 : 
 : Expressions use the @test:stats annotation to retrieve execution statistics
 : for each test function.
 :)
module namespace ot="http://exist-db.org/xquery/optimizer/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ot:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <fulltext default="none" attributes="false"/>
            <create qname="name" type="xs:string"/>
        </index>
    </collection>;

declare variable $ot:DATA :=
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

declare variable $ot:COLLECTION_NAME := "optimizertest";
declare variable $ot:COLLECTION := "/db/" || $ot:COLLECTION_NAME;

declare
    %test:setUp
function ot:setup() {
    xmldb:create-collection("/db/system/config/db", $ot:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ot:COLLECTION_NAME, "collection.xconf", $ot:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ot:COLLECTION_NAME),
    xmldb:store($ot:COLLECTION, "test.xml", $ot:DATA)
};

declare
    %test:tearDown
function ot:cleanup() {
    xmldb:remove($ot:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ot:COLLECTION_NAME)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-simple-comparison($name as xs:string) {
    collection($ot:COLLECTION)//address[name = $name]/city/text()
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 0]")
function ot:no-optimize-simple-comparison($name as xs:string) {
    collection($ot:COLLECTION)//address[city = $name]/city/text()
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-sequence($name as xs:string) {
    (collection($ot:COLLECTION)//address[name = $name]/city/text(), "xxx")
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-let($name as xs:string) {
    let $city := collection($ot:COLLECTION)//address[name = $name]/city/text()
    return
        $city
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 0]")
function ot:no-optimize-let($name as xs:string) {
    let $city := collection($ot:COLLECTION)//address[city = $name]/city/text()
    return
        $city
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-for($name as xs:string) {
    for $city in collection($ot:COLLECTION)//address[name = $name]/city/text()
    return
        $city
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 0]")
function ot:no-optimize-for($name as xs:string) {
    for $city in collection($ot:COLLECTION)//address[city = $name]/city/text()
    return
        $city
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-if-then($name as xs:string) {
    if (1 = 1) then
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    else
        ()
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-if-else($name as xs:string) {
    if (1 = 2) then
        ()
    else
        collection($ot:COLLECTION)//address[name = $name]/city/text()
};

declare %private function ot:find-by-name($name as xs:string) {
    collection($ot:COLLECTION)//address[name = $name]/city/text()
};

declare %private function ot:find-by-city($name as xs:string) {
    collection($ot:COLLECTION)//address[city = $name]/city/text()
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-function-call($name as xs:string) {
    ot:find-by-name($name)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:no-optimize-function-call($name as xs:string) {
    ot:find-by-name($name)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-try($name as xs:string) {
    try {
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    } catch * {
        ()
    }
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-catch($name as xs:string) {
    try {
        xs:int("abc")
    } catch * {
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    }
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-attribute-enclosed($name as xs:string) {
    <a title="{collection($ot:COLLECTION)//address[name = $name]/city/text()}"></a>
};

declare
    %test:stats
    %test:args("Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 0]")
function ot:no-optimize-attribute-enclosed($name as xs:string) {
    <a title="{collection($ot:COLLECTION)//address[city = $name]/city/text()}"></a>
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-element-enclosed($name as xs:string) {
    <a>{collection($ot:COLLECTION)//address[name = $name]/city/text()}</a>
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-element-dynamic-enclosed($name as xs:string) {
    element a {
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    }
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-attribute-dynamic-enclosed($name as xs:string) {
    <a>
    {
        attribute title {
            collection($ot:COLLECTION)//address[name = $name]/city/text()
        }
    }
    </a>
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-inline-function($name as xs:string) {
    let $f := function() {
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    }
    return
        $f()
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-inline-function-enclosed-attribute($name as xs:string) {
    let $f := function() {
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    }
    return
        <a title="{$f()}"></a>
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-inline-function-enclosed($name as xs:string) {
    let $f := function() {
        collection($ot:COLLECTION)//address[name = $name]/city/text()
    }
    return
        <a>{$f()}</a>
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-function-reference($name as xs:string) {
    let $f := ot:find-by-name#1
    return
        $f($name)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-function-map($name as xs:string) {
    let $f := ot:find-by-name#1
    return
        map($f, $name)
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-function-partial($name as xs:string) {
    let $f1 := function($foo, $name) {
        ot:find-by-name($name)
    }
    let $f2 := $f1("xxx", ?)
    return
        $f2($name)
};

declare
    %test:stats
    %test:args(1, "Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
    %test:args(2, "Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 0]")
    %test:args(3, "Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-switch($case as xs:int, $name as xs:string) {
    switch($case)
        case 1 return
            collection($ot:COLLECTION)//address[name = $name]/city/text()
        case 2 return
            collection($ot:COLLECTION)//address[city = $name]/city/text()
        default return
            collection($ot:COLLECTION)//address[name = $name]/city/text()
};

declare
    %test:stats
    %test:args("<a/>", "Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
    %test:args("<b/>", "Rüsselsheim")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 0]")
    %test:args("<c/>", "Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-typeswitch($case as element(), $name as xs:string) {
    typeswitch($case)
        case element(a) return
            collection($ot:COLLECTION)//address[name = $name]/city/text()
        case element(b) return
            collection($ot:COLLECTION)//address[city = $name]/city/text()
        default return
            collection($ot:COLLECTION)//address[name = $name]/city/text()
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-map($name as xs:string) {
    let $map := map {
        "key" := collection($ot:COLLECTION)//address[name = $name]/city/text()
    }
    return
        $map("key")
};

declare
    %test:stats
    %test:args("Rudi Rüssel")
    %test:assertXPath("$result//stats:index[@type = 'range'][@optimization = 2]")
function ot:optimize-map-entry($name as xs:string) {
    let $map := map:entry(
        "key", collection($ot:COLLECTION)//address[name = $name]/city/text()
    )
    return
        $map("key")
};