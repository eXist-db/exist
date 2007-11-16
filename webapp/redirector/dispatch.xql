(: This script is called from the RedirectorServlet configured in web.xml. :)
xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";

let $uri := request:get-uri()
let $path := request:get-path-info()
let $context := request:get-context-path()
let $logout := request:get-parameter('logout', ())
return
    (: Issue a redirect if parameter $logout is set :)
    if ($logout) then
        <exist:dispatch redirect="{$context}/index.xml"/>
    
    (: If the request path starts with /search, redirect to index.xql. :)
       
    else if (starts-with($path, "/search")) then
        (: Extract the path component after /search. It will be passed as a parameter :)
        let $collection := substring-after($path, "/search")
        return
            <exist:dispatch path="/redirector/index.xql">
                <exist:add-parameter name="collection" value="{$collection}"/>
                <exist:add-parameter name="dummy" value="Param added by dispatch.xql"/>
            </exist:dispatch>
    else
        ()