xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

if($exist:path eq "/")then(
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        
        <forward servlet="JMXServlet"/>
    
    	<view>
    	
    		<forward servlet="XSLTServlet">
    			<set-attribute name="xslt.stylesheet" value="{$exist:root}/xquery/status/status.xslt"/>
    			<set-attribute name="xslt.output.media-type" value="text/html"/>
    			<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
    			<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
    		</forward>
    		
    		<forward servlet="XSLTServlet">
    			<set-attribute name="xslt.stylesheet" value="{$exist:root}/stylesheets/db2xhtml.xsl"/>
    			<set-attribute name="xslt.output.media-type" value="text/html"/>
    			<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
    			<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
    		</forward>
    	</view>
    	<cache-control cache="no"/>
    </dispatch>
) else if (matches($exist:path, '(styles/syntax|scripts/syntax/|logo.jpg|default-style2.css|curvycorners.js)')) then (
    let $newPath := replace($exist:path, '^.*((styles/|scripts/|logo).*)$', '/$1') return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$newPath}"/>
    		<cache-control cache="yes"/>
    	</dispatch>
) else (
    <ignore xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
	</ignore>
)