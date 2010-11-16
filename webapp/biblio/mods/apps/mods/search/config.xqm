xquery version "1.0";

module namespace config="http://exist-db.org/mods/config";

declare variable $config:mods-root := "/db/mods";
declare variable $config:app-root := "/db/org/library/apps/mods/search";
declare variable $config:force-lower-case-usernames as xs:boolean := true();
declare variable $config:send-notification-emails := false();
declare variable $config:smtp-server := "smtp.yourdomain.com";
declare variable $config:smtp-from-address := "exist@yourdomain.com";