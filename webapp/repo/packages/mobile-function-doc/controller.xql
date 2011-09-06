xquery version "1.0";

(: $Id: controller.xql 13019 2010-10-28 03:41:35Z joewiz $ :)

(:~
	Controller XQuery for the RESTful function docs application.
	
	Demonstrates how to use in URL Rewriting for RESTful applications, e.g.:
	   - functions/ forwards transparently to functions/functions.xql
	   - functions/xmldb displays the docs for the xmldb module
	   - functions/xmldb/login displays the docs for the xmldb:login() module
	   - functions/xmldb:login() redirects to functions/xmldb/login 
	       in order to display the docs for the xmldb:login() module
	       
    Un-comment logging functions and monitor EXIST_HOME/webapp/WEB-INF/logs/exist.log
    to follow the construction of these parameters.
:)

import module namespace request="http://exist-db.org/xquery/request";

(: standard variables passed automatically to controller.xql  :)

declare variable $exist:root external;
declare variable $exist:prefix external;
declare variable $exist:controller external;
declare variable $exist:path external;
declare variable $exist:resource external;

(: variables from the HTTP Request module :)
let $url := request:get-url()
let $uri := request:get-uri()
let $context := request:get-context-path()
let $effective-uri := request:get-effective-uri()

(: the default query for this app:)
let $app-default-query := 'index.xql'

(: used to forward matching requests to the correct 'internal' path to the main query :)
let $internal-path-to-app := concat($exist:controller, '/', $app-default-query)

(: the 'external' path to app to the for use in constructing URLs  :)
let $external-path-to-app := concat($context, $exist:prefix, $exist:controller, '/')

(: prepare the URL parameters for use :)
let $post-query-url-params := subsequence(tokenize($exist:path, '/'), 2)

(: logging to demonstrate how variables are constructed for use in URL Rewriting :)
(:
let $log := util:log("DEBUG", concat("URL Info: request:get-url():           ", $url))
let $log := util:log("DEBUG", concat("URL Info: request:get-uri():           ", $uri))
let $log := util:log("DEBUG", concat("URL Info: request:get-effective-uri(): ", $effective-uri))
let $log := util:log("DEBUG", concat("URL Info: request:get-context-path():  ", $context))

let $log := util:log("DEBUG", concat("URL Info: $exist:root:                 ", $exist:root))
let $log := util:log("DEBUG", concat("URL Info: $exist:prefix:               ", $exist:prefix))
let $log := util:log("DEBUG", concat("URL Info: $exist:controller:           ", $exist:controller))
let $log := util:log("DEBUG", concat("URL Info: $exist:path:                 ", $exist:path))
let $log := util:log("DEBUG", concat("URL Info: $exist:resource:             ", $exist:resource))

let $log := util:log("DEBUG", concat("URL Info: $internal-path-to-app:       ", $internal-path-to-app))
let $log := util:log("DEBUG", concat("URL Info: $external-path-to-app:       ", $external-path-to-app))
:)

return
    (: redirect requests of ~/mobiledocs to ~/mobiledocs/ :)
    if ($exist:path eq '') then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            <redirect url="{$external-path-to-app}"/>
            <cache-control cache="no"/>
        </dispatch>
    (: make sure the global css, js and image files are resolved :)
    else if (matches($exist:path, '((styles|scripts|resources)/|logo.jpg)')) then
        let $newPath := replace($exist:path, '^.*(((styles|scripts|resources)/|logo).*)$', '/$1')
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="{$newPath}"/>
                <cache-control cache="yes"/>
            </dispatch>
    else
        let $module := $post-query-url-params[1]
        let $function := $post-query-url-params[2]
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                <forward url="{$internal-path-to-app}">
                    <add-parameter name="module" value="{$module}"/>
                    <add-parameter name="function" value="{$function}"/>
                </forward>
            </dispatch>