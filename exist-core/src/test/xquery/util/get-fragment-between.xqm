xquery version "3.1";

module namespace gfb = "http://exist-db.org/test/util/get-fragment-between";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace tei = "http://www.tei-c.org/ns/1.0";

declare variable $gfb:DOC1 :=
    <TEI xmlns="http://www.tei-c.org/ns/1.0">
        <text>
            <front>
                <pb facs="1.jpg"/>
                <p>Aus dem Leben einer Kartoffel.</p>
                <pb facs="2.jpg"/>
            </front>
            <body>
                <pb facs="3.jpg"/>
                <p>Hubertus Knoll spazierte über das <pb facs="4.jpg"/> Feld.</p>
                <pb facs="5.jpg"/>
            </body>
        </text>
    </TEI>;

declare variable $gfb:DOC2 :=
    <root xmlns="http://exist-db.org/xquery/xqsuite">
        <x/>
        <y/>
        <z/>
    </root>;

declare
    %test:setUp
function gfb:setup() {
    xmldb:create-collection("/db", "test-gfb"),
    xmldb:store("/db/test-gfb", "doc1.xml", $gfb:DOC1),
    xmldb:store("/db/test-gfb", "doc2.xml", $gfb:DOC2)
};

declare
    %test:tearDown
function gfb:teardown() {
    xmldb:remove("/db/test-gfb")
};

declare
    %test:assertEquals('<pb facs="1.jpg"></pb><p>Aus dem Leben einer Kartoffel.</p>')
function gfb:tei-fragment() {
    let $doc := doc("/db/test-gfb/doc1.xml")
    let $pbs := $doc//tei:pb
    let $pb1 := $pbs[1]
    let $pb2 := $pbs[2]
    return
        util:get-fragment-between($pb1, $pb2, false(), false())
};

declare
    %test:assertEquals('<TEI><text><front><pb facs="1.jpg"></pb><p>Aus dem Leben einer Kartoffel.</p></front></text></TEI>')
function gfb:tei-fragment-root() {
    let $doc := doc("/db/test-gfb/doc1.xml")
    let $pbs := $doc//tei:pb
    let $pb1 := $pbs[1]
    let $pb2 := $pbs[2]
    return
        fn:replace(util:get-fragment-between($pb1, $pb2, true(), false()), "\n", "")
};

declare
    %test:assertEquals('<TEI xmlns="http://www.tei-c.org/ns/1.0"><text><front><pb facs="1.jpg"></pb><p>Aus dem Leben einer Kartoffel.</p></front></text></TEI>')
function gfb:tei-fragment-root-ns() {
    let $doc := doc("/db/test-gfb/doc1.xml")
    let $pbs := $doc//tei:pb
    let $pb1 := $pbs[1]
    let $pb2 := $pbs[2]
    return
        fn:replace(util:get-fragment-between($pb1, $pb2, true(), true()), "\n", "")
};

declare
    %test:assertEquals("<x></x>")
function gfb:fragment-no-namespace() {
    let $doc := doc("/db/test-gfb/doc2.xml")
    let $elems := $doc/test:root/*
    let $fragment := util:get-fragment-between($elems[1], $elems[2], false(), false())
    return
        $fragment
};

declare
    %test:assertEquals("<root><x></x></root>")
function gfb:wrapped-fragment-no-namespace() {
    let $doc := doc("/db/test-gfb/doc2.xml")
    let $elems := $doc/test:root/*
    let $fragment := util:get-fragment-between($elems[1], $elems[2], true(), false())
    return
        $fragment => replace("\s", "")
};

declare
    %test:assertTrue
function gfb:wrapped-fragment-is-parseable() {
    let $doc := doc("/db/test-gfb/doc2.xml")
    let $elems := $doc/test:root/*
    let $fragment := util:get-fragment-between($elems[1], $elems[2], true(), false())
    let $parsed := try { parse-xml($fragment) } catch * { $err:code }
    return
        $parsed instance of document-node(element(root))
};
