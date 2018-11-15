xquery version "3.1";

(:~ Additional tests for the fn:count function :)
module namespace xid="http://exist-db.org/xquery/test/xmlid";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $xid:COLLECTION_NAME := "test-xmlid";

declare variable $xid:XML :=
    ``[<test>
        <item xml:id="nym_Ͷαναξιμοῦς"/>
        <item xml:id="123"/>
    </test>]``;

declare
%test:setUp
function xid:setup() {
    xmldb:create-collection("/db", $xid:COLLECTION_NAME)
};

declare
%test:tearDown
function xid:cleanup() {
    xmldb:remove($xid:COLLECTION_NAME)
};

declare
    %test:assertEquals("<item xml:id='nym_Ͷαναξιμοῦς'/>")
function xid:stored-xml() {
    xmldb:store($xid:COLLECTION_NAME, "test.xml", $xid:XML)[2],
    doc($xid:COLLECTION_NAME || "/test.xml")/id("nym_Ͷαναξιμοῦς"),
    (: not a valid ncname and thus ignored, c.f. https://www.w3.org/TR/xpath-functions-31/#func-id :)
    doc($xid:COLLECTION_NAME || "/test.xml")/id("123")
};

declare
%test:assertEquals("<item xml:id='nym_Ͷαναξιμοῦς'/>")
function xid:parse-xml() {
    let $test := fn:parse-xml($xid:XML)
    return (
        $test/id("nym_Ͷαναξιμοῦς"),
        (: not a valid ncname and thus ignored, c.f. https://www.w3.org/TR/xpath-functions-31/#func-id :)
        $test/id("123")
    )

};

declare
    %test:assertEquals("<item xml:id='nym_Ͷαναξιμοῦς'/>")
function xid:constructed-xml() {
    let $test :=
        <test>
            <item xml:id="nym_Ͷαναξιμοῦς"/>
        </test>
    return (
        $test/id("nym_Ͷαναξιμοῦς"),
        (: not a valid ncname and thus ignored, c.f. https://www.w3.org/TR/xpath-functions-31/#func-id :)
        $test/id("123")
    )
};

declare
%test:assertEquals("<item xml:id='nym_Ͷαναξιμοῦς'/>")
function xid:constructed-attribute() {
    let $test :=
        <test>
            <item>
                { attribute xml:id { "nym_Ͷαναξιμοῦς"} }
            </item>
        </test>
    return (
        $test/id("nym_Ͷαναξιμοῦς")
    )
};
