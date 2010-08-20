xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

let $uri := request:get-uri()
let $context := request:get-context-path()
let $install :=  request:get-parameter('install','')
let $remove :=  request:get-parameter('remove','')
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
return
    if ($path = "/repo/public/") then
	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="index.xqy"/>
    	</dispatch>
    else if ($path = "/repo/public/all/") then
	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="/repo/public/all.xqy"/>
    	</dispatch>
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
    	</ignore>