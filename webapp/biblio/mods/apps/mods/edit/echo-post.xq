xquery version "1.0";
declare option exist:serialize "method=xml media-type=text/xml omit-xml-declaration=no indent=yes";

(: XQuery script to echo an incomming HTTP POST :)

let $item := request:get-data()

return
<post-results>
   {$item}
</post-results>