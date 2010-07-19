xquery version "1.0";

declare option exist:serialize "method=text media-type=text/javascript";

declare variable $local:user := if (session:get-attribute("biblio.user")) then session:get-attribute("biblio.user") else "guest";

declare variable $local:COLLECTION := "/db/mods";

declare function local:sub-collections($root as xs:string, $children as xs:string*) {
    string-join(
        for $child in $children
        return
            local:collections(concat($root, '/', $child)),
        ","
    )
};

declare function local:canRead($collection as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $groups := xmldb:get-user-groups($local:user)
    return
        if ($owner eq $local:user) then
            substring($permissions, 1, 1) eq 'r'
        else if ($group = $groups) then
            substring($permissions, 4, 1) eq 'r'
        else
            substring($permissions, 7, 1) eq 'r'
};

declare function local:canWrite($collection as xs:string) as xs:boolean {
    let $permissions := xmldb:permissions-to-string(xmldb:get-permissions($collection))
    let $owner := xmldb:get-owner($collection)
    let $group := xmldb:get-group($collection)
    let $groups := xmldb:get-user-groups($local:user)
    return
        if ($owner eq $local:user) then
            substring($permissions, 2, 1) eq 'w'
        else if ($group = $groups) then
            substring($permissions, 5, 1) eq 'w'
        else
            substring($permissions, 8, 1) eq 'w'
};

declare function local:collections($root as xs:string) {
    let $children := xmldb:get-child-collections($root)
    let $canWrite := local:canWrite($root)
    return
        if (local:canRead($root)) then
            <s>{{ "title": "{substring-after($root, '/db/')}", "isFolder": true, "key": "{$root}",
                "writable": {if ($canWrite) then 'true' else 'false' },
                "addClass": "{if ($canWrite) then 'writable' else 'readable'}"
                {
                    if (exists($children)) then
                        <s>, "children": [
                        {
                            local:sub-collections($root, $children)
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

<s>[{local:collections($local:COLLECTION)}]</s>