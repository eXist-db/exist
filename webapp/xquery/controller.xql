xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

if ($exist:path = "/") then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="../examples.xml"/>
	</dispatch>
else if ($exist:resource = ('search.xql', 'functions.xql', 'svnlog.xql')) then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<!-- query results are passed to XSLT servlet via request attribute -->
		<set-attribute name="xquery.attribute"
			value="model"/>
		<view>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.input"
					value="model"/>
				<set-attribute name="xslt.stylesheet" 
					value="/stylesheets/db2xhtml.xsl"/>
				<set-attribute name="xslt.output.media-type"
				        value="text/html"/>
				<set-attribute name="xslt.output.doctype-public"
				    value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
				<set-attribute name="xslt.output.doctype-system"
				    value="resources/xhtml1-transitional.dtd"/>
			</forward>
		</view>
		<cache-control cache="no"/>
	</dispatch>
else if ($exist:resource eq 'acronyms.xql') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<!-- query results are passed to XSLT servlet via request attribute -->
		<set-attribute name="xquery.attribute"
			value="model"/>
		<view>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.input"
					value="model"/>
				<set-attribute name="xslt.stylesheet"
					value="stylesheets/acronyms.xsl"/>
			</forward>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.input"
					value=""/>
				<set-attribute name="xslt.stylesheet" 
					value="/stylesheets/db2xhtml.xsl"/>
				<set-attribute name="xslt.output.media-type"
				        value="text/html"/>
				<set-attribute name="xslt.output.doctype-public"
				    value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
				<set-attribute name="xslt.output.doctype-system"
				    value="resources/xhtml1-transitional.dtd"/>
			</forward>
		</view>
	</dispatch>
else if ($exist:resource = 'biblio.xml') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<view>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.stylesheet"
					value="stylesheets/doc2html-2.xsl"/>
			</forward>
		</view>
		<cache-control cache="yes"/>
	</dispatch>
else if ($exist:resource eq 'biblio.xql') then
	let $display := request:get-parameter("display", "overview")
	let $xsl := 
		if ($display eq "details") then "detailed.xsl" else "overview.xsl"
	return
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<set-attribute name="xquery.attribute" value="model"/>
			<view>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.input"
					value="model"/>
				<set-attribute name="xslt.stylesheet" 
					value="xquery/stylesheets/{$xsl}"/>
			</forward>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.input"
					value=""/>
				<set-attribute name="xslt.stylesheet" 
					value="stylesheets/doc2html-2.xsl"/>
			</forward>

			</view>
		</dispatch>
else if ($exist:resource eq 'twitter.xql') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
       <forward url="twitter.xql"/>
        <view>
            <forward url="twitter-view.xql"/>
        </view>
    </dispatch>
else if ($exist:resource = ('default-style.css', 'default-style2.css', 'curvycorners.js')) then
    let $newPath := replace($exist:path, '^.*/([^/]+/[^/]+)$', '/$1')
    return
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="{$newPath}"/>
		<cache-control cache="yes"/>
	</dispatch>
else if (matches($exist:path, 'syntax/.*\.(css|js)')) then
    let $newPath := replace($exist:path, '^.*/([^/]+/syntax/[^/]+)$', '/$1')
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$newPath}"/>
    		<cache-control cache="yes"/>
    	</dispatch>
else if (ends-with($exist:path, '/dump')) then
	let $newPath := substring-before($exist:path, '/dump')
	return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{$exist:controller}{$newPath}"/>
			<view>
				<forward url="/xquery/dump.xql">
				   <set-attribute name="controller" value="{$exist:controller}"/>
				   <set-attribute name="path" value="{$exist:path}"/>
				   <set-attribute name="resource" value="{$exist:resource}"/>
			    </forward>
			</view>
		</dispatch>
else
    <ignore xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
	</ignore>