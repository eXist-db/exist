module namespace prof="http://exist-db.org/xquery/profiling";

declare function prof:main() as element()
{
    prof:display-page()
};

declare function prof:display-page() as element() {
    let $action := request:get-parameter("action", ())
    let $result :=
        if ($action eq "Clear") then
            system:clear-trace()
        else if ($action eq "Enable") then
            system:enable-tracing(true())
        else if ($action eq "Disable") then
            system:enable-tracing(false())
        else
            ()
    let $sort := request:get-parameter("sort", "time")
    let $trace := system:trace()
    return
	<div class="panel">
		<div class="panel-head">Function Profiling</div>
		{ prof:display-form($trace, $sort) }
		<div class="inner-panel">
            <h2>Function Call Stats</h2>
        	<table cellspacing="0" cellpadding="5" class="trace">
        		<tr>
        			<th>
        			    <a href="{session:encode-url(request:get-uri())}?panel=trace&amp;sort=name">
        			        Function
        		        </a>
        	        </th>
        	        <th>Source</th>
        			<th class="trace-calls">
        			    <a href="{session:encode-url(request:get-uri())}?panel=trace&amp;sort=calls">
        			        Calls
        		        </a>
        			</th>
        			<th class="trace-elapsed">
        			    <a href="{session:encode-url(request:get-uri())}?panel=trace&amp;sort=time">
        			        Elapsed time in sec.
        		        </a>
        		    </th>
        		</tr>
        		{prof:display($trace, $sort)}
        	</table>
        </div>
	</div>
};

declare function prof:display($trace as element(), $sort as xs:string) as element()+ {
    if ($trace/prof:function) then
        for $func in $trace/prof:function
        order by prof:sort($func, $sort) descending
        return
            <tr>
                <td>{$func/@name/string()}</td>
                <td>{replace($func/@source, "^.*/([^/]+)$", "$1")}</td>
                <td class="trace-calls">{$func/@calls/string()}</td>
                <td class="trace-elapsed">{$func/@elapsed/string()}</td>
            </tr>
    else
        <tr>
            <td colspan="3">No statistics available or tracing not enabled.</td>
        </tr>
};

declare function prof:sort($function as element(prof:function), $sort as xs:string) {
    if ($sort eq "name") then
        $function/@name
    else if ($sort eq "calls") then
        xs:int($function/@calls)
    else
        xs:double($function/@elapsed)
};

declare function prof:display-form($trace as element(), $sort as xs:string) {
    let $label := if (system:tracing-enabled()) then 'Disable' else 'Enable'
    return
        <form action="{session:encode-url(request:get-uri())}" method="GET">
            <div class="inner-panel">
                <button type="submit" name="action" value="{$label}">{$label} Tracing</button>
                <button type="submit" name="action" value="Clear">Clear</button>
                <button type="submit" name="action" value="refresh">Refresh</button>
                <input type="hidden" name="panel" value="trace"/>
                <input type="hidden" name="sort" value="{$sort}"/>
            </div>
        </form>
};