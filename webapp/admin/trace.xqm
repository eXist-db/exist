module namespace prof="http://exist-db.org/xquery/profiling";

import module namespace yui="http://exist-db.org/xquery/yui" at "yahoo-ui.xql";

declare variable $prof:OPTIMIZATIONS :=
    <optimizations>
        <opt n="0">No index</opt>
        <opt n="1">Basic</opt>
        <opt n="2">Full</opt>
    </optimizations>;
    
declare function prof:main() as element()
{
    prof:display-page()
};

declare function prof:display-page() as element() {
    let $action := request:get-parameter("action", ())
    let $tab := number(request:get-parameter("tab", 1))
    let $tracelog := request:get-parameter("tracelog", "off") = "on"
    let $log := util:log("DEBUG", ("TAB: ", $tab))
    let $result :=
        if ($action eq "Clear") then
            system:clear-trace()
        else if ($action = "Enable") then
            system:enable-tracing(true(), $tracelog)
        else if ($action = "refresh") then
            system:enable-tracing(system:tracing-enabled(), $tracelog)
        else if ($action eq "Disable") then
            system:enable-tracing(false())
        else
            ()
    let $sort := request:get-parameter("sort", "time")
    let $trace := system:trace()
    return
    	<div class="panel">
    		<div class="panel-head">Query Profiling</div>
    		{ prof:display-form($trace, $sort) }
    		{
    		    yui:tabset(
    		        <tabset selected="{$tab + 1}" var="profilingTabs">
    		            <tab label="Queries">
    		                { prof:queries($trace, $sort) }
    	                </tab>
    	                <tab label="Functions">
    	                    { prof:functions($trace, $sort) }
    	                </tab>
    	                <tab label="Indexes">
    	                    { prof:indexes($trace, $sort) }
                        </tab>
                    </tabset>
                )
            }
    	</div>
};

declare function prof:functions($trace as element(), $sort as xs:string) as element() {
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
        	{prof:display-functions($trace, $sort)}
        </table>
	</div>
};

declare function prof:queries($trace as element(), $sort as xs:string) as element() {
    <div class="inner-panel">
        <h2>Main Query Stats</h2>
        <table cellspacing="0" cellpadding="5" class="trace">
        	<tr>
        		<th>
        		    <a href="{session:encode-url(request:get-uri())}?panel=trace&amp;sort=name">
        		        Source
        	        </a>
                </th>
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
        	{prof:display-queries($trace, $sort)}
        </table>
    </div>
};

declare function prof:indexes($trace as element(), $sort as xs:string) as element() {
    <div class="inner-panel">
        <h2>Index Usage Stats</h2>
        <table cellspacing="0" cellpadding="5" class="trace">
        	<tr>
        		<th>
        		    <a href="{session:encode-url(request:get-uri())}?panel=trace&amp;sort=name">
        		        Source
        	        </a>
                </th>
                <th class="trace-calls">Index</th>
                <th class="trace-calls">Optimization</th>
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
        	{prof:display-indexes($trace, $sort)}
        </table>
		<p class="legend">Optimization = <em>"Full"</em> means: the query optimizer 
		could use the index to execute 
		the expression in the best possible way. <em>"Basic"</em>: an index was
		available but could only be used to speed up the lookup, no further optimization
		was applied. <em>"No index"</em>: no index was found, no optimization possible.</p>
    </div>
};

declare function prof:display-functions($trace as element(), $sort as xs:string) as element()+ {
    if ($trace/prof:function) then
        for $func in $trace/prof:function
        order by prof:sort($func, $sort) descending
        return
            <tr>
                <td>{$func/@name/string()}</td>
                <td>{prof:truncate-source(replace($func/@source, "^.*/([^/]+)$", "$1"))}</td>
                <td class="trace-calls">{$func/@calls/string()}</td>
                <td class="trace-elapsed">{$func/@elapsed/string()}</td>
            </tr>
    else
        <tr>
            <td colspan="3">No statistics available or tracing not enabled.</td>
        </tr>
};

declare function prof:display-queries($trace as element(), $sort as xs:string) as element()+ {
    let $sort := if ($sort eq "name") then "source" else $sort
    return
        if ($trace/prof:query) then
            for $query in $trace/prof:query
            order by prof:sort($query, $sort) descending
            return
                <tr>
                    <td>{prof:truncate-source(replace($query/@source, "^.*/([^/]+)$", "$1"))}</td>
                    <td class="trace-calls">{$query/@calls/string()}</td>
                    <td class="trace-elapsed">{$query/@elapsed/string()}</td>
                </tr>
        else
            <tr>
                <td colspan="3">No statistics available or tracing not enabled.</td>
            </tr>
};

declare function prof:display-indexes($trace as element(), $sort as xs:string) as element()+ {
    let $sort := if ($sort eq "name") then "source" else $sort
    return
        if ($trace/prof:index) then
            for $index in $trace/prof:index
            let $optimization := $prof:OPTIMIZATIONS/opt[@n = $index/@optimization]/string() 
            order by prof:sort($index, $sort) descending
            return
                <tr>
                    <td>{prof:truncate-source(replace($index/@source, "^.*/([^/]+)$", "$1"))}</td>
                    <td class="trace-calls">{$index/@type/string()}</td>
                    <td class="trace-calls">{$optimization}</td>
                    <td class="trace-calls">{$index/@calls/string()}</td>
                    <td class="trace-elapsed">{$index/@elapsed/string()}</td>
                </tr>
        else
            <tr>
                <td colspan="3">No statistics available or tracing not enabled.</td>
            </tr>
};

declare function prof:truncate-source($source as xs:string) as xs:string {
    if (string-length($source) gt 60) then
        substring($source, 1, 60)
    else
        $source
};

declare function prof:sort($function as element(), $sort as xs:string) {
    if ($sort eq "name") then
        $function/@name
    else if ($sort eq "calls") then
        xs:int($function/@calls)
    else if ($sort eq "source") then
        $function/@source
    else
        xs:double($function/@elapsed)
};

declare function prof:display-form($trace as element(), $sort as xs:string) {
    let $label := if (system:tracing-enabled()) then 'Disable' else 'Enable'
    let $tracelog := request:get-parameter('tracelog', '') eq 'on'
    return
        <form name="f-trace" action="{session:encode-url(request:get-uri())}" method="GET">
            <div class="inner-panel">
                <button type="submit" name="action" value="{$label}">{$label} Tracing</button>
                <button type="submit" name="action" value="Clear">Clear</button>
                <button id="trace-refresh" type="submit" name="action" 
                    value="refresh">Refresh</button>
                {
                    if ($tracelog) then
                        <input type="checkbox" name="tracelog" checked="checked"/> 
                    else
                        <input type="checkbox" name="tracelog"/>
                } Write additional info to log
                <input type="hidden" name="panel" value="trace"/>
                <input type="hidden" name="sort" value="{$sort}"/>
                <input type="hidden" name="tab" value="0"/>
            </div>
        </form>
};
