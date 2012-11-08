xquery version "1.0";
(: $Id$ :)

declare namespace request="http://exist-db.org/xquery/request";

let $source := request:get-parameter("source", ())
return
    transform:transform(doc($source), "xml2html.xslt", ())
