xquery version "1.0";

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.html"/>
	</dispatch>
else if ($exist:resource eq 'execute') then
    let $query := request:get-parameter("qu", ())
    let $startTime := util:system-time()
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
			<!-- Query is executed by XQueryServlet -->
            <forward servlet="XQueryServlet">
				<!-- Query is passed via the attribute 'xquery.source' -->
                <set-attribute name="xquery.source" value="{$query}"/>
				<!-- Results should be written into attribute 'results' -->
				<set-attribute name="xquery.attribute" value="results"/>
				<clear-attribute name="results"/>
				<!-- Errors should be passed through instead of terminating the request -->
				<set-attribute name="xquery.report-errors" value="yes"/>
            </forward>
			<view>
				<!-- Post process the result: store it into the HTTP session
					and return the number of hits only. -->
				<forward url="session.xql">
					<clear-attribute name="xquery.source"/>
					<clear-attribute name="xquery.attribute"/>
					<set-attribute name="elapsed" 
						value="{string(seconds-from-duration(util:system-time() - $startTime))}"/>
				</forward>
			</view>
        </dispatch>
(: Retrieve an item from the query results stored in the HTTP session. The
	format of the URL will be /sandbox/results/X, where X is the number of the
	item in the result set :)
else if (starts-with($exist:path, '/results/')) then
	<dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="../session.xql">
			<add-parameter name="num" value="{$exist:resource}"/>
		</forward>
	</dispatch>
else if (matches($exist:path, '(scripts/|logo.jpg|default-style2.css)')) then
    let $newPath := replace($exist:path, '^.*((scripts/|logo).*)$', '/$1')
    return
        <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
    		<forward url="{$newPath}"/>
    		<cache-control cache="yes"/>
    	</dispatch>
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </dispatch>