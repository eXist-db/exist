xquery version "3.1";

module namespace path="http://exist-db.org/xquery/path";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $path:DOCUMENT_NODE := document {
            <p xmlns="http://example.com/one" xml:lang="de" author="Friedrich von Schiller">
            Freude, schöner Götterfunken,<br/>
            Tochter aus Elysium,<br/>
            Wir betreten feuertrunken,<br/>
            Himmlische, dein Heiligtum.</p>
            };

declare variable $path:ELEMENT_NODE := <employee xml:id="ID21256">
               <empnr>E21256</empnr>
               <first>John</first>
               <last>Brown</last>
            </employee>;

declare variable $path:COMMENTS :=
    <div>
        <!-- hallo -->
        <!-- hello -->
        <!-- ollah -->
    </div>;

declare variable $path:PI :=
    <div>
        <?xml-stylesheet type="text/xsl" href="style.xsl"?>
        <?foo bar="true" snafu="false"?>
        <?xml-stylesheet type="text/css" href="style.css"?>
    </div>;


(: in memory document node tests as defined in spec :)

declare
  %test:assertEquals('/')
  function path:in-memory-document-node() {
    fn:path($path:DOCUMENT_NODE)
};

declare
  %test:assertEquals('/Q{http://example.com/one}p[1]')
  function path:in-memory-document-element() {
    fn:path($path:DOCUMENT_NODE/*:p)
};

declare
  %test:assertEquals('/Q{http://example.com/one}p[1]/@Q{http://www.w3.org/XML/1998/namespace}lang')
  function path:in-memory-document-lang() {
    fn:path($path:DOCUMENT_NODE/*:p/@xml:lang)
};

declare
  %test:assertEquals('/Q{http://example.com/one}p[1]/@author')
  function path:in-memory-document-attribute() {
    fn:path($path:DOCUMENT_NODE/*:p/@author)
};

declare
  %test:assertEquals('/Q{http://example.com/one}p[1]/Q{http://example.com/one}br[2]')
  function path:in-memory-document-nested-element() {
    fn:path($path:DOCUMENT_NODE/*:p/*:br[2])
};

declare
  %test:assertEquals('/Q{http://example.com/one}p[1]/text()[2]')
  function path:in-memory-document-text-node() {
    fn:path($path:DOCUMENT_NODE//text()[starts-with(normalize-space(), 'Tochter')])
};

(: in memory element tests as defined in spec :)

declare
  %test:assertEquals('Q{http://www.w3.org/2005/xpath-functions}root()')
  function path:root-node() {
    fn:path($path:ELEMENT_NODE)
};

declare
  %test:assertEquals('Q{http://www.w3.org/2005/xpath-functions}root()/@Q{http://www.w3.org/XML/1998/namespace}id')
  function path:root-node-xml-id() {
    fn:path($path:ELEMENT_NODE/@xml:id)
};

declare
  %test:assertEquals('Q{http://www.w3.org/2005/xpath-functions}root()/Q{}empnr[1]')
  function path:root-node-element() {
    fn:path($path:ELEMENT_NODE/empnr)
};

(: Additional tests :)

declare
  %test:assertEquals('Q{http://www.w3.org/2005/xpath-functions}root()/comment()[3]')
  function path:comments() {
    fn:path($path:COMMENTS//comment()[contains(.,"ollah")])
};

declare
  %test:assertEquals('Q{http://www.w3.org/2005/xpath-functions}root()/processing-instruction(foo)[1]')
  function path:processing-instructions() {
    fn:path($path:PI//processing-instruction()[contains(.,"bar")])
};

(: ToDo namespace node :)


(: Error handling according to spec :)
declare
  %test:assertError("XPDY0002")
  function path:empty-context() {
    fn:path()
};

declare
    %test:assertError("err:XPTY0004")
function path:no-node-context() {
     util:eval-with-context("path()", (), false(), "a")
};

