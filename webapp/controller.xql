(: 	This is the main controller for the web application. It is called from the
	XQueryURLRewrite filter configured in web.xml. :)
xquery version "1.0";

(:~ -------------------------------------------------------
    Main controller: handles all requests not matched by
    sub-controllers.
    ------------------------------------------------------- :)

declare namespace c="http://exist-db.org/xquery/controller";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

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
	else if (matches($path, "/(rest|servlet|webdav/|cocoon|test.xml)")) then
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>
	(: redirect sandbox root to sandbox.xql :)
	else if (matches($uri, "sandbox/?$")) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<redirect url="sandbox.xql"/>
		</dispatch>
	else if ($name eq 'applications.xml') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<!-- query results are passed to XSLT servlet via request attribute -->
			<set-attribute name="xquery.attribute"
				value="model"/>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="model"/>
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
					value="model"/>
			</forward>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="model"/>
					<set-attribute name="xslt.stylesheet" 
						value="stylesheets/db2xhtml.xsl"/>
				</forward>
			</view>
		</dispatch>
		
    else if ($path = "/xforms/idxconf.xml") then
        let $log := util:log("DEBUG", "in xforms rule") return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
            <forward url="idxconf.xql"/>
			<!--view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.stylesheet" 
						value="xforms/xsltforms/xsltforms.xsl"/>
				</forward>
			</view-->
            <cache-control cache="no"/>
		</dispatch>
		
	(: the following xml files use different stylesheets :)
	else if ($name = ('index.xml', 'roadmap.xml', 'facts.xml')
	    or $path = '/examples.xml') then
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
						<set-attribute name="xslt.exist.version"
						    value="{system:get-version()}-rev{system:get-revision()}-{system:get-build()}"/>
					</forward>
				</view>
				<cache-control cache="no"/>
			</dispatch>
	else if (ends-with($uri, '.xml')) then
		(: 	check if the requested document was selected from a query result.
			if yes, pass it to docs.xql to get the match highlighting. :)
		if ($query) then
			let $docName := replace($uri, '^.*/([^/]+)$', '$1')
			return
				<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
					<forward url="/xquery/docs.xql">
						<add-parameter name="path" value="/db/xqdocs/{$docName}"/>
					</forward>
				</dispatch>
		else
			<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
				<view>
					<forward servlet="XSLTServlet">
						<set-attribute name="xslt.stylesheet" 
							value="stylesheets/db2xhtml.xsl"/>
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
	else
		<ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>
