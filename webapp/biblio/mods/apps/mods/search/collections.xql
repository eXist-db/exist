xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace config="http://exist-db.org/mods/config" at "config.xqm";
declare namespace session = "http://exist-db.org/xquery/session";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=text media-type=text/javascript";

declare function local:sub-collections($root as xs:string, $children as xs:string*) {
    fn:string-join(
        for $child in $children
        return
            local:collections(concat($root, '/', $child)),
        ","
    )
};

declare function local:collections($root as xs:string) {
    
    let $user := security:get-user-credential-from-session()[1],
    $children := xmldb:get-child-collections($root),
    $can-write := security:can-write-collection($user, $root) return
    
        if(security:can-read-collection($user, $root)) then
        
            (: is this the users collection? :)
            if($root eq $security:users-collection) then
            (
                (: users collection is treated specially :)
                if($children = $user)then
                (
                    (: found a mods home collection for the currently logged in user :)
                    let $home-collection-uri := security:get-home-collection-uri($user) return
                        <s>{{ "title": "Home", "isFolder": true, "key": "{$home-collection-uri}",
                            "writable": {if ($can-write) then 'true' else 'false' },
                            "addClass": "{if ($can-write) then 'writable' else 'readable'}",
                            "icon": "../css/dynatree/ltFld.user.gif"
                            {
                                let $home-children := xmldb:get-child-collections($home-collection-uri) return
                                    if(fn:exists($home-children)) then
                                        <s>, "children": [
                                        {
                                            local:sub-collections($home-collection-uri, $home-children)
                                        }
                                        ]
                                        </s>
                                    else
                                        ()
                            }
                        }}</s>
                )else()
            ) 
            else
            (   (: normal collection:)
                <s>{{ "title": "{fn:replace($root, '.*/','')}", "isFolder": true, "key": "{$root}",
                    "writable": {if ($can-write) then 'true' else 'false' },
                    "addClass": "{if ($can-write) then 'writable' else 'readable'}"
                    {
                        if (fn:exists($children)) then
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
            )
        else
            ()
};

<s>[{local:collections($config:mods-root)}]</s>