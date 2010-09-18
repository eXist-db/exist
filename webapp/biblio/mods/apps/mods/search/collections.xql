xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace config="http://exist-db.org/mods/config" at "config.xqm";
declare namespace session = "http://exist-db.org/xquery/session";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=text media-type=text/javascript";

declare function local:sub-collections($root as xs:string, $children as xs:string*) {
    string-join(
        for $child in $children
        return
            local:collections(concat($root, '/', $child)),
        ","
    )
};

declare function local:collections($root as xs:string) {
    let $children := xmldb:get-child-collections($root)
    let $can-write := security:can-write-collection(security:get-user-credential-from-session()[1], $root)
    return
        if (security:can-read-collection(security:get-user-credential-from-session()[1], $root)) then
            <s>{{ "title": "{substring-after($root, '/db/')}", "isFolder": true, "key": "{$root}",
                "writable": {if ($can-write) then 'true' else 'false' },
                "addClass": "{if ($can-write) then 'writable' else 'readable'}"
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

<s>[{local:collections($config:mods-root)}]</s>