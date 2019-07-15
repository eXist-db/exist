xquery version "3.1";

module namespace suggest="http://exist-db.org/xquery/lucene/test/suggestions";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $suggest:DOCUMENTS :=
    <documents>
        <document id="D-37/2">
            <title>Vogel, Vögel, Vogelhaus</title>
            <abstract>Es zwitschern die Vögel im Wald</abstract>
            <abstract>Über dem Walde weht ein Wind</abstract>
            <category>nature</category>
        </document>
        <document id="Z-49/2">
            <title>Streiten und Hoffen</title>
            <abstract>Da nun einmal der Himmel zerrissen und die Götter sich streiten</abstract>
            <category>philosophy</category>
        </document>
    </documents>;

declare variable $suggest:XCONF1 :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer" id="nodiacritics"/>
                <analyzer class="org.apache.lucene.analysis.core.KeywordAnalyzer" id="keyword"/>
                <analyzer class="org.apache.lucene.analysis.de.GermanAnalyzer" id="german"/>
                <analyzer class="org.apache.lucene.analysis.en.EnglishAnalyzer" id="english"/>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer" id="standard"/>
                <text qname="document">
                    <suggest id="document-content"/>
                    <field name="content" analyzer="standard">
                        <suggest id="document-text" type="freetext"/>
                    </field>
                    <field name="title" expression="title" analyzer="german">
                        <suggest id="document-title" type="fuzzy"/>
                    </field>
                    <field name="abstract" expression="abstract" analyzer="german">
                        <suggest id="document-abstract"/>
                    </field>
                    <field name="ident" expression="@id/string()" analyzer="keyword">
                        <suggest id="document-ident"/>
                    </field>
                    <facet dimension="cat" expression="category"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function suggest:setup() {
    let $testCol := xmldb:create-collection("/db", "lucenetest")
    let $confCol := xmldb:create-collection("/db/system/config/db", "lucenetest")
    return (
        xmldb:store($confCol, "collection.xconf", $suggest:XCONF1),
        xmldb:store($testCol, "documents.xml", $suggest:DOCUMENTS)
    )
};

declare
    %test:tearDown
function suggest:tearDown() {
    xmldb:remove("/db/lucenetest"),
    xmldb:remove("/db/system/config/db/lucenetest")
};

declare
    %test:assertEquals("wald", "walde", "weht", "wind", "vögel")
function suggest:no-field-standard-analyzer() {
    ft:suggest("document-content", "w"),
    ft:suggest("document-content", "vög")
};

declare
    %test:assertEquals("wald", "weht", "wind", "vogel")
function suggest:field-with-stemming() {
    ft:suggest("document-abstract", "w"),
    ft:suggest("document-abstract", "vög")
};

declare
    %test:assertEquals("vogel", "vogelhaus")
function suggest:field-fuzzy-suggester() {
    ft:suggest("document-title", "vogle")
};

declare
    %test:assertEquals("Z-49/2")
function suggest:field-keyword-analyzer() {
    ft:suggest("document-ident", "Z")
};