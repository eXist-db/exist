xquery version "3.0";

declare namespace find="http://exist-db.org/xquery/eXide/find";

declare option exist:serialize "method=json media-type=text/javascript";

declare function find:xquery-scripts($root as xs:string) {
    for $resource in xmldb:get-child-resources($root)
    let $path := concat($root, "/", $resource)
    where xmldb:get-mime-type($path) eq "application/xquery"
    return
        $path,
    
    for $child in xmldb:get-child-collections($root)
    let $path := concat($root, "/", $child)
    where sm:has-access($path, "r-x")
    return
        find:xquery-scripts($path)
};

declare function find:modules($root as xs:string) {
    for $script in find:xquery-scripts($root)
    let $data := util:binary-doc($script)
    let $source := util:base64-decode($data)
    where matches($source, "^module\s+namespace", "m")
    return
        let $match := analyze-string($source, "^module\s+namespace\s+([^\s=]+)\s*=\s*['""]([^'""]+)['""]", "m")//fn:match
        return
            <json:value xmlns:json="http://www.json.org" json:array="true"
                prefix="{$match/fn:group[1]}" uri="{$match/fn:group[2]}" at="{$script}"/>
};

<json:value xmlns:json="http://www.json.org">
{ for $module in find:modules("/db") order by $module/@at return $module }
</json:value>