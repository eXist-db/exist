xquery version "3.0";

(:~
 : A module to repair eXist-db's package repository, e.g. after you had to do a complete restore.
 : 
 : @author Wolfgang
 :)
module namespace repair="http://exist-db.org/xquery/repo/repair";

declare namespace expath="http://expath.org/ns/pkg";

(:~
 : Remove all installed packages from the repository!
 :)
declare %public function repair:clean-all() {
    for $pkg in repo:list()
    where repair:delete-package($pkg)
    return
        repo:remove($pkg)
};

(:~
 : Try to repair the package repository by scanning the collection containing the repository root
 : and the /db/system/repo. Re-creates .xar packages from the data found and registers them with
 : the package manager.
 :)
declare %public function repair:repair() {
    <repair>
    {
        let $root := repo:get-root()
        let $root := if (ends-with($root, "/")) then substring($root, 1, string-length($root) - 1) else $root
        return
            repair:scan-collections(xs:anyURI($root), repair:repair-callback(?, false())),
        repair:scan-collections(xs:anyURI("/db/system/repo"), repair:repair-callback(?, true()))
    }
    </repair>
};

(:~
 : Try to re-register a specific collection with the package manager.
 :)
declare %public function repair:repair($collection as xs:anyURI) {
    <repair>
    {
        repair:repair-callback($collection, false())
    }
    </repair>
};

declare %private function repair:repair-callback($collection as xs:anyURI, $includeAll as xs:boolean) {
    let $expathXML := doc($collection || "/expath-pkg.xml")//expath:package
    return
        if (exists($expathXML)) then
            try {
                let $xar := repair:get-xar($expathXML, $collection, $includeAll)
                let $installed := repair:install($xar)
                return
                    <package name="{$expathXML/@name}" restored="{$installed}"/>
            } catch * {
                <error collection="{$collection}">{$err:description}</error>
            }
        else
            ()
};

declare function repair:is-installed($name as xs:string) as xs:boolean {
    exists(filter(function($pkg) { $pkg = $name }, repo:list()))
};

(:~ Scan a collection tree recursively starting at $root. Call $func once for each collection found :)
declare %private function repair:scan-collections($root as xs:anyURI, $func as function(xs:anyURI) as item()*) {
    $func($root),
    for $child in xmldb:get-child-collections($root)
    return
        repair:scan-collections(xs:anyURI($root || "/" || $child), $func)
};

declare %private function repair:find-resources-to-zip($collection as xs:anyURI) {
    filter(function($name) {
        $name = ("expath-pkg.xml", "repo.xml", "exist.xml") or starts-with($name, "icon")
    }, xmldb:get-child-resources($collection))
};

declare %private function repair:resources-to-zip($expathConf as element(expath:package), $collection as xs:anyURI) {
    let $abbrev := $expathConf/@abbrev
    let $contentCol :=
        if (xmldb:collection-available($collection || "/" || $abbrev)) then
            $abbrev
        else if (xmldb:collection-available($collection || "/content")) then
            "content"
        else
            ()
    for $resource in ($contentCol, repair:find-resources-to-zip($collection))
    return
        xs:anyURI($collection || "/" || $resource)
};

declare %private function repair:get-xar($expathConf as element(expath:package), $collection as xs:anyURI, $includeAll as xs:boolean) {
    let $name := concat($expathConf/@abbrev, "-", $expathConf/@version, ".xar")
    let $xar := 
        if ($includeAll) then
            compression:zip($collection, true(), string($collection))
        else
            compression:zip(repair:resources-to-zip($expathConf, $collection), true(), string($collection))
    let $mkcol := xmldb:create-collection("/db/system", "repo")
    return
        xmldb:store("/db/system/repo", $name, $xar, "application/zip")
};

declare %private function repair:install($xar as xs:string) {
    let $installed := repo:install-from-db($xar)
    return
        if ($installed) then (
            xmldb:remove(replace($xar, "^(.*)/[^/]+$", "$1"), replace($xar, "^.*/([^/]+)$", "$1")),
            true()
        ) else
            false()
};

(:~
 : Check if the given package should be deleted before it is reconstructed from the backed up data.
 : A library package not installed in the database should only be deleted if a copy exists in /db/system/repo
 : from which we can restore it.
 :)
declare %private function repair:delete-package($pkg as xs:string) {
    let $repoDescriptor := repo:get-resource($pkg, "repo.xml")
    return
        if (exists($repoDescriptor)) then
            (: only packages not installed in the db need to be checked: repo:target will be empty for those :)
            if (empty(parse-xml(util:binary-to-string($repoDescriptor))//repo:target/node())) then
                exists(collection("/db/system/repo")//expath:package[@name = $pkg])
            else
                true()
        else
            exists(collection("/db/system/repo")//expath:package[@name = $pkg])
};