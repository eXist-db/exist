xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace xdb = "http://exist-db.org/xquery/xmldb";

declare variable $local:jmx-servlet-path := "/status";

declare function local:get-server-uri() as xs:string {
    fn:concat("http://", request:get-server-name(), ":", request:get-server-port(), request:get-context-path())
};

    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<view>
			<forward servlet="XSLTServlet">
				<set-attribute name="xslt.input" value="{fn:concat(local:get-server-uri(), $local:jmx-servlet-path)}"/>
				<set-attribute name="xslt.stylesheet" value="{$exist:root}/xquery/status/status.xslt"/>
				<set-attribute name="xslt.output.media-type" value="text/html"/>
				<set-attribute name="xslt.output.doctype-public" value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
				<set-attribute name="xslt.output.doctype-system" value="resources/xhtml1-transitional.dtd"/>
			</forward>
		</view>
		<cache-control cache="no"/>
	</dispatch>
