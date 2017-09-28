xquery version "3.0";

module namespace endpoint="http://exist-db.org/exquery/restxq/multipart_related/test/endpoint";

declare namespace http = "http://expath.org/ns/http-client";
declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";

declare
    %rest:POST('{$request}')
    %rest:path("/requestTest")
    %rest:consumes("multipart/related")
    %rest:produces("application/xml")
    %output:media-type("application/xml")
function endpoint:requestTest($request){$request};

declare
    %rest:POST('{$request}')
    %rest:path("/responseTest")
    %rest:consumes("text/xml")
    %rest:produces("multipart/related")
    %output:media-type("multipart/related")
function endpoint:responsePostTest($request){$request};
