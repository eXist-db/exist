xquery version "3.0";

(:~
 : Some tests on features of the test suite itself.
 :)
module namespace t="http://exist-db.org/xquery/test/xqsuite";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare 
    %test:assertXPath("/name[. = 'Item1']")
function t:xpath() {
    <item>
        <name>Item1</name>
    </item>
};

declare 
    %test:assertXPath("/t:name[. = 'Item1']")
function t:xpath-with-namespace() {
    <t:item>
        <t:name>Item1</t:name>
    </t:item>
};

declare 
    %test:assertXPath("/t:name/x:id[. = 'abc']")
function t:xpath-with-different-namespaces() {
    <t:item>
        <t:name><x:id xmlns:x="http://test.com/x">abc</x:id></t:name>
    </t:item>
};

declare 
    %test:assertXPath("declare namespace f='http://foo.com'; $result//f:name[. = 'Item1']")
function t:xpath-with-default-namespace() {
    <item xmlns="http://foo.com">
        <name>Item1</name>
    </item>
};

declare 
    %test:assertXPath("string-length($result) = 5")
function t:xpath-atomic-value() {
    "Hello"
};