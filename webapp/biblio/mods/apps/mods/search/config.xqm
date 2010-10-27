xquery version "1.0";

module namespace config="http://exist-db.org/mods/config";

declare variable $config:mods-root := "/db/mods";
declare variable $config:force-lower-case-usernames as xs:boolean := true();