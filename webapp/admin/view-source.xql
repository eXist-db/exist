xquery version "1.0";

let $source := request:request-parameter("source", ())
return
    transform:transform(doc($source), "xml2html.xslt", ())
