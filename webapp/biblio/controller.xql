xquery version "1.0";

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.xml"/>
	</dispatch>
(:  Main page: index.xml is a template, which is passed through
    search.xql and the db2xhtml stylesheet. search.xql will run
    the actual search and expand the index.xml template.
:)
else if ($exist:resource eq 'index.xml') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <view>
            <forward url="search.xql">
                <!-- Errors should be passed through instead of terminating the request -->
        		<set-attribute name="xquery.report-errors" value="yes"/>
        		<set-attribute name="xquery.attribute" value="model"/>
            </forward>
            <forward servlet="XSLTServlet">
    			<set-attribute name="xslt.input"
    				value="model"/>
    			<set-attribute name="xslt.stylesheet" 
    				value="../stylesheets/db2xhtml.xsl"/>
    			<set-attribute name="xslt.syntax-highlight" value="no"/>
    			<set-attribute name="xslt.output.media-type"
                        value="application/xhtml+xml"/>
                <set-attribute name="xslt.output.doctype-public"
                	    value="-//W3C//DTD XHTML 1.0 Transitional//EN"/>
                <set-attribute name="xslt.output.doctype-system"
                    value="resources/xhtml1-transitional.dtd"/>
    		</forward>
		</view>
	</dispatch>
(:  Retrieve an item from the query results stored in the HTTP session. The
	format of the URL will be /sandbox/results/X, where X is the number of the
	item in the result set :)
else if ($exist:resource eq 'retrieve') then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="{$exist:controller}/session.xql">
		</forward>
	</dispatch>
else if (matches($exist:path, '(resources/|styles/syntax|scripts/|logo.jpg|default-style2.css|curvycorners.js)')) then
    let $newPath := replace($exist:path, '^.*((styles/|scripts/|logo).*)$', '/$1')
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$newPath}"/>
    		<cache-control cache="yes"/>
    	</dispatch>
else
    (: everything else is passed through :)
    <ignore xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </ignore>