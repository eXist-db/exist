(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://exist-db.org
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
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-201  USA
 :)
xquery version "3.1";

(:~
 : Cleanroom test for Lucene 10 native English stopword support.
 :
 : Uses org.apache.lucene.analysis.en.EnglishAnalyzer directly (no custom analyzer).
 : Config: <analyzer class="org.apache.lucene.analysis.en.EnglishAnalyzer"/>
 :
 : EnglishAnalyzer: stopwords + Porter stemming. "indexed" → "index", "and" filtered.
 : This is the Lucene 10 native approach when backwards compatibility is not required.
 :
 : Contrast with EnglishStopwordsStandardAnalyzer (stopwords only, no stemming),
 : which is the stopgap for legacy behaviour.
 :)
module namespace naten="http://exist-db.org/xquery/lucene/native-english/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $naten:COLLECTION_NAME := "lucene-test-native-english";
declare variable $naten:COLLECTION := "/db/" || $naten:COLLECTION_NAME;

(:~
 : Config: EnglishAnalyzer (Lucene 10 native) – stopwords + stemming.
 :)
declare variable $naten:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.en.EnglishAnalyzer"/>
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function naten:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $naten:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $naten:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $naten:COLLECTION_NAME, "collection.xconf", $naten:XCONF),
      xmldb:store($naten:COLLECTION, "doc.xml",
          <doc>
              <p>The stopwords should not be indexed.</p>
          </doc>
      ),
      xmldb:reindex($naten:COLLECTION) )
};

declare
    %test:tearDown
function naten:tearDown() {
    ( xmldb:remove($naten:COLLECTION),
      xmldb:remove("/db/system/config/db/" || $naten:COLLECTION_NAME) )
};

(:~ Phrase "and indexed": "and" filtered, "indexed" stemmed to "index". Matches "indexed" in text. :)
declare
    %test:assertTrue
function naten:phrase-stopword() {
    let $result := doc($naten:COLLECTION || "/doc.xml")//p[ft:query(., '"and indexed"')]
    return exists($result) and $result/text() = "The stopwords should not be indexed."
};

(:~ Bool must stopwords+indexed: "and" filtered, match on "indexed" (stemmed). :)
declare
    %test:assertTrue
function naten:bool-stopword() {
    let $qu := <query><bool><term occur="must">stopwords</term><term occur="must">and</term><term occur="must">indexed</term></bool></query>,
        $result := doc($naten:COLLECTION || "/doc.xml")//p[ft:query(., $qu)]
    return exists($result)
};

(:~ Term "indexed" matches via stem "index". :)
declare
    %test:assertTrue
function naten:stemmed-term() {
    let $result := doc($naten:COLLECTION || "/doc.xml")//p[ft:query(., "indexed")]
    return exists($result)
};
