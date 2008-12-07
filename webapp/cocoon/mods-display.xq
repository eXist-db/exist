xquery version "1.0";
(: $Id: mods-display.xq 6538 2007-09-12 09:09:35Z brihaye $ :)

module namespace md="http://exist-db.org/biblio/mods-display";

declare namespace mods="http://www.loc.gov/mods/v3";

declare function md:record-full($rec as element()) as element() {
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
            if (exists($rec/mods:abstract)) then
                <p class="abstract">{$rec/mods:abstract/text()}</p>
            else
                ()
        }
        <table cellspacing="0">
            { md:names-full($rec/mods:name) }
            { md:publishers($rec/mods:originInfo/mods:publisher) }
            {
                let $subjects := $rec/mods:subject
                return (
                    for $auth in distinct-values($subjects/@authority) return
                        md:subjects($subjects[@authority=$auth]/mods:topic,
                        $auth),
                    md:subjects($subjects[not(@authority)]/mods:topic, ())
                )
            }
            {
                md:show-single("Created", $rec/mods:originInfo/mods:dateCreated),
                md:show-single("Last modified", $rec/mods:originInfo/mods:dateModified),
                md:show-single("Mime type", $rec/mods:physicalDescription/mods:internetMediaType)
            }
        </table>
    </div>
};

declare function md:show-single($label as xs:string, $node as element()?) 
as element()* {
    if (exists($node)) then
        <tr>
            <th>{$label}:</th>
            <td>{$node/text()}</td>
        </tr>
    else
        ()
};

declare function md:names-full($names as element()*) as element()* {
    if (exists($names)) then
        <tr>
            <th>By:</th>
            <td>
            {
                for $name at $pos in $names
                let $str :=
                    if(exists($name/mods:namePart[@type='family'])) then
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

declare function md:publishers($publishers as element()*) as element()* {
    if (exists($publishers)) then
        <tr>
            <th>Publisher:</th>
            <td>
            {
                for $pub as xs:string at $pos in $publishers/string()
                return
                    if ($pos > 1) then ("; ", $pub) else $pub
            }
            </td>
        </tr>
    else ()
};

declare function md:subjects($subjects as element()*, $auth as xs:string?) as element()* {
    if (exists($subjects)) then
        <tr>
            <th>Topics
            { if ($auth) then (<br/>, "(", $auth, "):") else ":" }
            </th>
            <td>
            {
                let $ordered := 
                    for $s as xs:string in $subjects/string() order by $s return $s
                for $topic at $pos in $ordered
                return
                    if ($pos > 1) then ("; ", $topic) else $topic
            }
            </td>
        </tr>
    else
        ()
};

declare function md:year($rec as element()) as xs:string? {
    if (exists($rec/mods:originInfo/mods:dateCreated)) then
        concat(" (", substring($rec/mods:originInfo/mods:dateCreated, 1, 4), ")")
    else ()
};

declare function md:titles($rec as element()) as element()* {
    let $main := $rec/mods:titleInfo[not(@type)]
    return (
        <span class="mods-title">
            {$main/mods:title[not(@type)]/text()}
        </span>
    )
};

declare function md:alternative($titles as element()*) as node()* {
    for $alt in $titles 
    return (
        text { ". " },
        <span class="mods-alternative">
            {$alt/text()}
        </span>
    )
};

declare function md:names($names as element()*) as element()* {
    let $max := if (count($names) lt 3) then count($names) else 3
    for $pos in 1 to $max
    let $name := $names[$pos]
    return
        <span class="mods-name">
        { if ($pos > 1) then "; " else () }
        {
            if(exists($name/mods:namePart[@type='family'])) then
                ($name/mods:namePart[@type='family']/text(), ", ", 
                $name/mods:namePart[@type='given']/text())
            else
                $name/mods:namePart/text()
        }
        </span>
        
};
