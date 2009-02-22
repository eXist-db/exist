(: 	This is the main controller for the web application. It is called from the
	XQueryURLRewrite filter configured in web.xml. :)
xquery version "1.0";

declare namespace c="http://exist-db.org/xquery/controller";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

declare function c:admin($path as xs:string, $name as xs:string) {
    if (starts-with($path, "/admin/backups")) then
        let $user := xdb:get-current-user()
        return
            if (not(xdb:is-admin-user($user))) then
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        			<redirect url="../admin.xql"/>
        		</dispatch>
        	else
                <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
                    <forward url="/admin/retrieve-backup.xql">
                        <add-parameter name="archive" value="{$name}"/>
                    </forward>
                </dispatch>
    else if (matches($path, "admin/?$")) then
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="admin.xql"/>
		</dispatch>
    else if ($name eq "admin.xql") then
	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
	        <forward url="/admin/admin.xql"/>
	    </dispatch>
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>
};

let $uri := request:get-uri()
let $context := request:get-context-path()
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
let $query := request:get-parameter("q", ())
return
	(: redirect webapp root to index.xml :)
    if ($path eq '/') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="index.xml"/>
		</dispatch>
	(: /rest and /webdav will be ignored :)
	else if (matches($path, "/(rest|webdav|cocoon|test.xml)")) then
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>
	(: redirect sandbox root to sandbox.xql :)
	else if (matches($uri, "sandbox/?$")) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="sandbox.xql"/>
		</dispatch>
	else if (starts-with($path, "/admin")) then
	    c:admin($path, $name)
	else if ($name = ('search.xql', 'functions.xql', 'svnlog.xql')) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{$path}">
				<!-- query results are passed to XSLT servlet via request attribute -->
				<set-attribute name="xquery.attribute"
					value="xslt.model"/>
			</forward>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="xslt.model"/>
					<set-attribute name="xslt.stylesheet" 
						value="stylesheets/db2html.xsl"/>
				</forward>
			</view>
		</dispatch>
	else if ($name eq 'acronyms.xql') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<!-- query results are passed to XSLT servlet via request attribute -->
			<set-attribute name="xquery.attribute"
				value="xslt.model"/>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="xslt.model"/>
					<set-attribute name="xslt.stylesheet"
						value="xquery/stylesheets/acronyms.xsl"/>
				</forward>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value=""/>
					<set-attribute name="xslt.stylesheet" 
						value="stylesheets/db2html.xsl"/>
				</forward>
			</view>
		</dispatch>
	else if ($name eq 'biblio.xql') then
		let $display := request:get-parameter("display", "overview")
		let $xsl := 
			if ($display eq "details") then "detailed.xsl" else "overview.xsl"
		return
			<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
				<set-attribute name="xquery.attribute" value="xslt.model"/>
				<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="xslt.model"/>
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
	else if ($name eq 'applications.xml') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<!-- query results are passed to XSLT servlet via request attribute -->
			<set-attribute name="xquery.attribute"
				value="xslt.model"/>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="xslt.model"/>
					<set-attribute name="xslt.stylesheet" 
						value="apps/applications.xsl"/>
				</forward>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value=""/>
					<set-attribute name="xslt.stylesheet" 
						value="stylesheets/db2html.xsl"/>
				</forward>
			</view>
		</dispatch>

	else if ($name eq 'articles') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="/xquery/exist-articles.xql">
				<!-- query results are passed to XSLT servlet via request attribute -->
				<set-attribute name="xquery.attribute"
					value="xslt.model"/>
			</forward>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="xslt.model"/>
					<set-attribute name="xslt.stylesheet" 
						value="stylesheets/db2html.xsl"/>
				</forward>
			</view>
		</dispatch>

	(: the following xml files use different stylesheets :)
	else if ($name = ('index.xml', 'examples.xml', 'roadmap.xml', 'facts.xml', 'biblio.xml')) then
		let $stylesheet :=
			if ($name eq 'roadmap.xml') then
				"stylesheets/roadmap.xsl"
			else if ($name eq "facts.xml") then
				"stylesheets/facts.xsl"
			else
				"stylesheets/doc2html-2.xsl"
		return
			<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
				<view>
					<forward servlet="XSLTServlet">
						<set-attribute name="xslt.stylesheet"
							value="{$stylesheet}"/>
					</forward>
				</view>
				<cache-control cache="yes"/>
			</dispatch>
	else if (ends-with($uri, '.xml')) then
		(: 	check if the requested document was selected from a query result.
			if yes, pass it to docs.xql to get the match highlighting. :)
		if ($query) then
			let $docName := replace($uri, '^.*/([^/]+)$', '$1')
			return
				<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
					<forward url="docs.xql">
						<add-parameter name="path" value="/db/xqdocs/{$docName}"/>
					</forward>
				</dispatch>
		else
			<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
				<view>
					<forward servlet="XSLTServlet">
						<set-attribute name="xslt.stylesheet" 
							value="stylesheets/db2html.xsl"/>
					</forward>
				</view>
            	<cache-control cache="yes"/>
			</dispatch>
	else
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>
