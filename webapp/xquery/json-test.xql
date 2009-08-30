xquery version "1.0";

import module namespace json="http://www.json.org";

declare option exist:serialize "method=xhtml media-type=text/html";

let $xml1 :=
    <root>
        <h1>H</h1>
        <p/>
        <p>some text</p>
        <h2>HH</h2>
    </root>
let $xml2 :=
    <root>
        <a id="a1"/>
        <b id="b1" type="t"/>
        <c id="c1">text</c>
        <d id="d1"><e>text</e></d>
    </root>
let $xml3 :=
    <root>
        <p>Some <b>mixed</b> text.</p>
    </root>
let $xml4 :=
    <root>Single</root>
return
    <html xmlns="http://www.w3.org/1999/xhtml">
      <head>
        <title>XML 2 JSON Test</title>
          <!-- Dependencies -->
        <script type="text/javascript" src="http://yui.yahooapis.com/2.3.1/build/yahoo-dom-event/yahoo-dom-event.js"></script>
        <script type="text/javascript" src="http://yui.yahooapis.com/2.3.1/build/logger/logger-min.js"></script>
        <!-- Source File -->
        <script type="text/javascript" src="http://yui.yahooapis.com/2.3.1/build/yuitest/yuitest-beta-min.js"></script>
        <script type="text/javascript" src="json-test.js"></script>
      </head>
      <body>
        <h1>Running XML2JSON Tests</h1>
    
        <script type="text/javascript">
            var data1 = {json:xml-to-json($xml1)};
            var data2 = {json:xml-to-json($xml2)};
            var data3 = {json:xml-to-json($xml3)};
            var data4 = {json:xml-to-json($xml4)};
        </script>
      </body>
    </html>