xquery version "1.0";

declare option exist:serialize "media-type=text/xml omit-xml-declaration=no";

let $query := request:get-parameter("qu", ())
let $result := util:compile($query)
return
    if ($result) then
        <error>{$result}</error>
    else
        <ok/>