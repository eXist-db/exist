xquery version "1.0";

(: echo-post.xq: Return all data from an HTTP post to the caller. :)

declare namespace exist = "http://exist.sourceforge.net/NS/exist";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";

declare option exist:serialize "method=xml media-type=text/xml indent=yes";

let $post-data := request:get-data()

return
<post-data>
   {$post-data}
</post-data>