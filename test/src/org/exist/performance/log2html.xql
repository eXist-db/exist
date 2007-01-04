xquery version "1.0";

declare namespace t="http://exist.sourceforge.net/NS/exist";

declare function t:median($sequence) {
	let $ordered := for $item in $sequence order by $item return $item
    return 
		if (empty($ordered)) then
			()
		else if (count($ordered) mod 2 = 1) then
			$ordered[(count($ordered) idiv 2) + 1]
		else
			avg(($ordered[(count($ordered) idiv 2)], $ordered[(count($ordered) idiv 2) + 1]))
};

declare function t:process-action($group as element(t:group), $action as element(t:action)+) {
    let $isSeq := ($action/@name = "org.exist.performance.ActionSequence")
    return (
        <tr>
            <td class="{if ($isSeq) then 'sequence' else ''}">{string($action[1]/@name)}</td>
            <td class="timing">{round(t:median($action/@elapsed))}</td>
            <td class="timing">{sum($action/@elapsed)}</td>
            <td class="desc">{string($action[1]/@description)}</td>
        </tr>,
        if ($isSeq) then
            <tr>
                <td colspan="4" class="nested">
                    {t:process-sequence($group, $action[1])}
                </td>
            </tr>
        else ()
    )
};

declare function t:process-sequence($group as element(t:group), $sequence as element(t:action)) {
    <table>
        <tr>
            <th>Action</th>
            <th class="timing">Med. time</th>
            <th class="timing">Total time</th>
            <th class="desc">Description</th>
        </tr>
        {
            if ($sequence/@name = "org.exist.performance.ActionSequence") then
                let $actions := distinct-values($group//t:action[@parent = $sequence/@id]/@id)
                for $action in $actions
                return
                    t:process-action($group, $group//t:action[@id = $action])
            else
                t:process-action($group, $sequence)
        }
    </table>
};

declare function t:process-thread($group as element(t:group), $thread as xs:string) {
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
            for $action in $group//t:action[@thread = $thread][not(@parent)]
            return
                t:process-action($group, $action)
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
        for $group in //t:group
        return
            <div class="group">
                <h1>Group {string($group/@name)}</h1>
                {
                    for $thread in distinct-values($group//t:action/@thread)
                    return
                        t:process-thread($group, $thread)
                }
            </div>
    }
    </body>
</html>