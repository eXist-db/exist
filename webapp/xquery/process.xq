xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace util="http://exist-db.org/xquery/util";

(: Namespace for the local functions in this script :)
declare namespace f="http://exist-db.org/xquery/local-functions";

(:  
    Display hits for the current page. The number of hits to print per page
    is read from parameter "howmany". Parameter "start" identifies the 
    current offset in the result set.
:)
declare function f:display($hits as item()*, $count as xs:int)
as element()+
{
    if (empty($hits)) then
        <p>Nothing found! <a href="xquery.xq">Back to query form</a>.</p>
    else
        let $howmany := request:request-parameter("howmany", "10") cast as xs:int,
            $start := request:request-parameter("start", "1") cast as xs:int,
            $end := if ($start + $howmany le $count) then $start + $howmany - 1 else $count
        return (
            <table class="display" border="0" cellspacing="0" cellpadding="4">
                <tr>
                    <td colspan="3" align="center">
                        &lt;<a href="xquery.xq">New Query</a>&gt;
                    </td>
                </tr>
                { f:navbar($start, $end, $howmany, $count) }
                {
                    for $p in $start to $end
                    let $current := item-at($hits, $p),
                        $style := if($p mod 2 eq 0) then "high" else "low"
                    return
                        <tr class="{$style}">
                            <td align="left" class="position">{$p}</td>
                            <td colspan="2">
                                <xml-source>{$current}</xml-source>
                            </td>
                        </tr>
                }
                { f:navbar($start, $end, $howmany, $count) }
                 <tr>
                    <td colspan="3" align="center">
                        &lt;<a href="xquery.xq">New Query</a>&gt;
                    </td>
                </tr>
            </table>
        )
};

(: Display the navigation bar :)
declare function f:navbar($start as xs:int, $end as xs:int, 
$hitsPerPage as xs:int, $count as xs:int) as element() 
{
    let $uri := request:request-uri()
    return
        <tr class="navbar">
            <td align="left" width="7%">
                {
                    (: Link to previous page :)
                    if ($start gt 1) then
                        <a class="navbar" href="{$uri}?start={$start
                        - $hitsPerPage}&amp;howmany={$hitsPerPage}">&lt;&lt;</a>
                    else ()
                }
            </td>
            <td align="center" width="86%">
                {
                    (: Create page shortcuts :)
                    let $sections := $count idiv $hitsPerPage - 1,
                        $currentSection := $start idiv $hitsPerPage,
                        $lastSection :=
                            if ($sections lt 0) then 0 else
                                (: show 10 max unless current page > 10 :)
                                if ($sections gt 9) then 
                                    if ($currentSection gt 8) then $currentSection + 1
                                    else 9
                                else $sections
                    for $i in 0 to $lastSection
                    let $s := xs:int($i * $hitsPerPage + 1)
                    return 
                        if($i ne $currentSection) then
                            <a class="navbar"
                            href="{$uri}?start={$s}&amp;howmany={$hitsPerPage}">
                                [{$i + 1}]
                            </a>
                        else
                            (: current page doesn't get a link :)
                            <span class="navbar">[{$i + 1}]</span>
                }
            </td>
            <td align="right" width="7%">
                {
                    (: Link to next page :)
                    if ($end lt $count) then
                        <a class="navbar" href="{$uri}?start={$end + 1}&amp;howmany={$hitsPerPage}">
                            &gt;&gt;
                        </a>
                    else ()
                }
            </td>
        </tr>
};

(:  Add the last query to the query-history. The history is
    stored in the session as an XQuery sequence.
:)
declare function f:add-to-history($query as xs:string) as empty()
{
    let $history := request:get-session-attribute("history")
    return
        request:set-session-attribute("history", ($history, $query))
};

(:  The main function. If a query has been passed in parameter
    "query", execute the query and store the results into the
    session. If "query" is empty, try to retrieve the previous
    result set from the session.
:)
declare function f:main() as element()+
{
    let $query := request:request-parameter("query", ()),
        $previous := request:get-session-attribute("results"),
        $collection := request:request-parameter("collection", ())
    return
        if ($query) then
            let $startTime := current-time(),
                $result := util:eval($query, $collection),
                $count := count($result),
                $queryTime := current-time() - $startTime
            return (
                <p>Found {$count} hits in
                {get-seconds-from-dayTimeDuration($queryTime)} seconds.</p>,
                request:set-session-attribute("results", $result),
                f:add-to-history($query),
                f:display($result, $count)
            )
        else if ($previous) then
            f:display($previous, count($previous))
        else
            <p>Please specify a query! <a href="xquery.xq">Back to query
            form</a>.</p>
};

<document xmlns:xi="http://www.w3.org/2001/XInclude">
   	
    <header>
        <logo src="logo.jpg"/>
        <title>Open Source XML Database</title>
	    <author email="wolfgang@exist-db.org">Wolfgang M. Meier</author>
        <style href="xquery.css"/>
    </header>    

    <!-- include sidebar -->
    <xi:include href="sidebar.xml"/>
  
    <body>

        <section title="Query Results">
        
        { f:main() }

        </section>
    </body>
</document>
