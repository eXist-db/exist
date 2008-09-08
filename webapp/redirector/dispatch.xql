(: This script is called from the XQueryURLRewrite filter configured in web.xml. :)
xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";

let $uri := request:get-uri()
let $servlet := request:get-servlet-path()
let $extraPath := replace($servlet, "^/[^/]+/(.*)$", "$1")
let $context := request:get-context-path()
let $logout := request:get-parameter('logout', ())
let $log := util:log("DEBUG", ("$uri=", $uri, " $servlet=", $servlet, " $context=", $context))
return
    (: Issue a redirect if parameter $logout is set :)
    if ($logout) then
        <exist:dispatch>
            <exist:redirect url="{$context}/index.xml"/>
            <exist:cache-control cache="yes"/>
        </exist:dispatch>
    else
        <exist:dispatch>
            <exist:forward url="/redirector/index.xql"/>
            <exist:add-parameter name="dummy" value="Param added by dispatch.xql"/>
            <exist:add-parameter name="extra-path" value="{$extraPath}"/>
        </exist:dispatch>
