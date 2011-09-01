xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

let $name as xs:string := request:get-parameter('name', 'noname')
let $path := request:get-parameter('path', '')
let $collection := concat('/db/',$path)

(: make sure you use the right user permissions that has write access to this collection :)
let $create := xmldb:create-collection($collection, $name)
return
<html>
    <body>
        <textarea>
        {
            if ($create)
            then (
                <ok/>
             ) else (
                <failed/>
             )
            }
        </textarea>
    </body>
</html>