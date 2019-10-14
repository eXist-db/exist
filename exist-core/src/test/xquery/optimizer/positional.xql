xquery version "3.0";

(:~
 : Test all kinds of XQuery expressions to see if optimizer does properly 
 : analyze them and indexes are used in fully optimized manner.
 : 
 : Expressions use the @test:stats annotation to retrieve execution statistics
 : for each test function.
 :)
module namespace ot="http://exist-db.org/xquery/optimizer/test/positional";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ot:COLLECTION_NAME := "optimizertest";
declare variable $ot:COLLECTION := "/db/" || $ot:COLLECTION_NAME;
declare variable $ot:DOC := $ot:COLLECTION || "/test.xml";
declare variable $ot:DOC_NESTED := $ot:COLLECTION || "/nested.xml";

declare variable $ot:DATA :=
    <words>{ot:generate-words(50000)}</words>;

declare variable $ot:NESTED :=
    <test>
        <div>{ot:generate-words(20)}</div>
        <div>{ot:generate-words(20)}</div>
    </test>;

declare function ot:generate-words($count as xs:int) {
    for $i in (1 to $count) return <w xml:id="{$i}">{$i}</w>
};

declare
    %test:setUp
function ot:setup() {
    xmldb:create-collection("/db", $ot:COLLECTION_NAME),
    xmldb:store($ot:COLLECTION, "test.xml", $ot:DATA),
    xmldb:store($ot:COLLECTION, "nested.xml", $ot:NESTED)
};

declare
    %test:tearDown
function ot:cleanup() {
    xmldb:remove($ot:COLLECTION)
};

declare
    %test:assertEquals("<w xml:id='25001'>25001</w>")
function ot:simple-following() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[1]
};

declare
    %test:assertEquals("<w xml:id='25001'>25001</w>")
function ot:simple-following-node() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::node()[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-node() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::node()[1]
};

declare
    %test:assertEquals("25001")
function ot:simple-following-text() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::text()[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-text() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::text()[1]
};

declare
    %test:assertEquals("<w xml:id='1'>1</w>")
function ot:simple-following-nested() {
    let $w := doc($ot:DOC_NESTED)/test/*[1]/w[@xml:id='20']
    return
        $w/following::*[2]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-nested() {
    let $w := doc($ot:DOC_NESTED)/test/*[1]/w[@xml:id='20']
    return
        $w/following::*[2]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-preceding() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/preceding::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-preceding-sibling() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/preceding-sibling::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-simple-following-sibling() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following-sibling::*[1]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-multiple-filters() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[1][self::w]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-filter-with-operator() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    for $i in 1 to 5
    return
        $w/following::*[$i + 1][self::w]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-filter-with-operator2() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    for $i in 1 to 5
    return
        $w/following::*[1 + $i][self::w]
};

declare
    %test:assertEquals("<w xml:id='24999'>24999</w>", "<w xml:id='24998'>24998</w>")
function ot:optimize-following-filter-with-at() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    for $i at $p in 1 to 2
    return
        $w/preceding::w[$p]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:no-optimize-following-filter-with-function() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    let $i := 1
    return
        (: not optimized because max may return any atomic value, not just numbers :)
        $w/following::*[max(($i, 2))][self::w]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-filter-with-function() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[count(('a', 'b', 'c'))][self::w]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-following-filter-position() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[position() = 1]
};

declare
    %test:stats
    %test:assertXPath("not($result//stats:optimization[@type = 'PositionalPredicate'])")
function ot:optimize-following-filter-last() {
    let $w := doc($ot:DOC)//w[@xml:id='25000']
    return
        $w/following::*[last()]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:optimization[@type = 'PositionalPredicate']")
function ot:optimize-following-nested-filter() {
    doc($ot:DOC)//w[@xml:id='25000'][following::*[1] = "25001"]
};