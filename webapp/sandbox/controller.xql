xquery version "1.0";

(:~
	Controller XQuery for the sandbox application. To avoid
	using util:eval, the controller executes a user-defined
	query by forwarding it to the XQueryServlet first, where it will
	be handled like any other query. The results are then passed to
	a second XQuery, session.xql, for post-processing.
:)

(: Root path: redirect to sandbox.xql :)
if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="sandbox.xql"/>
	</dispatch>
(: Execute a query :)
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
else if (starts-with($exist:path, '/scripts/yui')) then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<forward url="/{replace($exist:path, '(/scripts/yui/.*)$', '$1')}"/>
	</dispatch>
else
    (: everything else is passed through :)
    <ignore xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </ignore>
