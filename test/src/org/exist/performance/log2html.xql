xquery version "1.0";

declare namespace t="http://exist.sourceforge.net/NS/exist";

declare function t:process-action($action as element(t:action)+) {
    let $isSeq := ($action/@name = "org.exist.performance.ActionSequence")
    return (
        <tr>
            <td class="{if ($isSeq) then 'sequence' else ''}">{string($action[1]/@name)}</td>
            <td class="timing">{round(avg($action/@elapsed))}</td>
            <td class="timing">{sum($action/@elapsed)}</td>
            <td class="desc">{string($action[1]/@description)}</td>
        </tr>,
        if ($isSeq) then
            <tr>
                <td colspan="4" class="nested">
                    {t:process-sequence($action[1])}
                </td>
            </tr>
        else ()
    )
};

declare function t:process-sequence($group as element(t:action)) {
    <table>
        <tr>
            <th>Action</th>
            <th class="timing">Avg. time</th>
            <th class="timing">Total time</th>
            <th class="desc">Description</th>
        </tr>
        {
            if ($group/@name = "org.exist.performance.ActionSequence") then
                let $actions := distinct-values(//t:action[@parent = $group/@id]/@id)
                for $action in $actions
                return
                    t:process-action(//t:action[@id = $action])
            else
                t:process-action($group)
        }
    </table>
};

declare function t:process-thread($thread as xs:string) {
    <div class="thread">
        <h1>Thread: {$thread}</h1>
        
        <table>
        <tr>
            <th>Action</th>
            <th class="timing">Avg. time</th>
            <th class="timing">Total time</th>
            <th class="desc">Description</th>
        </tr>
        {
            for $action in //t:action[@thread = $thread][not(@parent)]
            return
                t:process-action($action)
        }
        </table>
    </div>
};

<html>
    <head>
        <title>Performance Test Results</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	    <link rel="stylesheet" href="style.css" type="text/css" />
    </head>
    <body>
    {
        for $thread in distinct-values(//t:action/@thread)
        return
            t:process-thread($thread)
    }
    </body>
</html>