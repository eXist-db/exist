xquery version "1.0";

declare option exist:serialize "method=text media-type=text/javascript";

import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "../admin/dates.xqm";

declare function local:sub-collections($root as xs:string, $children as xs:string*, $user as xs:string) {
    string-join(
        for $child in $children
        return
            local:collections(concat($root, '/', $child), $user),
        ","
    )
};

declare function local:canRead($collection as xs:string, $user as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 1, 1) eq 'r'
        else if ($group = $groups) then
            substring($permissions, 4, 1) eq 'r'
        else
            substring($permissions, 7, 1) eq 'r'
};

declare function local:canWrite($collection as xs:string, $user as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $groups := xmldb:get-user-groups($user)
    return
        if ($owner eq $user) then
            substring($permissions, 2, 1) eq 'w'
        else if ($group = $groups) then
            substring($permissions, 5, 1) eq 'w'
        else
            substring($permissions, 8, 1) eq 'w'
};

declare function local:collections($root as xs:string, $user as xs:string) {
    let $children := xmldb:get-child-collections($root)
    let $canWrite := local:canWrite($root, $user)
    return
        if (local:canRead($root, $user)) then
            <s>{{ "title": "{$root}", "isFolder": true, "key": "{$root}",
                "writable": {if ($canWrite) then 'true' else 'false' },
                "addClass": "{if ($canWrite) then 'writable' else 'readable'}"
                {
                    if (exists($children)) then
                        <s>, "children": [
                        {
                            local:sub-collections($root, $children, $user)
                        }
                        ]
                        </s>
                    else
                        ()
                }
            }}</s>
        else
            ()
};

declare function local:resources($collection as xs:string, $user as xs:string) {
    <s>[
    {
        string-join(
            let $resources := xmldb:get-child-resources($collection)
            for $resource in $resources
            let $path := concat($collection, "/", $resource)
            let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection, $resource))
            let $owner := xmldb:get-owner($collection, $resource)
            let $group := xmldb:get-group($collection, $resource)
            let $created := date:format-dateTime(xmldb:created($collection, $resource))
            let $lastMod := date:format-dateTime(xmldb:created($collection, $resource))
            order by $resource ascending
            return
                <s>["{$resource}", "{$permissions}", "{$owner}", "{$group}", "{$created}", "{$lastMod}"]</s>,
            ", "
        )
    }
    ]</s>
};

let $view       := request:get-parameter("view", "c")
let $collection := request:get-parameter("root", "/db")
let $user       := request:get-parameter("user", "guest")
let $pass       := request:get-parameter("password", "guest")
return
    if ($view eq "c") then
        <s>[{local:collections($collection, $user)}]</s>/normalize-space()
    else
        <s>{local:resources($collection, $user)}</s>/normalize-space()