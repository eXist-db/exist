xquery version "1.0";

import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";
import module namespace config="http://exist-db.org/mods/config" at "config.xqm";
declare namespace session = "http://exist-db.org/xquery/session";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";
declare namespace group = "http://exist-db.org/mods/sharing/group";

declare option exist:serialize "method=text media-type=text/javascript";

declare function local:sub-collections($root as xs:string, $children as xs:string*) {
    fn:string-join(
        for $child in $children
        return
            local:collections(concat($root, '/', $child)),
        ","
    )
};

declare function local:get-group-collections($group-id as xs:string, $collection as xs:string) {

        fn:concat(
            """children"": [",
                
                let $sub-collections := xmldb:get-child-collections($collection) return
                    for $sub-collection at $i in  $sub-collections
                    let $sub-collection-path := fn:concat($collection, "/", $sub-collection),
                    $can-write := security:can-write-collection(security:get-user-credential-from-session()[1], $sub-collection-path) return
                        fn:concat(
                            "{""title"": """, fn:replace($sub-collection, '.*/',''), """,",
                            """isFolder"": true,",
                            """key"": """, $sub-collection-path, """,",
                            """writable"": ", if($can-write)then("true")else("false"), ",",
                            """addClass"": ", if($can-write)then("""writable""")else("""readable"""), ",",
                            local:get-group-collections($group-id, $sub-collection-path),
                            "}",
                            if($i lt count($sub-collections))then(",")else() 
                        ),
                        
            "]"
        )
};

declare function local:collections($root as xs:string) {
    
    if(xmldb:collection-available($root))then
    (
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
                    )else(),
                    
                    (: groups collection of shared group items :)
                    <s>{{ "title": "Groups", "isFolder": true, "key": "{$sharing:groups-collection}",
                                "writable": false,
                                "addClass": "readable",
                                "icon": "../css/dynatree/ltFld.groups.gif"
                                <s>, "children": [
                                {
                                    let $user-groups := sharing:get-users-groups(security:get-user-credential-from-session()[1]) return
                                        for $group at $i in  $user-groups return
                                            <s>{{
                                                "title": "{$group/group:name}",
                                                "isFolder": true,
                                                "key": "{fn:concat($sharing:groups-collection, "/", $group/@id)}",
                                                "writable": true,
                                                "addClass": "writable"
                                                {
                                                <s>,   
                                                    {local:get-group-collections($group/@id, $security:users-collection)}
                                                </s>
                                                }
                                            }}{if($i lt count($user-groups))then(",")else()}</s>
                                }
                                ]
                                </s>
                    }}</s>
                )
                
                (: groups collection is treated specially, i.e. skipped :)
                else if($root eq $sharing:groups-collection)then
                (
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
            else()
    )else()
};

<s>[{local:collections($config:mods-root)}]</s>