xquery version "3.0";

module namespace luct="http://exist-db.org/xquery/lucene/test";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $luct:INDEX_DATA :=
    <doc>
        <field name="title">Lorem ipsum dolor</field>
    </doc>;

declare variable $luct:INDEX_DATA_SUBCOLL :=
    <doc>
        <field name="title">Only admin can read this</field>
    </doc>;

declare variable $luct:XCONF :=
     <collection xmlns="http://exist-db.org/collection-config/1.0">
         <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
             <lucene>
                 <text qname="foo"/>
             </lucene>
         </index>
     </collection>;

declare variable $luct:DOCUMENT_INDEX_DATA :=
    <doc>
        <field name="title">Different index data</field>
    </doc>;

declare variable $luct:DOCUMENT_DATA :=
     <text>
         <foo/>
     </text>;

declare
    %test:setUp
function luct:setup() {
    xmldb:create-collection("/db", "lucenetest"),
    xmldb:store("/db/lucenetest", "test.txt", "Lorem ipsum", "text/text"),
    sm:chmod(xs:anyURI("/db/lucenetest/test.txt"), "rw-------"),
    ft:index("/db/lucenetest/test.txt", $luct:INDEX_DATA),
    
    xmldb:create-collection("/db/lucenetest", "sub"),
    sm:chmod(xs:anyURI("/db/lucenetest/sub"), "rwx------"),
    xmldb:store("/db/lucenetest/sub", "test.txt", "Lorem ipsum", "text/text"),
    sm:chmod(xs:anyURI("/db/lucenetest/sub/test.txt"), "rw-rw-rw-"),
    ft:index("/db/lucenetest/sub/test.txt", $luct:INDEX_DATA_SUBCOLL),

    xmldb:create-collection("/db/system/config/db", "lucenetest"),
    xmldb:store('/db/system/config/db/lucenetest', "collection.xconf", $luct:XCONF)
};

declare
    %test:tearDown
function luct:cleanup() {
    xmldb:remove("/db/lucenetest")
};

declare
    %test:assertEmpty
function luct:check-visibility-fail() {
    system:as-user("guest", "guest", 
        ft:search("/db/lucenetest/", "title:ipsum")/search/@uri/string()
    )
};

declare
    %test:assertEquals("/db/lucenetest/test.txt")
function luct:check-visibility-pass() {
    system:as-user("admin", "", 
        ft:search("/db/lucenetest/", "title:ipsum")/search/@uri/string()
    )
};

declare
    %test:assertEmpty
function luct:check-visibility-collection-fail() {
    system:as-user("guest", "guest", 
        ft:search("/db/lucenetest/sub/", "title:admin")/search/@uri/string()
    )
};

declare
    %test:assertEquals("/db/lucenetest/sub/test.txt")
function luct:check-visibility-collection-pass() {
    system:as-user("admin", "", 
        ft:search("/db/lucenetest/sub/", "title:admin")/search/@uri/string()
    )
};

declare
      %test:assertEquals("/db/lucenetest/indexedDocument.xml")
  function luct:check-ft-search-on-index() {
      let $store :=  system:as-user("admin", "",
            xmldb:store("/db/lucenetest", 'indexedDocument.xml', $luct:DOCUMENT_DATA)
          )
      let $index := system:as-user("admin", "",
            ft:index("/db/lucenetest/indexedDocument.xml", $luct:DOCUMENT_INDEX_DATA)
          )
      return
          system:as-user("admin", "",
              ft:search("/db/lucenetest/", "title:different")/search/@uri/string()
          )
};

declare
    %test:assertEquals("Different index data")
function luct:check-ft-get-field-on-index() {
    let $store :=  system:as-user("admin", "",
            xmldb:store("/db/lucenetest", 'indexedDocument2.xml', $luct:DOCUMENT_DATA)
        )
    let $index := system:as-user("admin", "",
            ft:index("/db/lucenetest/indexedDocument2.xml", $luct:DOCUMENT_INDEX_DATA)
        )
    return
        system:as-user("admin", "",
            ft:get-field("/db/lucenetest/indexedDocument2.xml", "title")
        )
};
