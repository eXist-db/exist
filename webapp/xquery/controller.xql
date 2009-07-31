xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

let $uri := request:get-uri()
let $context := request:get-context-path()
let $path := substring-after($uri, $context)
let $name := replace($uri, '^.*/([^/]+)$', '$1')
return
    if ($name = ('search.xql', 'functions.xql', 'svnlog.xql')) then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<forward url="{$path}">
				<!-- query results are passed to XSLT servlet via request attribute -->
				<set-attribute name="xquery.attribute"
					value="model"/>
			</forward>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="model"/>
					<set-attribute name="xslt.stylesheet" 
						value="stylesheets/db2html.xsl"/>
				</forward>
			</view>
		</dispatch>
    else if ($name eq 'acronyms.xql') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<!-- query results are passed to XSLT servlet via request attribute -->
			<set-attribute name="xquery.attribute"
				value="model"/>
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.input"
						value="model"/>
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
    else if ($name = 'biblio.xml') then
		<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<view>
				<forward servlet="XSLTServlet">
					<set-attribute name="xslt.stylesheet"
						value="stylesheets/doc2html-2.xsl"/>
				</forward>
			</view>
			<cache-control cache="yes"/>
		</dispatch>
	else if ($name eq 'biblio.xql') then
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
	else if ($name eq 'twitter.xql') then
	    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
	        <view>
	            <forward url="twitter-view.xql"/>
            </view>
	    </dispatch>
    else
        <ignore xmlns="http://exist.sourceforge.net/NS/exist">
            <cache-control cache="yes"/>
		</ignore>