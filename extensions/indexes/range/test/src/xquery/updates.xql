xquery version "3.0";

module namespace rt="http://exist-db.org/xquery/range/test/updates";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare namespace mods="http://www.loc.gov/mods/v3";

declare variable $rt:COLLECTION := "/db/rangetest";

declare variable $rt:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#" xmlns:tei="http://www.tei-c.org/ns/1.0" xmlns:vra="http://www.vraweb.org/vracore4.htm" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:mods="http://www.loc.gov/mods/v3">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                
                <!--MODS-->
                <text qname="mods:title"/>
            </lucene>
            <range>
                <create qname="mods:name">
                    <field name="name-type" match="@type" type="xs:string"/>
                    <field name="name-part" match="mods:namePart" type="xs:string" case="yes"/>
                </create>
                <create qname="mods:mods">
                    <field name="mods-dateIssued" match="mods:originInfo/mods:dateIssued" type="xs:string"/>
                    <field name="mods-id" match="@ID" type="xs:string"/>
                    <field name="mods-authority" match="@authority" type="xs:string"/>
                    <field name="mods-lang" match="@lang" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $rt:DATA := 
    <mods:modsCollection>
        <mods:mods ID="books/aw/Knuth86a">
            <mods:titleInfo>
                <mods:title>TeX: The Program</mods:title>
            </mods:titleInfo>
            <mods:name type="personal">
                <mods:namePart>Donald E. Knuth</mods:namePart>
            </mods:name>
            <mods:originInfo>
                <mods:dateIssued>1986</mods:dateIssued>
                <mods:publisher>Addison-Wesley</mods:publisher>
            </mods:originInfo>
            <mods:identifier type="isbn">0-201-13437-3</mods:identifier>
        </mods:mods>
        <mods:mods ID="books/aw/Lamport86">
            <mods:titleInfo>
                <mods:title>LaTeX: User's Guide &amp; Reference Manual</mods:title>
            </mods:titleInfo>
            <mods:name type="personal">
                <mods:namePart>Leslie Lamport</mods:namePart>
            </mods:name>
            <mods:originInfo>
                <mods:dateIssued>1986</mods:dateIssued>
                <mods:publisher>Addison-Wesley</mods:publisher>
            </mods:originInfo>
            <mods:identifier type="isbn">0-201-15790-X</mods:identifier>
        </mods:mods>
    </mods:modsCollection>
;

declare
    %test:setUp
function rt:setup() {
    xmldb:create-collection("/db/system/config/db", "rangetest"),
    xmldb:store("/db/system/config/db/rangetest", "collection.xconf", $rt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", "rangetest"),
    xmldb:store($rt:COLLECTION, "test.xml", $rt:DATA)
};

declare
    %test:tearDown
function rt:cleanup() {
    xmldb:remove($rt:COLLECTION, "test.xml"),
    xmldb:remove($rt:COLLECTION),
    xmldb:remove("/db/system/config/db/rangetest")
};

declare
    %test:assertEquals(1)
function rt:t00_query() {
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Leslie Lamport")])
};

declare 
    %test:assertEquals(1, 1)
function rt:t01_replaceTitle() {
    update replace
        collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "latex")]
            /mods:titleInfo/mods:title
    with
        <mods:title>The best text processor ever</mods:title>,
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "'text processor'")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Leslie Lamport")])
};

declare 
    %test:assertEquals(1, 1, 1)
function rt:t02_insertName() {
    update insert
        <mods:name type="personal">
            <mods:namePart>Hansi Reiher</mods:namePart>
        </mods:name>
    into
        collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")],
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Hansi Reiher")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Donald E. Knuth")])
};

declare 
    %test:assertEquals(1, 1, 1, 1)
function rt:t03_insertAfter() {
    update insert
        <mods:name type="personal">
            <mods:namePart>Gerda Schwan</mods:namePart>
        </mods:name>
    following
        collection($rt:COLLECTION)//mods:mods/range:field-eq("name-part", "Donald E. Knuth"),
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Hansi Reiher")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Donald E. Knuth")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Gerda Schwan")])
};

declare 
    %test:assertEquals(1, 1, 1, 1, 1)
function rt:t04_insertBefore() {
    update insert
        <mods:name type="personal">
            <mods:namePart>Susi Spatz</mods:namePart>
        </mods:name>
    preceding
        collection($rt:COLLECTION)//mods:mods/range:field-eq("name-part", "Donald E. Knuth"),
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Hansi Reiher")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Donald E. Knuth")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Gerda Schwan")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Susi Spatz")])
};

declare 
    %test:assertEquals(1, 0, 1)
function rt:t05_replaceName() {
    update replace
        collection($rt:COLLECTION)//mods:mods/range:field-eq("name-part", "Susi Spatz")
    with
        <mods:name>
            <mods:namePart>Manfred Specht</mods:namePart>
        </mods:name>,
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Susi Spatz")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Manfred Specht")])
};

declare 
    %test:assertEquals(1, 1, 0)
function rt:t06_replaceName() {
    update replace
        collection($rt:COLLECTION)//mods:mods/mods:name[mods:namePart = "Manfred Specht"]/mods:namePart
    with
        <mods:namePart>Doris Drossel</mods:namePart>,
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[mods:name[mods:namePart = "Doris Drossel"]]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Manfred Specht")])
};

declare 
    %test:assertEquals(1, 1, 0)
function rt:t07_updateName() {
    update value
        collection($rt:COLLECTION)//mods:mods/mods:name[mods:namePart = "Doris Drossel"]/mods:namePart
    with
        "Adolf Adler",
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[mods:name[mods:namePart = "Adolf Adler"]]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Doris Drossel")])
};

declare 
    %test:assertEquals(1, 1, 1, 0)
function rt:t08_updateIDAttrib() {
    update value
        collection($rt:COLLECTION)//mods:mods[@ID = "books/aw/Lamport86"]/@ID
    with
        "CHANGED_ID",
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "'text processor'")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Leslie Lamport")]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_ID"]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "books/aw/Lamport86"])
};

declare 
    %test:assertEquals(1, 1, 1, 0)
function rt:t09_replaceIDAttrib() {
    update replace
        collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_ID"]/@ID
    with
        attribute ID { "CHANGED_2" },
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "'text processor'")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Leslie Lamport")]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_2"]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_ID"])
};

declare 
    %test:assertEquals(1, 1, 1, 0)
function rt:t10_updateYear() {
    update replace
        collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_2"]/mods:originInfo/mods:dateIssued
    with
        <mods:dateIssued>2014</mods:dateIssued>,
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "'text processor'")]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_2"][range:field-eq("name-part", "Leslie Lamport")]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_2"][mods:originInfo/mods:dateIssued = "2014"]),
    count(collection($rt:COLLECTION)//mods:mods[@ID = "CHANGED_2"][mods:originInfo/mods:dateIssued = "1986"])
};

declare
    %test:assertEquals(1, 1, 0)
function rt:t11_deleteName() {
    update delete
        collection($rt:COLLECTION)//mods:mods/mods:name[mods:namePart = "Donald E. Knuth"],
    count(collection($rt:COLLECTION)//mods:mods[ft:query(mods:titleInfo/mods:title, "program")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Hansi Reiher")]),
    count(collection($rt:COLLECTION)//mods:mods[range:field-eq("name-part", "Donald E. Knuth")])
};
