xquery version "3.0";

(:~
 : Tests for the XHTML5 and HTML5 serializers.
 :)
module namespace st="http://exist-db.org/test/serializer";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: HTML5 tests: output is not well-formed XML :)

declare 
    %test:assertXPath("matches($result, '^&lt;!DOCTYPE html&gt;')")
function st:html5-doctype() {
    let $html :=
        <html>
            <body></body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

declare 
    %test:assertXPath("matches($result, '.*&lt;link[^&gt;]*[^/]+&gt;')")
function st:html5-empty-link() {
    let $html :=
        <html>
            <head>
                <link rel="stylesheet" type="text/css" href="style.css"/>
            </head>
            <body></body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

declare 
    %test:assertXPath("matches($result, '.*&lt;img[^&gt;]*[^/]+&gt;')")
function st:html5-empty-img() {
    let $html :=
        <html>
            <body>
                <img src="foo.png"></img>
            </body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

declare 
    %test:assertXPath("matches($result, '.*&lt;script.*&gt;&lt;/script&gt;')")
function st:html5-non-empty-element() {
    let $html :=
        <html>
            <head>
                <script type="text/javascript" src="test.js"/>
            </head>
            <body></body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

declare 
    %test:assertXPath("matches($result, '1 &lt; 2')")
function st:html5-script-no-escape() {
    let $html :=
        <html>
            <head>
                <script type="text/javascript">1 &lt; 2</script>
            </head>
            <body></body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

declare 
    %test:assertXPath("matches($result, 'body &gt; p')")
function st:html5-style-no-escape() {
    let $html :=
        <html>
            <head>
                <style type="text/css">
                    body &gt; p {{ color: red; }}
                </style>
            </head>
            <body></body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

declare 
    %test:assertXPath("matches($result, 'checked[^=]')")
function st:html5-empty-attribute() {
    let $html :=
        <html>
            <body>
                <input type="checkbox" checked="checked"/>
            </body>
        </html>
    return
        util:serialize($html, "method=html5 indent=no")
};

(:  XHTML5 tests :)

(:~
 : XHTML5 serializer must produce well-formed XML
 :)
declare 
    %test:assertExists
function st:xhtml5() {
    let $html :=
        <html>
            <head>
                <script type="text/javascript">1 &lt; 2</script>
                <style type="text/css">
                    body &gt; p {{ color: red; }}
                </style>
            </head>
            <body>
                <img src="foo.png"></img>
                <input type="checkbox" checked="checked"/>
            </body>
        </html>
    let $str := util:serialize($html, "method=xhtml5 indent=no")
    return
        parse-xml($str)
};

declare 
    %test:assertXPath("matches($result, '.*&lt;link.*/&gt;')")
function st:xhtml5-empty-link() {
    let $html :=
        <html>
            <head>
                <link rel="stylesheet" type="text/css" href="style.css"/>
            </head>
            <body></body>
        </html>
    return
        util:serialize($html, "method=xhtml5 indent=no")
};

declare 
    %test:assertXPath("matches($result, '.*&lt;img.*/&gt;')")
function st:xhtml5-empty-img() {
    let $html :=
        <html>
            <body>
                <img src="foo.png"></img>
            </body>
        </html>
    return
        util:serialize($html, "method=xhtml5 indent=no")
};

declare 
    %test:assertXPath("matches($result, '.*&lt;script.*&gt;&lt;/script&gt;')")
function st:xhtml5-non-empty-element() {
    let $html :=
        <html>
            <head>
                <script type="text/javascript" src="test.js"/>
            </head>
            <body></body>
        </html>
    return
        util:serialize($html, "method=xhtml5 indent=no")
};