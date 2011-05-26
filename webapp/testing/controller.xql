xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

let $uri := request:get-uri()
let $context := request:get-context-path()
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
return
    if ($path = "/xproc/") then
	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<redirect url="examples.xml"/>
    	</dispatch>
    	
	else if (ends-with($uri, '.xproc')) then
	    let $docName := replace($uri, '^.*/([^/]+)$', '$1')
	    return
    	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    			<forward url="/rest/db/xproc/examples/{$docName}">
    				<!--add-parameter name="xproc" value="/db/xproc/examples/{$docName}"/-->
    			</forward>
    		</dispatch>
    		
    else if (ends-with($uri, '.xml')) then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.stylesheet" value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
				    <set-attribute name="xslt.output.media-type" value="text/html"/>
                	<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
                	<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
                    <set-attribute name="xslt.root" value="{request:get-context-path()}{$exist:prefix}"/>
				    <set-attribute name="xslt.base" value="{$exist:root}"/>
				</forward>
			</view>
            <cache-control cache="no"/>
		</dispatch>
		
    else if (matches($exist:path, '(resources/|styles/syntax|scripts/syntax/|logo.jpg|default-style2.css|curvycorners.js)')) then
    let $newPath := replace($exist:path, '^.*((resources/|styles/|scripts/|logo).*)$', '/$1')
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$newPath}"/>
    		<cache-control cache="yes"/>
    	</dispatch>
    	
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
    	</ignore>