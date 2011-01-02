xquery version "1.0";

import module namespace jquery="http://exist-db.org/xquery/jquery" at "jquery.xql" (: "resource:org/exist/xquery/lib/jquery.xql" :);

(: declare option exist:serialize "method=xhtml media-type=application/xhtml+xml omit-xml-declaration=no enforce-xhtml=yes"; :)
declare option exist:serialize "method=xhtml";

(: We receive an HTML template as input :)
let $input := request:get-data()
return
    jquery:process($input)
