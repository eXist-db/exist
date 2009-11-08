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

let $pathSep := util:system-property("file.separator")

let $app-default-query := 'functions.xql'
let $internal-path-to-app := concat($exist:controller, '/', $app-default-query)

let $context := request:get-context-path()
let $external-path-to-app := concat($context, $exist:controller, '/')

let $uri := request:get-uri()
let $post-query-url-params := subsequence(tokenize($exist:path, '/'), 2)

(: Logging to demonstrate how variables are constructed for use in URL Rewriting :)
let $log := util:log("DEBUG", concat("URL Info: $exist:root:                ", $exist:root))
let $log := util:log("DEBUG", concat("URL Info: request:get-context-path(): ", $context))
let $log := util:log("DEBUG", concat("URL Info: $app-default-query:         ", $app-default-query))
let $log := util:log("DEBUG", concat("URL Info: $internal-path-to-app:      ", $internal-path-to-app))

let $log := util:log("DEBUG", concat("URL Info: $exist:controller:          ", $exist:controller))
let $log := util:log("DEBUG", concat("URL Info: $external-path-to-app:      ", $external-path-to-app))

let $log := util:log("DEBUG", concat("URL Info: $exist:path:                ", $exist:path))
let $log := util:log("DEBUG", concat("URL Info: $exist:resource:            ", $exist:resource))
let $log := util:log("DEBUG", concat("URL Info: request:get-uri():          ", $uri))


return
	(: make sure the global css, js and image files are resolved :)
    if (matches($exist:path, '((styles|scripts|resources)/|logo.jpg)')) then
        let $newPath := replace($exist:path, '^.*(((styles|scripts|resources)/|logo).*)$', '/$1')
        return
            <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    			<forward url="{$newPath}"/>
    			<cache-control cache="yes"/>
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
            	    <!-- query results are passed to XSLT servlet via request attribute -->
              		<set-attribute name="xquery.attribute"
              			value="model"/>
    			</forward>
          		<view>
          		    <!-- Fix sidebar links -->
          		    <forward url="{$exist:controller}/filter.xql">
          		        <set-attribute name="xquery.attribute"
              			   value="model"/>
              		   <set-attribute name="base" value="{$context}"/>
              	    </forward>
              	    <!-- Apply db2xhtml stylesheet -->
          			<forward servlet="XSLTServlet">
          				<set-attribute name="xslt.input"
          					value="model"/>
          			    <set-attribute name="xslt.base"
          			        value="{$exist:root}{$exist:controller}"/>
          				<set-attribute name="xslt.stylesheet" 
          					value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
          				<set-attribute name="xslt.output.media-type"
          				        value="text/html"/>
          				<set-attribute name="xslt.output.doctype-public"
          				    value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
          				<set-attribute name="xslt.output.doctype-system"
          				    value="resources/xhtml1-transitional.dtd"/>
          			</forward>
          		</view>
    		</dispatch>