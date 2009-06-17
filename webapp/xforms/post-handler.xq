xquery version "1.0";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace system="http://exist-db.org/xquery/system";

let $action := request:get-parameter("action", ())
let $v := <xform>{request:get-parameter('postdata',())}</xform>
let $href-uri := request:get-parameter("path", ())

let $name := tokenize($href-uri, "/")[last()]
let $path := substring-before($href-uri,$name)

return
if (contains($action,'reflect')) then
    $v
else
    if(starts-with($path,'file://')) then
    (: disable file:// handling for now
            let $query := concat("file:serialize(",
									 $serialized,
								 	 ",'",
									 substring-after($href-uri,'file://'),
									 "','method=xml')")
				return
					util:eval($query) 
	:) ()
	else
	    xmldb:store($path,string($name),$v)
    
