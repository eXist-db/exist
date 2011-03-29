xquery version "1.0";

import module namespace config="http://exist-db.org/mods/config" at "../config.xqm";
import module namespace json="http://www.json.org";
import module namespace request = "http://exist-db.org/xquery/request";
import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace session = "http://exist-db.org/xquery/session";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";
import module namespace util="http://exist-db.org/xquery/util";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare namespace exist = "http://exist.sourceforge.net/NS/exist";
declare namespace group = "http://commons/sharing/group";
declare namespace col = "http://library/search/collections";

declare option exist:serialize "method=json media-type=text/javascript";

(:~
: Generates tree nodes for navigation of collections in
: the library search app. Uses the JSON serializer to get JSON output.
:
: Also generates a virtual collection root called 'Groups', this starts
: as /db/commons/groups and then any sub-collection under here
: is actually a link back to the shared collection (i.e. a collection in a users home folder)
: Group collection paths look like /db/commons/group/{uuid}/{collection-name}
: The {uuid} refers to an entry stored in /db/commons/group
: The {collection-name} is the real name of the collection from the original users folder
:
: The JSON output generated is suitable for use with dynatree
:
: @author Adam Retter <adam@existsolutions.com>
:)


(:~
: Outputs details about a collection as a tree-node
:
: @param title
:   Title of the tree node
: @param collection-path
:   Collection path that backs the tree node
: @param is-folder
:   Is this tree node a folder?
: @param $icon-path
:   Optional path to a custom icon
: @param $tooltip
:   Optional tooltip
: @param writeable
:   Is this tree node writeable - i.e. can folders/resources be added to it
: @param additional-classes
:   Optional additional CSS classes to apply
: @param has-lazy-children
:   Are there children for this node which can be lazily fetched?
: @param explicit-children
:   Any children that should be explicity displayed, ignored if has-lazy-children is true()
:
:)
declare function col:create-tree-node($title as xs:string, $collection-path as xs:string, $is-folder as xs:boolean, $icon-path as xs:string?, $tooltip as xs:string?, $writeable as xs:boolean, $additonal-classes as xs:string*, $has-lazy-children as xs:boolean, $explicit-children as element(node)*) as element(node) {
    <node>
        <title>{$title}</title>
        <key>{$collection-path}</key>
        <isFolder>{$is-folder}</isFolder>
        <writeable>{$writeable}</writeable>
        <addClass>{
            fn:string-join(
                (if($writeable) then 'writable' else 'readable', $additonal-classes),
                ' '
            )
        }</addClass>
        {
        if($icon-path)then
            <icon>{$icon-path}</icon>
        else(),
        if($tooltip)then
            <tooltip>{$tooltip}</tooltip>
        else()
        }
        <isLazy>{$has-lazy-children}</isLazy>
        {
            if(not($has-lazy-children) and not(empty($explicit-children)))then
                for $explicit-child in $explicit-children return
                    <children>{$explicit-child/child::node()}</children>
            else()
        }
    </node>
};

(:~
: Gets the root collection and any special collections directly under root
:
: @param root-collection-path
:   Path to the root collection in the database
:)
declare function col:get-root-collection($root-collection-path as xs:string) as element(node) {

    let $user := security:get-user-credential-from-session()[1] return

        if(security:can-read-collection($user, $root-collection-path)) then
            let $children := xmldb:get-child-collections($root-collection-path),
            $can-write := security:can-write-collection($user, $root-collection-path),
            
            (: home collection :)
            $home-collection-path := security:get-home-collection-uri($user),
            $has-home-children := not(empty(xmldb:get-child-collections($home-collection-path))),
            $home-json := col:create-tree-node("Home", $home-collection-path, true(), "../skin/ltFld.user.gif" , "Home Folder", true(), "userHomeSubCollection", $has-home-children, ()),
            
            (: group collection :)
            $has-group-children := not(empty(sharing:get-users-groups(security:get-user-credential-from-session()[1]))),
            $group-json := col:create-tree-node("Groups", $config:groups-collection, true(), "../skin/ltFld.groups.gif", "Groups", false(), (), $has-group-children, ())
            
            return
            
                (: root collection, containing home and group collection as children :)
                col:create-tree-node(fn:replace($root-collection-path, ".*/", ""), $root-collection-path, true(), (), (), $can-write, (), false(), ($home-json, $group-json))
        else()
};

(:~
: Gets lazy child collections
:
: @param collection-path
:   The parent collection path
:)
declare function col:get-child-collections($collection-path as xs:string) as element(json:value){
    
    let $user := security:get-user-credential-from-session()[1] return
        if(security:can-read-collection($user, $collection-path)) then
            
            (:get children :)
            let $children := xmldb:get-child-collections($collection-path) return
                
                <json:value>
                {
                    for $child in $children
                    let $child-collection-path := fn:concat($collection-path, "/", $child) return
                    
                        (: output the child :)
                        col:get-collection($child-collection-path)
                }
                </json:value>
        else()           
};

(:~
: Gets a collection
:
: @param collection-path
:    The path of the collection to retrieve
:)
declare function col:get-collection($collection-path as xs:string) as element(json:value) {

    let $user := security:get-user-credential-from-session()[1] return

        (: perform some checks on a child:)
        if(security:can-read-collection($user, $collection-path))then
            let $name := fn:replace($collection-path, ".*/", ""),
            $can-write := security:can-write-collection($user, $collection-path),
            $has-children := not(empty(xmldb:get-child-collections($collection-path))),
            $shared-with-group-id := sharing:get-group-id($collection-path),
            $tooltip := 
                if($shared-with-group-id)then
                    fn:concat("Shared With: ", sharing:get-group($shared-with-group-id)/group:name/text())
                else()
            return
                (: output the collection :)
                <json:value>
                {
                    col:create-tree-node($name, $collection-path, true(), (), $tooltip, $can-write, (), $has-children, ())/child::node()
                }
                </json:value>
        else()
};

(:~
: Gets the virtual "Groups" root, i.e. returns all groups that are accessible to a user
:)
declare function col:get-groups-virtual-root() as element(json:value) {
    
    let $groups := sharing:get-users-groups(security:get-user-credential-from-session()[1]) return
        <json:value>
        {
            for $group in $groups return
                <json:value>
                {
                    col:create-tree-node($group/group:name, fn:concat($config:groups-collection, "/", $group/@id), true(), (), (), false(), (), true(), ())/child::node()
                }
                </json:value>
        }
        </json:value>
};

(:~
: Generates the collections for a group in the navigation tree
:
: $group-collection-path The virtual collection path of the group e.g. /db/mods/groups/{uuid}
:)
declare function col:get-group-virtual-child-collections($collection-path as xs:string) as element(json:value) {
    
    (: extract the group id from the virtual collection path :)
    let $group-id := fn:replace($collection-path, ".*/", "") return
        
        <json:value>{
            (: iterate through every users collection :)
            for $user-collection in xmldb:get-child-collections($config:users-collection)
            let $user-collection-path := fn:concat($config:users-collection, "/", $user-collection),
            $child-nodes := col:resolve-real-users-collection-for-group($group-id, $user-collection-path) return
                for $node in $child-nodes return
                    element json:value {
                        
                        (: TODO - we need this at the moment because if there is only one output node then the json output gets broken :)
                        if(count($child-nodes) eq 1)then        
                            attribute json:array { "true" }
                        else(),
                        
                        $node/child::node()
                    }
        }</json:value>
};

(:~
: Resolves a real collection from a users home folder for a group
:
: @param group-id
:   The id of the group to retrieve the collection for
:
: @param user-collection-path
:   The path of the users home folder to scan for collections that belong to the group-id
:)
declare function col:resolve-real-users-collection-for-group($group-id as xs:string, $user-collection-path as xs:string) as node()*
{
    let $user := security:get-user-credential-from-session()[1],
    $system-group := sharing:get-group($group-id)/group:system/group:group return
        for $user-sub-collection in xmldb:get-child-collections($user-collection-path)
        let $user-sub-collection-path := fn:concat($user-collection-path, "/", $user-sub-collection) return
            (
                if(security:get-group($user-sub-collection-path) eq $system-group)then(
                    col:get-collection($user-sub-collection-path)
                )else(),
                    col:resolve-real-users-collection-for-group($group-id, $user-sub-collection-path)
            )
};


(:~
: Request routing
:
: If the http querystring parameter key exists then we retrieve tree nodes based on this
: key which is basically a real or virtual (for groups) collection path.
: If there is no key we deliver the tree root
:)
if(request:get-parameter("key",()))then
    let $collection-path := request:get-parameter("key",()) return
        if($collection-path eq $config:groups-collection) then
            (: start of groups collection - the groups collection is virtual and so receives special treatment :)
            col:get-groups-virtual-root()
        else if(fn:starts-with($collection-path, $config:groups-collection)) then
            (: children of virtual group collection :)
            col:get-group-virtual-child-collections($collection-path)
        else
            (: just a child collection :)   
            col:get-child-collections($collection-path)
else
    (: no key, so its the root that we want :)
    col:get-root-collection($config:mods-root)