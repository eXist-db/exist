xquery version "3.1";

module namespace ct="http://exist-db.org/xquery/test/content-extraction";

declare namespace contentextraction="http://exist-db.org/xquery/contentextraction";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace html="http://www.w3.org/1999/xhtml";

(: minimal PDF taken from https://brendanzagaeski.appspot.com/0004.html :)
declare variable $ct:PDF := "%PDF-1.1
%¥±ë

1 0 obj
  << /Type /Catalog
     /Pages 2 0 R
  >>
endobj

2 0 obj
  << /Type /Pages
     /Kids [3 0 R]
     /Count 1
     /MediaBox [0 0 300 144]
  >>
endobj

3 0 obj
  <<  /Type /Page
      /Parent 2 0 R
      /Resources
       << /Font
           << /F1
               << /Type /Font
                  /Subtype /Type1
                  /BaseFont /Times-Roman
               >>
           >>
       >>
      /Contents 4 0 R
  >>
endobj

4 0 obj
  << /Length 55 >>
stream
  BT
    /F1 18 Tf
    0 0 Td
    (Hello World) Tj
  ET
endstream
endobj

xref
0 5
0000000000 65535 f
0000000018 00000 n
0000000077 00000 n
0000000178 00000 n
0000000457 00000 n
trailer
  <<  /Root 1 0 R
      /Size 5
  >>
startxref
565
%%EOF" => util:string-to-binary();

declare
    %test:setUp
function ct:setup() {
    let $testCol := xmldb:create-collection("/db", "test")
    return
        xmldb:store("/db/test", "test.pdf", $ct:PDF)
};

declare
    %test:tearDown
function ct:tearDown() {
    xmldb:remove("/db/test")
};

declare
    %test:assertTrue
function ct:exists-in-db() {
    let $pdf-path := "/db/test/test.pdf"
    return
        if (util:binary-doc-available($pdf-path)) then
            let $pdf := util:binary-doc($pdf-path)
            return
                $pdf instance of xs:base64Binary
        else
            false()
};

declare
    %test:assertEquals("1")
function ct:read-metadata-from-db() {
    (
        util:binary-doc("/db/test/test.pdf")
        => contentextraction:get-metadata()
    )//html:meta[@name eq "xmpTPg:NPages"]/@content/string()
};

declare
    %test:assertEquals("Hello World")
function ct:read-content-and-metadata-from-db() {
    (
        util:binary-doc("/db/test/test.pdf")
        => contentextraction:get-metadata-and-content()
    )//html:p[2]/string()
};