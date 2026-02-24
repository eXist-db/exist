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
 : XQSuite tests for Lucene indexing and query forms (term, phrase, near, wildcard, regex, etc.).
 : Refactored from queries.xml (TestSet). Uses $qrys:COLLECTION with self-contained sample play + text1/text2.
 :
 : @author Wolfgang Meier
 : @author Leif-Jöran Olsson
 :)
module namespace qrys="http://exist-db.org/xquery/lucene/queries/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace tei="http://www.tei-c.org/ns/1.0";
declare namespace tt="urn:test";
declare namespace exist="http://exist.sourceforge.net/NS/exist";
declare namespace stats="http://exist-db.org/xquery/profiling";

(:~
 : Collection config: p, tt:p, tei:p, id, para, b, tt:note, z, LINE, @type.
 :)
declare variable $qrys:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:tei="http://www.tei-c.org/ns/1.0" xmlns:tt="urn:test">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="stop" class="org.apache.lucene.analysis.core.StopAnalyzer"/>
                <analyzer id="keyword" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="p"/>
                <text qname="tt:p"/>
                <text qname="tei:p"/>
                <text qname="id" field="id" analyzer="keyword"/>
                <text qname="para" analyzer="stop"/>
                <text qname="b"/>
                <text qname="tt:note"/>
                <text qname="z"/>
                <text field="lines" qname="LINE"/>
                <text qname="LINE"/>
                <text qname="@type"/>
            </lucene>
        </index>
    </collection>;

(:~
 : text1.xml content.
 :)
declare variable $qrys:TEXT1 as element(test) :=
    <test xmlns:tt="urn:test">
        <p>Eins zwei drei vier zwei fünf sechs.</p>
        <p>Sieben acht <b>neun</b> zehn acht.</p>
        <para>The stopwords should not be indexed.</para>
        <p>Det är flera ord och fraser i det här stycket
        som börjar med det är flera ord och fraser här.
        Dessutom är stycket något krystat komponerat med
        ord och fraser i långa rader, men det är nu dags
        att verkligen sluta upprepa ord och fraser nu.</p>
        <tt:note>Note using different namespace.</tt:note>
        <x>
            <y>Nested <z>nodes</z></y>
        </x>
        <id>A11</id>
        <p xmlns="http://www.tei-c.org/ns/1.0">erste aus haus maus zaus yaus raus qaus leisten</p>
    </test>;

(:~
 : text2.xml content.
 :)
declare variable $qrys:TEXT2 as element(test) :=
    <test>
        <div type="chapter">
            <head>Div1</head>
            <p>First level</p>
            <div type="section">
                <head>Div2</head>
                <p>Second level</p>
                <div type="subsection">
                    <head>Div3</head>
                    <p>Third level</p>
                </div>
            </div>
        </div>
    </test>;

(:~
 : Self-contained sample play (PLAY/SCENE/SPEECH/SPEAKER/LINE) for LINE/field tests.
 : 10 SCENEs; 29 SPEECHes with LINE containing "king" (matches fields-line-king-count, query-field-lines-king, fields-descendant-scene-count).
 : SPEAKER has no LINE child so query-field-wrong-parent returns empty.
 :)
declare variable $qrys:SAMPLE_PLAY as document-node() :=
    document {
        <PLAY>
            {
                for $scene at $s in (3, 3, 3, 3, 3, 3, 3, 3, 2, 3)
                return
                    <SCENE>
                        <TITLE>Scene { $s }</TITLE>
                        { for $i in 1 to $scene return <SPEECH><SPEAKER>Speaker</SPEAKER><LINE>king</LINE></SPEECH> }
                    </SCENE>
            }
        </PLAY>
    };

declare variable $qrys:COLLECTION_NAME := "lucene-test-queries";
declare variable $qrys:COLLECTION := "/db/" || $qrys:COLLECTION_NAME;

(:~
 : Callback for util:index-keys (returns &lt;t&gt;term&lt;/t&gt;).
 :)
declare
    %private
function qrys:key($key as xs:string, $options as item()*) as element(t) {
    <t>{ $key }</t>
};

(:~
 : setUp: create config hierarchy, collection, store sample play + text1, text2, reindex.
 :)
declare
    %test:setUp
function qrys:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $qrys:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $qrys:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $qrys:COLLECTION_NAME, "collection.xconf", $qrys:XCONF),
      xmldb:store($qrys:COLLECTION, "hamlet.xml", $qrys:SAMPLE_PLAY),
      xmldb:store($qrys:COLLECTION, "macbeth.xml", $qrys:SAMPLE_PLAY),
      xmldb:store($qrys:COLLECTION, "r_and_j.xml", $qrys:SAMPLE_PLAY),
      xmldb:store($qrys:COLLECTION, "text1.xml", document { $qrys:TEXT1 }),
      xmldb:store($qrys:COLLECTION, "text2.xml", document { $qrys:TEXT2 }),
      xmldb:reindex($qrys:COLLECTION) )
};

(:~
 : tearDown: remove collection and config.
 :)
declare
    %test:tearDown
function qrys:tearDown() {
    ( xmldb:remove($qrys:COLLECTION),
      xmldb:remove("/db/system/config/db/" || $qrys:COLLECTION_NAME) )
};

(: --- Term / range / boolean / phrase / near / wildcard / regex (XML expected) --- :)

(:~
 : Term range query: [eins TO zwei] matches documents containing terms in that range.
 : Lucene QueryParser uses square brackets for inclusive range.
 : New test for Lucene 10; counterpart: qrys:term-range-query.
 :)
declare
    %test:assertTrue
function qrys:term-range-query-brackets() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '[eins TO zwei]')]
    return exists($result) and deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ Term range query (Lucene QueryParser requires [lower TO upper] brackets for range) :)
declare
    %test:assertTrue
function qrys:term-range-query() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '[eins TO zwei]')]
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ Case sensitivity :)
declare
    %test:assertTrue
function qrys:case-sensitivity() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., 'Eins')] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 1: boolean with must, matches :)
declare
    %test:assertTrue
function qrys:xml-query-bool-must-matches() {
    let $qu := <query><bool><term occur="must">eins</term><term occur="must">zwei</term></bool></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 1a: boolean with optional, matches, minimum of 4 results :)
declare
    %test:assertEmpty
function qrys:xml-query-bool-optional-min4() {
    let $qu := <query><bool min="4"><term occur="should">eins</term><term occur="should">zwei</term></bool></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
};

(:~ XML query test 1b: boolean with optional, matches, minimum of 2 results :)
declare
    %test:assertTrue
function qrys:xml-query-bool-optional-min2() {
    let $qu := <query><bool min="2"><term occur="should">eins</term><term occur="should">zwei</term></bool></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 2: boolean must, no match :)
declare
    %test:assertEmpty
function qrys:xml-query-bool-must-no-match() {
    let $qu := <query><bool><term occur="must">eins</term><term occur="must">sieben</term></bool></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
};

(:~ XML query test 3: boolean with should :)
declare
    %test:assertTrue
function qrys:xml-query-bool-should() {
    let $qu := <query><bool><term occur="must">eins</term><term occur="should">sieben</term></bool></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 4: phrase :)
declare
    %test:assertTrue
function qrys:xml-query-phrase() {
    let $qu := <query><phrase><term>eins</term><term>zwei</term></phrase></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 5: phrase, matches too distant :)
declare
    %test:assertEmpty
function qrys:xml-query-phrase-too-distant() {
    let $qu := <query><phrase><term>eins</term><term>drei</term></phrase></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
};

(:~ XML query test 6: phrase with slop 2 :)
declare
    %test:assertTrue
function qrys:xml-query-phrase-slop2() {
    let $qu := <query><phrase slop="2"><term>eins</term><term>drei</term></phrase></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 7: phrase, wrong order of terms :)
declare
    %test:assertEmpty
function qrys:xml-query-phrase-wrong-order() {
    let $qu := <query><phrase><term>zwei</term><term>eins</term></phrase></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
};

(:~ XML query test 8: near :)
declare
    %test:assertTrue
function qrys:xml-query-near() {
    let $qu := <query><near slop="4"><term>eins</term><term>vier</term></near></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 9: near, wrong slop :)
declare
    %test:assertEmpty
function qrys:xml-query-near-wrong-slop() {
    let $qu := <query><near slop="1"><term>eins</term><term>vier</term></near></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
};

(:~ XML query test 10: near unordered :)
declare
    %test:assertTrue
function qrys:xml-query-near-unordered() {
    let $qu := <query><near ordered="no"><term>zwei</term><term>eins</term></near></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 11: near nested :)
declare
    %test:assertTrue
function qrys:xml-query-near-nested() {
    let $qu := <query><near slop="10"><near><term>eins</term><term>zwei</term></near><near><term>zwei</term><term>fünf</term></near></near></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 12: near first :)
declare
    %test:assertTrue
function qrys:xml-query-near-first() {
    let $qu := <query><near slop="10"><first end="3"><term>zwei</term><term>drei</term></first><near><term>fünf</term><term>sechs</term></near></near></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 13: wildcard :)
declare
    %test:assertTrue
function qrys:xml-query-wildcard() {
    let $qu := <query><term>eins</term><wildcard>sech*</wildcard></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 14: regex :)
declare
    %test:assertTrue
function qrys:xml-query-regex() {
    let $qu := <query><term>eins</term><regex>sech.*</regex></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(:~ XML query test 15: near with regex :)
declare
    %test:assertTrue
function qrys:xml-query-near-regex() {
    let $qu := <query><near slop="5"><regex>[ei][ei]ns</regex><regex>s.ch.</regex></near></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., $qu)] return $hit
    return deep-equal($result, <p>Eins zwei drei vier zwei fünf sechs.</p>)
};

(: --- Wildcard context (xpath assertions) --- :)

(:~ Wildcard context: test[ft:query(*, "acht")] -> result has p :)
declare
    %test:assertXPath("exists($result/p)")
function qrys:wildcard-context-acht() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(*, "acht")]
};

(:~
 : Wildcard context ft:query(*, "should"): query all children of test; "should" appears in para.
 : test[ft:query(*, "should")] must return the test element whose child matches.
 : New test for Lucene 10; counterpart: qrys:wildcard-context-should.
 :)
declare
    %test:assertXPath("exists($result/p)")
function qrys:wildcard-context-should-has-p() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(*, "should")]
};

(:~ Wildcard context: test[ft:query(*, "should")] -> result has p :)
declare
    %test:assertXPath("exists($result/p)")
function qrys:wildcard-context-should() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(*, "should")]
};

(:~ Wildcard context: p[ft:query(*, "neun")] :)
declare
    %test:assertTrue
function qrys:wildcard-context-neun() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")/test/p[ft:query(*, "neun")]
    return deep-equal($result, <p>Sieben acht <b>neun</b> zehn acht.</p>)
};

(:~ Wildcard context: p[ft:query(*, "acht")] no match :)
declare
    %test:assertEmpty
function qrys:wildcard-context-acht-no-match() {
    doc($qrys:COLLECTION || "/text1.xml")/test/p[ft:query(*, "acht")]
};

(:~ Wildcard context: x[ft:query(*, "nodes")] no match :)
declare
    %test:assertEmpty
function qrys:wildcard-context-nested-no-match() {
    doc($qrys:COLLECTION || "/text1.xml")/test/x[ft:query(*, "nodes")]
};

(:~ Wildcard context: //y[ft:query(*, "nodes")] match in nested :)
declare
    %test:assertTrue
function qrys:wildcard-context-match-in-nested() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")/test//y[ft:query(*, "nodes")]
    return deep-equal($result, <y>Nested <z>nodes</z></y>)
};

(:~ Wildcard context: test[ft:query(.//*, "neun")] -> result has p :)
declare
    %test:assertXPath("exists($result/p)")
function qrys:wildcard-context-descendant-neun() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(.//*, "neun")]
};

(:~ Wildcard context: p[ft:query(./*, "neun")] :)
declare
    %test:assertTrue
function qrys:wildcard-context-self-child-neun() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(./*, "neun")]
    return deep-equal($result, <p>Sieben acht <b>neun</b> zehn acht.</p>)
};

(:~ Wildcard context: x[ft:query(.//*, "nodes")] no match (wrong path) :)
declare
    %test:assertEmpty
function qrys:wildcard-context-descendant-nodes() {
    doc($qrys:COLLECTION || "/text1.xml")/x[ft:query(.//*, "nodes")]
};

(:~ Wildcard context: x[ft:query(./y/*, "nodes")] no match :)
declare
    %test:assertEmpty
function qrys:wildcard-context-long-path() {
    doc($qrys:COLLECTION || "/text1.xml")/x[ft:query(./y/*, "nodes")]
};

(:~ Wildcard context: x[ft:query(./*, "nodes")] no match in child :)
declare
    %test:assertEmpty
function qrys:wildcard-context-no-match-child() {
    doc($qrys:COLLECTION || "/text1.xml")/x[ft:query(./*, "nodes")]
};

(:~ Wildcard context with prefix wildcard *:p "acht" -> result has *:p :)
declare
    %test:assertXPath("exists($result/*:p)")
function qrys:wildcard-context-prefix-wildcard-acht() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(*:p, "acht")]
};

(:~ Wildcard context *:note "namespace" -> result has *:note :)
declare
    %test:assertXPath("exists($result/*:note)")
function qrys:wildcard-context-prefix-wildcard-note() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(*:note, "namespace")]
};

(:~ Wildcard context with namespace tt:*, "namespace" :)
declare
    %test:assertTrue
function qrys:wildcard-context-namespace() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(tt:*, "namespace")]/tt:note
    return deep-equal($result, <tt:note xmlns:tt="urn:test">Note using different namespace.</tt:note>)
};

(: --- Optimizer tests (trace / stats) --- :)

(:~ Optimizer: query on element b "neun" :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])")
function qrys:optimizer-query-on-element() {
    doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(b, "neun")]
};

(:~ Optimizer: query on self "acht" :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])")
function qrys:optimizer-query-on-self() {
    doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., "acht")]
};

(:~ Optimizer: query on wildcard * "acht" :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])")
function qrys:optimizer-query-on-wildcard() {
    doc($qrys:COLLECTION || "/text1.xml")/test[ft:query(*, "acht")]
};

(:~ Optimizer: wildcard context .//* "nodes" :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])")
function qrys:optimizer-wildcard-self-descendant() {
    doc($qrys:COLLECTION || "/text1.xml")//x[ft:query(.//*, "nodes")]
};

(:~ Optimizer: wildcard context ./y/* "nodes" :)
declare
    %test:stats
    %test:assertXPath("exists($result//stats:index[@type eq 'lucene'][@optimization-level eq 'OPTIMIZED'])")
function qrys:optimizer-wildcard-long-path() {
    doc($qrys:COLLECTION || "/text1.xml")//x[ft:query(./y/*, "nodes")]
};

(: --- Phrase highlighting --- :)

(:~ Phrase highlighting 1: "zwei drei" :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-1() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"zwei drei"')] return util:expand($hit)
    return deep-equal($result, <p>Eins <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">zwei drei</exist:match> vier zwei fünf sechs.</p>)
};

(:~ Phrase highlighting 2: "eins zwei" :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-2() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"eins zwei"')] return util:expand($hit)
    return deep-equal($result, <p><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">Eins zwei</exist:match> drei vier zwei fünf sechs.</p>)
};

(:~ Phrase highlighting 3: "zwei fünf" :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-3() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"zwei fünf"')] return util:expand($hit)
    return deep-equal($result, <p>Eins zwei drei vier <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">zwei fünf</exist:match> sechs.</p>)
};

(:~ Phrase highlighting 4: "eins zwei" (duplicate of 2) :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-4() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"eins zwei"')] return util:expand($hit)
    return deep-equal($result, <p><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">Eins zwei</exist:match> drei vier zwei fünf sechs.</p>)
};

(:~ Phrase highlighting 5: "acht neun" on b :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-5() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"acht neun"')] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Phrase highlighting 6: multiple phrases "ord och fraser" (4 matches). Paragraph worded so no occurrence ends with punctuation (avoids highlighter returning 3). :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-6() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"ord och fraser"')] return util:expand($hit)//exist:match
    return count($result) eq 4 and (every $m in $result satisfies $m/self::exist:match and normalize-space(string($m)) eq "ord och fraser")
};

(:~ Phrase highlighting 7: "stycket något krystat" :)
declare
    %test:assertTrue
function qrys:phrase-highlighting-7() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., '"stycket något krystat"')] return util:expand($hit)//exist:match
    return deep-equal($result, <exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">stycket något krystat</exist:match>)
};

(: --- Match highlighting: prefix, wildcard, regex, fuzzy, near --- :)

(:~ Match highlighting: prefix query neu* :)
declare
    %test:assertTrue
function qrys:match-highlight-prefix() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., 'neu*')] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Match highlighting: prefix query XML syntax :)
declare
    %test:assertTrue
function qrys:match-highlight-prefix-xml() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., <query><prefix>neu</prefix></query>)] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Match highlighting: wildcard *eu* :)
declare
    %test:assertTrue
function qrys:match-highlight-wildcard() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., <query><wildcard>*eu*</wildcard></query>)] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Match highlighting: wildcard TEI NS multi-term count 42. Assert in-function to avoid assertXPath namespace injection (xmlns prefix). :)
declare
    %test:assertEquals(42)
function qrys:match-highlight-wildcard-tei-count() {
    let $result := for $expr in ("aus", "haus", "maus", "zaus", "yaus", "raus", "qaus",
                                  "a*", "h*", "m*", "z*", "y*", "r*", "q*",
                                  "au*", "ha*", "ma*", "za*", "ya*", "ra*", "qa*",
                                  "aus*", "hau*", "mau*", "zau*", "yau*", "rau*", "qau*",
                                  "a*s", "h*s", "m*s", "z*s", "y*s", "r*s", "q*s",
                                  "au*s", "ha*s", "ma*s", "za*s", "ya*s", "ra*s", "qa*s")
                  let $query := <query><wildcard>{ $expr }</wildcard></query>
                  return for $hit in doc($qrys:COLLECTION || "/text1.xml")//tei:p[ft:query(., $query)] return util:expand($hit)//exist:match
    return count($result)
};

(:~ Match highlighting: regex .?eu.* :)
declare
    %test:assertTrue
function qrys:match-highlight-regex() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., <query><regex>.?eu.*</regex></query>)] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Match highlighting: fuzzy neue :)
declare
    %test:assertTrue
function qrys:match-highlight-fuzzy() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., <query><fuzzy>neue</fuzzy></query>)] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Match highlighting: near "acht neun" :)
declare
    %test:assertTrue
function qrys:match-highlight-near-string() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., <query><near>acht neun</near></query>)] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(:~ Match highlighting: near XML (term + near) :)
declare
    %test:assertTrue
function qrys:match-highlight-near-xml() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., <query><near><term>acht</term><near>neun</near></near></query>)] return util:expand($hit/b)
    return deep-equal($result, <b><exist:match xmlns:exist="http://exist.sourceforge.net/NS/exist">neun</exist:match></b>)
};

(: --- index-keys (callback) --- :)

(:~ index-keys test 1: by-qname p, no start :)
declare
    %test:assertTrue
function qrys:index-keys-by-qname-all() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ util:index-keys-by-qname(xs:QName("p"), (), $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms>
        <t>acht</t><t>att</t><t>börjar</t><t>dags</t><t>dessutom</t><t>det</t><t>drei</t><t>eins</t><t>first</t><t>flera</t>
        <t>fraser</t><t>fünf</t><t>här</t><t>i</t><t>komponerat</t><t>krystat</t><t>level</t><t>långa</t><t>med</t><t>men</t>
        <t>neun</t><t>nu</t><t>något</t><t>och</t><t>ord</t><t>rader</t><t>sechs</t><t>second</t><t>sieben</t><t>sluta</t><t>som</t>
        <t>stycket</t><t>third</t><t>upprepa</t><t>verkligen</t><t>vier</t><t>zehn</t><t>zwei</t><t>är</t>
    </terms>)
};

(:~ index-keys test 2: by-qname p, start "s" :)
declare
    %test:assertTrue
function qrys:index-keys-by-qname-s() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ util:index-keys-by-qname(xs:QName("p"), "s", $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms><t>sechs</t><t>second</t><t>sieben</t><t>sluta</t><t>som</t><t>stycket</t></terms>)
};

(:~ index-keys test 3: non-existent collection :)
declare
    %test:assertTrue
function qrys:index-keys-nonexistent-collection() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ collection("/db/does/not/exist")/util:index-keys-by-qname(xs:QName("p"), "s", $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms/>)
};

(:~ index-keys test 4: index-keys on nodes p, start "s" :)
declare
    %test:assertTrue
function qrys:index-keys-on-nodes-s() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ util:index-keys(doc($qrys:COLLECTION || "/text1.xml")//p, "s", $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms><t>sechs</t><t>sieben</t><t>sluta</t><t>som</t><t>stycket</t></terms>)
};

(:~ index-keys test 5: index-keys on ft:query result "zehn" -> "s" :)
declare
    %test:assertTrue
function qrys:index-keys-on-query-result() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ util:index-keys(doc($qrys:COLLECTION || "/text1.xml")//p[ft:query(., 'zehn')], "s", $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms><t>sieben</t></terms>)
};

(:~ index-keys test 6: index-keys on @type :)
declare
    %test:assertTrue
function qrys:index-keys-attr-type() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ util:index-keys(doc($qrys:COLLECTION || "/text2.xml")//@type, "", $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms><t>chapter</t><t>section</t><t>subsection</t></terms>)
};

(:~ index-keys test 7: by-qname @type (previously commented out in XML). :)
declare
    %test:pending("previously commented out; index-keys-by-qname with @type")
    %test:assertTrue
function qrys:index-keys-by-qname-attr-type() {
    let $callback := util:function(xs:QName("qrys:key"), 2),
        $result := <terms>{ util:index-keys-by-qname(xs:QName("@type"), "", $callback, 10000, "lucene-index") }</terms>
    return deep-equal($result, <terms><t>chapter</t><t>section</t><t>subsection</t></terms>)
};

(: --- Analyzer (stopword) tests --- :)

(:~ Analyzer test: phrase "and indexed" on para :)
declare
    %test:assertTrue
function qrys:analyzer-phrase-stopword() {
    let $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., '"and indexed"')] return $hit
    return deep-equal($result, <para>The stopwords should not be indexed.</para>)
};

(:~ Analyzer test 1: phrase XML and+indexed :)
declare
    %test:assertTrue
function qrys:analyzer-phrase-stopword-xml() {
    let $qu := <query><phrase><term>and</term><term>indexed</term></phrase></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
    return deep-equal($result, <para>The stopwords should not be indexed.</para>)
};

(:~
 : Phrase "and indexed" with stopword: para contains "The stopwords should not be indexed."
 : StandardAnalyzer removes "and"; phrase query and+indexed should still match.
 : New test for Lucene 10; counterpart: qrys:analyzer-phrase-stopword.
 :)
declare
    %test:assertTrue
function qrys:analyzer-phrase-stopword-match() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., '"and indexed"')]
    return exists($result) and deep-equal($result, <para>The stopwords should not be indexed.</para>)
};

(:~ Analyzer test 2: near and+indexed :)
declare
    %test:assertTrue
function qrys:analyzer-near-stopword() {
    let $qu := <query><near><term>and</term><term>indexed</term></near></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
    return deep-equal($result, <para>The stopwords should not be indexed.</para>)
};

(:~ Analyzer test 3: term "and" only -> no match :)
declare
    %test:assertEmpty
function qrys:analyzer-term-stopword-only() {
    let $qu := <query><term>and</term></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
};

(:~ Analyzer test 4: near "and" only -> no match :)
declare
    %test:assertEmpty
function qrys:analyzer-near-stopword-only() {
    let $qu := <query><near><term>and</term></near></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
};

(:~ Analyzer test 5: phrase "and" only -> no match :)
declare
    %test:assertEmpty
function qrys:analyzer-phrase-stopword-only() {
    let $qu := <query><phrase><term>and</term></phrase></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
};

(:~ Analyzer test 6: bool must and+the -> no match :)
declare
    %test:assertEmpty
function qrys:analyzer-bool-stopword-only() {
    let $qu := <query><bool><term occur="must">and</term><term occur="must">the</term></bool></query>
    return for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
};

(:~
 : Boolean with stopword: bool must stopwords+and. "and" is stopword but query requires
 : both terms; analyzer should skip "and" and match on "stopwords" only for that clause.
 : New test for Lucene 10; counterpart: qrys:analyzer-bool-with-stopword.
 :)
declare
    %test:assertTrue
function qrys:analyzer-bool-with-stopword-match() {
    let $qu := <query><bool><term occur="must">stopwords</term><term occur="must">and</term></bool></query>,
        $result := doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)]
    return exists($result) and deep-equal($result, <para>The stopwords should not be indexed.</para>)
};

(:~ Analyzer test 7: bool stopwords+and matches :)
declare
    %test:assertTrue
function qrys:analyzer-bool-with-stopword() {
    let $qu := <query><bool><term occur="must">stopwords</term><term occur="must">and</term></bool></query>,
        $result := for $hit in doc($qrys:COLLECTION || "/text1.xml")//para[ft:query(., $qu)] return $hit
    return deep-equal($result, <para>The stopwords should not be indexed.</para>)
};

(: --- Nested elements (text2) --- :)

(:~ Nested elements 1: div[ft:query(div/p, "second")] :)
declare
    %test:assertTrue
function qrys:nested-elements-1() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")//div[ft:query(div/p, "second")]
    return deep-equal($result, <div type="chapter"><head>Div1</head><p>First level</p><div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div></div>)
};

(:~ Nested elements 2: div[ft:query(div/div/p, "third")] :)
declare
    %test:assertTrue
function qrys:nested-elements-2() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")/test/div[ft:query(div/div/p, "third")]
    return deep-equal($result, <div type="chapter"><head>Div1</head><p>First level</p><div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div></div>)
};

(:~ Nested elements 3: div[ft:query(div/div/p, "second")] no match :)
declare
    %test:assertEmpty
function qrys:nested-elements-3() {
    doc($qrys:COLLECTION || "/text2.xml")/div[ft:query(div/div/p, "second")]
};

(:~ Nested elements 4: //div[ft:query(div/p, "third")] :)
declare
    %test:assertTrue
function qrys:nested-elements-4() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")//div[ft:query(div/p, "third")]
    return deep-equal($result, <div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div>)
};

(:~ Nested elements wildcard 1: div[ft:query(div/*, "third")] :)
declare
    %test:assertTrue
function qrys:nested-wildcard-1() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")//div[ft:query(div/*, "third")]
    return deep-equal($result, <div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div>)
};

(:~ Nested elements wildcard 2: test/div[ft:query(div/div/*, "third")] :)
declare
    %test:assertTrue
function qrys:nested-wildcard-2() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")/test/div[ft:query(div/div/*, "third")]
    return deep-equal($result, <div type="chapter"><head>Div1</head><p>First level</p><div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div></div>)
};

(:~ Nested elements wildcard 3: div[ft:query(div/div/*, "second")] no match :)
declare
    %test:assertEmpty
function qrys:nested-wildcard-3() {
    doc($qrys:COLLECTION || "/text2.xml")/div[ft:query(div/div/*, "second")]
};

(:~ Nested elements wildcard 4: //div[ft:query(div/*, "second")] :)
declare
    %test:assertTrue
function qrys:nested-wildcard-4() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")//div[ft:query(div/*, "second")]
    return deep-equal($result, <div type="chapter"><head>Div1</head><p>First level</p><div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div></div>)
};

(:~ Nested elements wildcard 5: //div[ft:query(div/*, "third")] :)
declare
    %test:assertTrue
function qrys:nested-wildcard-5() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")//div[ft:query(div/*, "third")]
    return deep-equal($result, <div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div>)
};

(:~ Nested elements wildcard 6: //div[ft:query(./div/*, "third")] :)
declare
    %test:assertTrue
function qrys:nested-wildcard-6() {
    let $result := doc($qrys:COLLECTION || "/text2.xml")//div[ft:query(./div/*, "third")]
    return deep-equal($result, <div type="section"><head>Div2</head><p>Second level</p><div type="subsection"><head>Div3</head><p>Third level</p></div></div>)
};

(: --- Fields (LINE, ft:query-field, keyword id) --- :)

(:~ Fields: duplicate index LINE "king" count 29 :)
declare
    %test:assertXPath("count($result) eq 29")
function qrys:fields-line-king-count() {
    doc($qrys:COLLECTION || "/macbeth.xml")//SPEECH[ft:query(LINE, "king")]
};

(:~ Query field test: lines "king" count 29 :)
declare
    %test:assertXPath("count($result) eq 29")
function qrys:query-field-lines-king() {
    doc($qrys:COLLECTION || "/macbeth.xml")//SPEECH[ft:query-field("lines", "king")]
};

(:~ Fields: descendant SCENE ft:query-field lines "king" count 10 :)
declare
    %test:assertEquals(10)
function qrys:fields-descendant-scene-count() {
    count(doc($qrys:COLLECTION || "/macbeth.xml")//SCENE[ft:query-field("lines", "king")])
};

(:~ Query field wrong parent: SPEAKER[ft:query-field("lines", "king")] -> empty :)
declare
    %test:assertEmpty
function qrys:query-field-wrong-parent() {
    doc($qrys:COLLECTION || "/macbeth.xml")//SPEAKER[ft:query-field("lines", "king")]
};

(:~ Fields: keyword analyzer id "A11" :)
declare
    %test:assertTrue
function qrys:fields-keyword-id() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")/test[ft:query-field("id", "A11")]/id
    return deep-equal($result, <id>A11</id>)
};

(:~ Fields: keyword analyzer XML syntax :)
declare
    %test:assertTrue
function qrys:fields-keyword-xml() {
    let $query := <query><term>A11</term></query>,
        $result := doc($qrys:COLLECTION || "/text1.xml")/test[ft:query-field("id", $query)]/id
    return deep-equal($result, <id>A11</id>)
};

(:~ Fields: keyword analyzer (ft:query-field "id" "A11"); doc context so index sees our docs. :)
declare
    %test:assertTrue
function qrys:fields-keyword-no-context() {
    let $result := doc($qrys:COLLECTION || "/text1.xml")/test[ft:query-field("id", "A11")]/id,
        $expected := doc($qrys:COLLECTION || "/text1.xml")/test/id[. = "A11"]
    return count($result) eq 1 and deep-equal($result, $expected)
};

(:~ Fields: remove document then query-field (previously commented out in XML). Restores text1.xml so later tests are not broken. :)
declare
    %test:pending("previously commented out; remove document then ft:query-field")
    %test:assertEmpty
function qrys:fields-remove-document() {
    xmldb:remove($qrys:COLLECTION, "text1.xml"),
    let $result := ft:query-field("id", "a11.b22.c33.d44")
    return ( xmldb:store($qrys:COLLECTION, "text1.xml", document { $qrys:TEXT1 }), $result )
};
