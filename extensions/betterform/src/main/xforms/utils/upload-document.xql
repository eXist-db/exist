xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

let $filename := request:get-uploaded-file-name('file')
let $path := request:get-parameter('path', '')
let $collection := concat('/db/',$path)

let $store := xmldb:store($collection, $filename, request:get-uploaded-file-data('file'), 'application/octet-stream')

return
<html>
    <body>
        <textarea>
        {
            if ($store)
             then (
                <ok/>
             ) else (
                <failed/>
             )
        }
        </textarea>
    </body>
</html>