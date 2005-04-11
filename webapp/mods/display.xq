module namespace display="http://exist-db.org/biblio/display";

declare namespace mods="http://www.loc.gov/mods/v3";

declare function display:record-short($num as xs:int, 
    $rec as element(), $expanded as xs:boolean) as element() {
    <tr class="record-short" id="r_{$num}">
        <td><input type="checkbox" name="r" value="{$num}"/></td>
        <td class="names">{display:names($rec/mods:name)}</td>
        <td class="year">{display:year($rec)}</td> 
        <td>{display:titles($rec)}</td>
        <td>
        {
            if ($expanded) then
                <a id="a_{$num}" href="javascript:hideDetails('{$num}')">
                    <img src="images/up.png" border="0"/>
                </a>
            else
                <a id="a_{$num}" href="javascript:loadDetails('{$num}')">
                    <img src="images/down.png" border="0"/>
                </a>
        }
        </td>
    </tr>
};

declare function display:record-full-preload($num as xs:int, $rec as element(),
    $visible as xs:boolean) as element() {
    <tr class="{if($visible) then () else 'hidden'}" id="f_{$num}">
        <td colspan="5">{display:record-full($rec)}</td>
    </tr>
};

declare function display:record-full($rec as element()) as element() {
    <div class="record-full">
        {
        let $title := $rec/mods:titleInfo
        return
            <div class="titles">
                <h1>{$title/mods:title[not(@type)]/text()}</h1>
                {
                    for $alt in $title/mods:title[@type="alternative"] return
                        <h2>{$alt/text()}</h2>
                }
            </div>
        }
        { 
            for $url in $rec/mods:location/mods:url/text() return
                <p class="url"><a href="{$url}">{$url}</a></p>
        }
        {
            if ($rec/mods:abstract) then
                <p class="abstract">{$rec/mods:abstract/text()}</p>
            else
                ()
        }
        <table cellspacing="0">
            { display:names-full($rec/mods:name) }
            { display:publishers($rec/mods:originInfo/mods:publisher) }
            {
                let $subjects := $rec/mods:subject
                return (
                    for $auth in distinct-values($subjects/@authority) return
                        display:subjects($subjects[@authority=$auth]/mods:topic,
                        $auth),
                    display:subjects($subjects[not(@authority)]/mods:topic, ())
                )
            }
            {
                display:show-single("Created", $rec/mods:originInfo/mods:dateCreated),
                display:show-single("Last modified", $rec/mods:originInfo/mods:dateModified),
                display:show-single("Mime type", $rec/mods:physicalDescription/mods:internetMediaType)
            }
        </table>
    </div>
};

declare function display:show-single($label as xs:string, $node as element()?) 
as element()* {
    if ($node) then
        <tr>
            <th>{$label}:</th>
            <td>{$node/text()}</td>
        </tr>
    else
        ()
};

declare function display:names-full($names as element()*) as element()* {
    if ($names) then
        <tr>
            <th>By:</th>
            <td>
            {
                for $name at $pos in $names
                let $str :=
                    if($name/mods:namePart[@type='family']) then
                        ($name/mods:namePart[@type='family']/text(), ", ", 
                        $name/mods:namePart[@type='given']/text())
                    else
                        $name/mods:namePart/text(),
                    $query := <a href="?field1=au&amp;term1={$str}&amp;mode1=near">{$str}</a>
                return
                    if ($pos > 1) then ("; ", $query) else $query
            }
            </td>
        </tr>
    else
        ()
};

declare function display:publishers($publishers as element()*) as element()* {
    if ($publishers) then
        <tr>
            <th>Publisher:</th>
            <td>
            {
                for $pub as xs:string at $pos in $publishers
                return
                    if ($pos > 1) then ("; ", $pub) else $pub
            }
            </td>
        </tr>
    else ()
};

declare function display:subjects($subjects as element()*, $auth as xs:string?) as element()* {
    if ($subjects) then
        <tr>
            <th>Topics
            { if ($auth) then (<br/>, "(", $auth, "):") else ":" }
            </th>
            <td>
            {
                let $ordered := 
                    for $s as xs:string in $subjects order by $s return $s
                for $topic at $pos in $ordered
                return
                    if ($pos > 1) then ("; ", $topic) else $topic
            }
            </td>
        </tr>
    else
        ()
};

declare function display:year($rec as element()) as xs:string? {
    if ($rec/mods:originInfo/mods:dateCreated) then
        concat(" (", substring($rec/mods:originInfo/mods:dateCreated, 1, 4), ")")
    else ()
};

declare function display:titles($rec as element()) as node()* {
    let $main := $rec/mods:titleInfo[not(@type)]
    return (
        <span class="mods-title">
            {$main/mods:title[not(@type)]/text()}
        </span>
    )
};

declare function display:alternative($titles as element()*) as node()* {
    for $alt in $titles 
    return (
        text { ". " },
        <span class="mods-alternative">
            {$alt/text()}
        </span>
    )
};

declare function display:names($names as element()*) as element()* {
    let $max := if (count($names) lt 3) then count($names) else 3
    for $pos in 1 to $max
    let $name := $names[$pos]
    return
        <span class="mods-name">
        { if ($pos > 1) then "; " else () }
        {
            if($name/mods:namePart[@type='family']) then
                ($name/mods:namePart[@type='family']/text(), ", ", 
                $name/mods:namePart[@type='given']/text())
            else
                $name/mods:namePart/text()
        }
        </span>
        
};

declare function display:navigation($hits as xs:int, $start as xs:int, $next as xs:int,
    $max as xs:int, $preload as xs:boolean) as element() {
    <div id="navigation">
        <h3>Showing hits {$start} to {$next - 1} of {$hits}</h3>
            <ul>
                <li>
                {
                    if ($preload) then
                        <a href="javascript:expandAll({$start},{$next})">
                            <img src="images/down.png" border="0"/>
                            Expand all
                        </a>
                    else
                        <a href="?start={$start}&amp;howmany={$max}&amp;expand">
                            <img src="images/down.png" border="0"/>
                            Expand all
                        </a>
                }
                </li>
                <li>
                    <a href="javascript:collapseAll({$start},{$next})">
                        <img src="images/up.png" border="0"/>
                        Collapse all
                    </a>
                </li>
                <li>
                    <label for="howmany">Display: </label>
                    <select name="howmany" onChange="form.submit()">
                    {
                        for $i in (10, 50, 100)
                        return
                            element option {
                                if ($i eq $max) then
                                    attribute selected { "true" }
                                else
                                    (),
                                $i
                            }
                    }
                    </select>
                </li>
                <li>
                    <a href="javascript:toggleCheckboxes()">Mark all</a>
                </li>
            </ul>
            <ul>
                <li>
                    <input type="submit" name="action" value="Remove"/></li>
                <li>
                    <input type="submit" onClick="exportData()" name="action" value="Export"/>
                    <label for="format">Format: </label>
                    <select id="format" name="format">
                        <option>MODS</option>
                    </select>
                </li>
            </ul>
            {
                if ($start > 1) then
                    <a id="link-prev" href="?start={$start - $max}&amp;howmany={$max}">
                        &lt;&lt; previous
                    </a>
                else ()
            }
            {
                if ($next <= $hits) then
                    <a id="link-next" href="?start={$next}&amp;howmany={$max}">
                        more &gt;&gt;
                    </a>
                else
                    ()
            }
            <input type="hidden" name="start" value="{$start}"/>
    </div>
};