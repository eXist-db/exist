xquery version "1.0";

declare option exist:serialize "media-type=text/xml omit-xml-declaration=no";

if (doc-available("/db/XQTS/progress.xml")) then doc("/db/XQTS/progress.xml")/progress else ()