xquery version "1.0";

import module namespace xdb="http://exist-db.org/xquery/xmldb";

declare variable $home external;
declare variable $dir external;

declare variable $commons-collection-name := "commons";
declare variable $commons-users-collection-name := "users";
declare variable $commons-groups-collection-name := "groups";

declare function local:mkcol-recursive($collection, $components) {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xdb:create-collection($collection, $components[1]),
            local:mkcol-recursive($newColl, subsequence($components, 2))
        )
    else
        ()
};

declare function local:mkcol($collection, $path) {
    local:mkcol-recursive($collection, tokenize($path, "/"))
};

util:log("INFO", ("Running pre-install script ...")),
if (xdb:group-exists("biblio.users")) then ()
else xdb:create-group("biblio.users"),
if (xdb:exists-user("editor")) then ()
else xdb:create-user("editor", "editor", "biblio.users", ()),

util:log("INFO", ("Loading collection configuration ...")),
local:mkcol("/db/system/config", "db/org/library/apps/mods/code-tables"),
xdb:store-files-from-pattern("/system/config/db/org/library/apps/mods/code-tables", $dir, "library/apps/mods/code-tables/*.xconf"),
local:mkcol("/db/system/config", "db/mods"),
xdb:store-files-from-pattern("/system/config/db/mods", $home, "samples/mods/*.xconf"),

util:log("INFO", ("Creating temp collection ...")),
local:mkcol("/db", "org/library/apps/mods/temp"),
xdb:set-collection-permissions("/db/org/library/apps/mods/temp", "editor", "biblio.users", util:base-to-integer(0770, 8)),

local:mkcol("/db", "mods/samples"),
local:mkcol("/db", "mods/eXist"),

local:mkcol("/db", $commons-collection-name),
local:mkcol(fn:concat("/db/", $commons-collection-name), $commons-users-collection-name),
local:mkcol(fn:concat("/db/", $commons-collection-name), $commons-groups-collection-name),
xdb:set-collection-permissions(fn:concat("/db/", $commons-collection-name, "/", $commons-users-collection-name), "editor", "biblio.users", util:base-to-integer(0770, 8)),
xdb:set-collection-permissions(fn:concat("/db/", $commons-collection-name, "/", $commons-groups-collection-name), "editor", "biblio.users", util:base-to-integer(0770, 8)),
xdb:store-files-from-pattern("/db/mods/samples", $home, "samples/mods/*.xml"),
xdb:store-files-from-pattern("/db/mods/eXist", $home, "samples/mods/eXist/*.xml")