xquery version "3.1";

module namespace suggest="http://exist-db.org/xquery/lucene/test/suggestions";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $suggest:DOCUMENTS :=
    <documents>
        <document id="B-01/1" xml:lang="en">
            <title>Evolutionary Biology</title>
            <abstract>Evolutionary biology is the subfield of biology that studies the evolutionary processes
            that produced the diversity of life on Earth, starting from a single common ancestor. These processes
            include natural selection, common descent, and speciation.</abstract>
            <category>biology</category>
        </document>
        <document id="B-01/2" xml:lang="it">
            <title>Biologia Evoluzionistica</title>
            <abstract>La biologia evolutiva è la disciplina scientifica della biologia che analizza l'origine e
            la discendenza delle specie, così come i loro cambiamenti, la loro diffusione e diversità nel corso
            del tempo. Uno studioso di biologia evolutiva è noto come biologo dell'evoluzione o, meno formalmente,
            evoluzionista.</abstract>
            <category>biology</category>
        </document>
        <document id="B-01/3" xml:lang="de">
            <title>Biologie</title>
            <abstract>Biologie (von altgriechisch βίος bíos, deutsch ‚Leben‘, und λόγος, lógos, hier: ‚Lehre‘;
            Biologie wurde früher auch Lebenskunde genannt) ist die Wissenschaft der Lebewesen.</abstract>
            <category>biology</category>
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
                    <field name="title" expression="title">
                        <suggest id="document-title" type="fuzzy"/>
                    </field>
                    <field name="title-en" expression="title" if="@xml:lang='en'">
                        <suggest id="title-en" use="stored"/>
                    </field>
                    <field name="title-it" expression="title" if="@xml:lang='it'">
                        <suggest id="title-it" use="stored"/>
                    </field>
                    <field name="abstract-en" expression="abstract" if="@xml:lang='en'">
                        <suggest id="abstract-en"/>
                    </field>
                    <field name="abstract-it" expression="abstract" if="@xml:lang='it'">
                        <suggest id="abstract-it"/>
                    </field>
                    <field name="ident" expression="@id/string()" analyzer="keyword">
                        <suggest id="document-ident"/>
                    </field>
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
    %test:assertEquals("biologia", "biologie", "biologo", "biology")
function suggest:no-field-standard-analyzer() {
    ft:suggest("document-content", "bio")
};

declare
    %test:assertEquals("Evolutionary Biology")
function suggest:title-en() {
    ft:suggest("title-en", "biol")
};

declare
    %test:assertEquals("Biologia Evoluzionistica")
function suggest:title-it() {
    ft:suggest("title-it", "biol")
};

declare
    %test:assertEquals("Biologia Evoluzionistica")
function suggest:title-it-multi() {
    ft:suggest("title-it", "biologia evo")
};

declare
    %test:assertEquals("evolutiva", "evoluzionista")
function suggest:abstract-it() {
    ft:suggest("abstract-it", "evol")
};

declare
    %test:assertEquals("evolutionary")
function suggest:abstract-en() {
    ft:suggest("abstract-en", "evol")
};

declare
    %test:assertEquals("biologia", "biologie")
function suggest:field-fuzzy-suggester() {
    ft:suggest("document-title", "biologei")
};

declare
    %test:assertEquals("B-01/1", "B-01/2", "B-01/3")
function suggest:field-keyword-analyzer() {
    ft:suggest("document-ident", "B-01")
};