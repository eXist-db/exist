xquery version "1.0";

(:~
	Controller XQuery for the RESTful function docs application.
:)

import module namespace request="http://exist-db.org/xquery/request";

(: Standard controller.xql variables :)

declare variable $exist:controller external;
declare variable $exist:path external;
declare variable $exist:resource external;
declare variable $exist:root external;

let $app-default-query := 'functions.xql'
let $internal-path-to-app := concat($exist:controller, '/', $app-default-query)

let $context := request:get-context-path()
let $external-path-to-app := concat($context, $exist:controller, '/')

let $uri := request:get-uri()
let $post-query-url-params := subsequence(tokenize(substring-after(request:get-uri(), $exist:controller), '/'), 2)

(: Logging for sanity :)
let $log := util:log("DEBUG", concat("URL Rewriter: $exist:root:                ", $exist:root))
let $log := util:log("DEBUG", concat("URL Rewriter: request:get-context-path(): ", $context))
let $log := util:log("DEBUG", concat("URL Rewriter: $app-default-query:         ", $app-default-query))
let $log := util:log("DEBUG", concat("URL Rewriter: $internal-path-to-app:      ", $internal-path-to-app))

let $log := util:log("DEBUG", concat("URL Rewriter: $exist:controller:          ", $exist:controller))
let $log := util:log("DEBUG", concat("URL Rewriter: $external-path-to-app:      ", $external-path-to-app))

let $log := util:log("DEBUG", concat("URL Rewriter: $exist:path:                ", $exist:path))
let $log := util:log("DEBUG", concat("URL Rewriter: $exist:resource:            ", $exist:resource))
let $log := util:log("DEBUG", concat("URL Rewriter: request:get-uri():          ", $uri))


return
	(: App root path: forward to default query :)
	if ($exist:path eq '/') then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$internal-path-to-app}"/>
    	</dispatch>
	
	(: Execute RESTful queries by passing URL parameters to the query :)
    else
        let $module := $post-query-url-params[1]
        let $function := $post-query-url-params[2]
        
        let $log := util:log('DEBUG', concat('Functions App module:   ', $module))
        let $log := util:log('DEBUG', concat('Functions App function: ', $function))
        
        return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{$internal-path-to-app}">
    			<add-parameter name="basepath" value="{$external-path-to-app}"/>
        	    <add-parameter name="module" value="{$module}"/>,
        	    <add-parameter name="function" value="{$function}"/>
			</forward>
		</dispatch>