(:
 :  eXide - web-based XQuery IDE
 :  
 :  Copyright (C) 2011 Wolfgang Meier
 :
 :  This program is free software: you can redistribute it and/or modify
 :  it under the terms of the GNU General Public License as published by
 :  the Free Software Foundation, either version 3 of the License, or
 :  (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU General Public License for more details.
 :
 :  You should have received a copy of the GNU General Public License
 :  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 :)
xquery version "3.0";

import module namespace tmpl="http://exist-db.org/xquery/template" at "tmpl.xql";

(:~ 
    Edit the expath and repo app descriptors.
    Functions to read, update the descriptors and deploy an app.
:)
declare namespace deploy="http://exist-db.org/eXide/deploy";
declare namespace expath="http://expath.org/ns/pkg";
declare namespace repo="http://exist-db.org/xquery/repo";

declare variable $app-root := request:get-attribute("app-root");

declare variable $deploy:ANT_FILE :=
    <project default="xar" name="$$app$$">
        <property name="project.version" value="$$version$$"/>
        <property name="project.app" value="$$app$$"/>
        <property name="build.dir" value="build"/>
        <target name="xar">
            <mkdir dir="${{build.dir}}"/>
            <zip basedir="." destfile="${{build.dir}}/${{project.app}}-${{project.version}}.xar" 
                excludes="${{build.dir}}/*"/>
        </target>
    </project>;
    
declare function deploy:select-option($value as xs:string, $current as xs:string?, $label as xs:string) {
    <option value="{$value}">
    { if (exists($current) and $value eq $current) then attribute selected { "selected" } else (), $label }
    </option>
};

declare function deploy:get-app-root($collection as xs:string) {
    if (doc(concat($collection, "/expath-pkg.xml"))) then
        $collection
    else if ($collection ne "/db") then
        let $parent := replace($collection, "^(.*)/[^/]+$", "$1")
        return
            deploy:get-app-root($parent)
    else
        ()
};

declare function deploy:store-expath($collection as xs:string?, $userData as xs:string*, $permissions as xs:int?) {
    let $descriptor :=
        <package xmlns="http://expath.org/ns/pkg"
            name="{request:get-parameter('name', ())}" abbrev="{request:get-parameter('abbrev', ())}"
            version="{request:get-parameter('version', ())}" spec="1.0">
            <title>{request:get-parameter("title", ())}</title>
        </package>
    return (
        xmldb:store($collection, "expath-pkg.xml", $descriptor, "text/xml"),
        xmldb:set-resource-permissions($collection, "expath-pkg.xml", $userData[1], $userData[2], $permissions)
    )
};

declare function deploy:repo-descriptor() {
    <meta xmlns="http://exist-db.org/xquery/repo">
        <description>
        {
            let $desc := request:get-parameter("description", ())
            return
                if ($desc) then $desc else request:get-parameter("title", ())
        }
        </description>
        {
            for $author in request:get-parameter("author", ())
            return
                <author>{$author}</author>
        }
        <website>{request:get-parameter("website", ())}</website>
        <status>{request:get-parameter("status", ())}</status>
        <license>GNU-LGPL</license>
        <copyright>true</copyright>
        <type>application</type>
        <target>{request:get-parameter("target", ())}</target>
        <prepare>{request:get-parameter("prepare", ())}</prepare>
        <finish>{request:get-parameter("finish", ())}</finish>
        {
            if (request:get-parameter("owner", ())) then
                <permissions user="{request:get-parameter('owner', ())}" 
                    password="{request:get-parameter('password', ())}" 
                    group="{request:get-parameter('group', ())}" 
                    mode="{request:get-parameter('mode', ())}"/>
            else
                ()
        }
    </meta>
};

declare function deploy:store-repo($descriptor as element(), $collection as xs:string?, $userData as xs:string*, $permissions as xs:int?) {
    (
        xmldb:store($collection, "repo.xml", $descriptor, "text/xml"),
        xmldb:set-resource-permissions($collection, "repo.xml", $userData[1], $userData[2], $permissions)
    )
};

declare function deploy:mkcol-recursive($collection, $components, $userData as xs:string*, $permissions as xs:int?) {
    if (exists($components)) then
        let $newColl := concat($collection, "/", $components[1])
        return (
            xmldb:create-collection($collection, $components[1]),
            if (exists($userData)) then
                xmldb:set-collection-permissions($newColl, $userData[1], $userData[2], $permissions)
            else
                (),
            deploy:mkcol-recursive($newColl, subsequence($components, 2), $userData, $permissions)
        )
    else
        ()
};

declare function deploy:mkcol($path, $userData as xs:string*, $permissions as xs:int?) {
    let $path := if (starts-with($path, "/db/")) then substring-after($path, "/db/") else $path
    return
        deploy:mkcol-recursive("/db", tokenize($path, "/"), $userData, $permissions)
};

declare function deploy:create-collection($collection as xs:string, $userData as xs:string+, $permissions as xs:int) {
    let $target := collection($collection)
    return
        if ($target) then
            $target
        else
            deploy:mkcol($collection, $userData, $permissions)
};

declare function deploy:check-group($group as xs:string) {
    if (xmldb:group-exists($group)) then
        ()
    else
        xmldb:create-group($group)
};

declare function deploy:check-user($repoConf as element()) as xs:string+ {
    let $perms := $repoConf/repo:permissions
    let $user := if ($perms/@user) then $perms/@user/string() else xmldb:get-current-user()
    let $group := if ($perms/@group) then $perms/@group/string() else xmldb:get-user-groups($user)[1]
    let $create :=
        if (xmldb:exists-user($user)) then
            if (index-of(xmldb:get-user-groups($user), $group)) then
                ()
            else (
                deploy:check-group($group),
                xmldb:add-user-to-group($user, $group)
            )
        else (
            deploy:check-group($group),
            xmldb:create-user($user, $perms/@password, $group, ())
        )
    return
        ($user, $group)
};

declare function deploy:target-permissions($repoConf as element()) as xs:int {
    let $permissions := $repoConf/repo:permissions/@mode/string()
    return
        if ($permissions) then
            util:base-to-integer(xs:int($permissions), 8)
        else
            util:base-to-integer(0775, 8)
};

declare function deploy:set-execute-bit($resource as xs:string) {
    let $mode :=
        sm:get-permissions($resource)/sm:permission/@mode
    return
        replace($mode, "(..).(..).(..).", "$1x$2x$3x")
};

declare function deploy:copy-templates($target as xs:string, $source as xs:string, $userData as xs:string+, $permissions as xs:int) {
    let $null := deploy:mkcol($target, $userData, $permissions)
    return (
        for $resource in xmldb:get-child-resources($source)
        let $targetPath := xs:anyURI(concat($target, "/", $resource))
        return (
            xmldb:copy($source, $target, $resource),
            let $mime := xmldb:get-mime-type($targetPath)
            let $perms := 
                if ($mime eq "application/xquery") then
                    xmldb:string-to-permissions(deploy:set-execute-bit($targetPath))
                else $permissions
            return
                xmldb:set-resource-permissions($target, $resource, $userData[1], $userData[2], $perms)
        ),
        for $childColl in xmldb:get-child-collections($source)
        return
            deploy:copy-templates(concat($target, "/", $childColl), concat($source, "/", $childColl), $userData, $permissions)
    )
};

declare function deploy:store-templates-from-db($target as xs:string, $base as xs:string, $userData as xs:string+, $permissions as xs:int) {
    let $template := request:get-parameter("template", "basic")
    let $templateColl := concat($base, "/templates/", $template)
    return
        deploy:copy-templates($target, $templateColl, $userData, $permissions)
};

declare function deploy:chmod($collection as xs:string, $userData as xs:string+, $permissions as xs:int) {
    (
        xmldb:set-collection-permissions($collection, $userData[1], $userData[2], $permissions),
        for $resource in xmldb:get-child-resources($collection)
        let $path := concat($collection, "/", $resource)
        let $mime := xmldb:get-mime-type($path)
        let $perms := 
            if ($mime eq "application/xquery") then
                xmldb:string-to-permissions(deploy:set-execute-bit($path))
            else $permissions
        return
            xmldb:set-resource-permissions($collection, $resource, $userData[1], $userData[2], $perms),
        for $child in xmldb:get-child-collections($collection)
        return
            deploy:chmod(concat($collection, "/", $child), $userData, $permissions)
    )
};

declare function deploy:store-ant($target as xs:string, $permissions as xs:int) {
    let $abbrev := request:get-parameter("abbrev", "")
    let $version := request:get-parameter("version", "1.0")
    let $parameters :=
        <parameters>
            <param name="app" value="{$abbrev}"/>
            <param name="version" value="{$version}"/>
        </parameters>
    let $antXML := tmpl:expand-template($deploy:ANT_FILE, $parameters)
    return
        xmldb:store($target, "build.xml", $antXML)
};

declare function deploy:store-templates-from-fs($target as xs:string, $base as xs:string, $userData as xs:string+, $permissions as xs:int) {
    let $pathSep := util:system-property("file.separator")
    let $template := request:get-parameter("template", "basic")
    let $templatesDir := concat($base, $pathSep, "templates", $pathSep, $template)
    return (
        xmldb:store-files-from-pattern($target, $templatesDir, "**/*", (), true(), "**/.svn/**"),
        deploy:chmod($target, $userData, $permissions)
    )
};

declare function deploy:store-templates($target as xs:string, $userData as xs:string+, $permissions as xs:int) {
    let $base := substring-before(system:get-module-load-path(), "/modules")
    return
        if (starts-with($base, "xmldb:exist://")) then
            deploy:store-templates-from-db($target, $base, $userData, $permissions)
        else
            deploy:store-templates-from-fs($target, $base, $userData, $permissions)
};

declare function deploy:store($collection as xs:string?, $expathConf as element()?) {
    let $repoConf := deploy:repo-descriptor()
    let $permissions := deploy:target-permissions($repoConf)
    let $userData := deploy:check-user($repoConf)
    return
        if (not($collection)) then
            error(QName("http://exist-db.org/xquery/sandbox", "missing-collection"), "collection parameter missing")
        else
            let $create := deploy:create-collection($collection, $userData, $permissions)
            let $null := (
                deploy:store-expath($collection, $userData, $permissions), 
                deploy:store-repo($repoConf, $collection, $userData, $permissions),
                if (empty($expathConf)) then (
                    deploy:store-templates($collection, $userData, $permissions),
                    deploy:store-ant($collection, $permissions)
                ) else
                    ()
            )
            return
                <ok/>
};

declare function deploy:view($collection as xs:string?, $expathConf as element()?, $repoConf as element()?) {
    let $null := util:declare-option("exist:serialize", "method=html media-type=text/html")
    return
        <form>
            {
                if ($collection) then (
                    <input type="hidden" name="collection" value="{$collection}"/>,
                    <h3>App collection: {$collection}</h3>
                ) else
                    ()
            }
            <fieldset>
                <legend>Application Properties</legend>
                <ol>
                    {
                        if (empty($repoConf)) then
                            <li>
                                <div class="hint">The template to use for generating the basic app structure.</div>
                                <select name="template">
                                    <option value="templating" selected="selected">HTML Templates</option>
                                    <option value="basic">Basic</option>
                                </select>
                                <label for="template">Template:</label>
                            </li>
                        else
                            ()
                    }
                    <li>
                        <div class="hint">The collection where the app will be installed. Should normally be different from 
                        the source collection.</div>
                        <input type="text" name="target" value="{$repoConf/repo:target}" 
                            placeholder="/db/yourapp" size="40" required="required"/>
                        <label for="target">Target collection:</label>
                    </li>
                    <li><hr/></li>
                    <li>
                        <div class="hint">The name of the package. This must be a URI.</div>
                        <input type="url" name="name" placeholder="http://exist-db.org/apps/yourapp" required="required"
                            value="{if ($expathConf) then $expathConf/@name else ''}" size="40"/>
                        <label for="name">Name:</label>
                    </li>
                    <li>
                        <div class="hint">A short name for the app. This will be the name of the collection into which
                        the app is installed.</div>
                        <input type="text" name="abbrev" placeholder="Short name" 
                            value="{$expathConf/@abbrev}" size="25" required="required"/>
                        <label for="abbrev">Abbreviation:</label>
                    </li>
                    <li>
                        <div class="hint">A descriptive title for the application.</div>
                        <input type="text" name="title" value="{$expathConf/expath:title}" size="40" required="required"/>
                        <label for="title">Title:</label>
                    </li>
                    <li>
                        <input type="text" name="version" value="{if ($expathConf) then $expathConf/@version else '0.1'}" 
                            size="10" required="required"/>
                        <label for="version">Version:</label>
                    </li>
                    <li>
                    {
                        let $status := $repoConf/repo:status/string()
                        return
                            <select name="status">
                                { deploy:select-option("alpha", $status, "Alpha") }
                                { deploy:select-option("beta", $status, "Beta") }
                                { deploy:select-option("stable", $status, "Stable") }
                            </select>
                    }
                        <label for="status">Status:</label>
                    </li>
                    <li><hr/></li>
                    <li>
                        <div class="hint">Optional: name of an XQuery script which will be run <b>before</b> the
                        application is installed. Use this to create users, index configurations and the like.</div>
                        <input type="text" name="prepare" value="{if ($repoConf) then $repoConf/repo:prepare else 'pre-install.xql'}" 
                            placeholder="pre-install.xql" size="40"/>
                        <label for="prepare">Pre-install XQuery:</label>
                    </li>
                    <li>
                        <div class="hint">Optional: name of an XQuery script which will be run <b>after</b> the
                        application was installed.</div>
                        <input type="text" name="finish" value="{$repoConf/repo:finish}" size="40"
                            placeholder="post-install.xql"/>
                        <label for="finish">Post-install XQuery:</label>
                    </li>
                </ol>
            </fieldset>
            <fieldset>
                <legend>Description</legend>
                <ol>
                    <li>
                        <div class="hint">The author(s) of the application.</div>
                        <label for="author">Author:</label>
                        <ul class="author-repeat">
                        {
                            if (empty($repoConf)) then
                                <li class="repeat"><input type="text" name="author" size="25"/></li>
                            else
                                for $author in $repoConf/repo:author
                                return
                                    <li class="repeat"><input type="text" name="author" value="{$author}" size="25"/></li>
                        }
                            <li><button id="author-add-trigger">Add</button><button id="author-remove-trigger">Remove</button></li>
                        </ul>
                    </li>
                    <li>
                        <div class="hint">A longer description of the application.</div>
                        <textarea name="description" cols="40">{$repoConf/repo:description/text()}</textarea>
                        <label for="description">Description:</label>
                    </li>
                    <li>
                        <div class="hint">Link to the author's website.</div>
                        <input type="url" name="website" value="{$repoConf/repo:website}" size="40"/>
                        <label for="website">Website:</label>
                    </li>
                </ol>
            </fieldset>
            <fieldset>
                <legend>Default Permissions</legend>
                
                <p>Default permissions applied to all resources uploaded into the database. To set
                non-default permissions on particular resources, use a post-install script.</p>
                {
                    let $owner := $repoConf/repo:permissions/@user
                    let $password := $repoConf/repo:permissions/@password
                    let $group := $repoConf/repo:permissions/@group
                    let $mode := $repoConf/repo:permissions/@mode
                    return
                        <ol>
                            <li>
                                <input type="text" name="owner" value="{$owner}" size="20"/>
                                <label for="owner">Owner:</label>
                            </li>
                            <li>
                                <input type="password" name="password" value="{$password}" size="20"/>
                                <label for="owner">Password:</label>
                            </li>
                            <li>
                                <input type="text" name="group" value="{$group}" size="20"/>
                                <label for="owner">Group:</label>
                            </li>
                            <li>
                                <input type="text" name="mode" value="{if ($mode) then $mode else '0744'}" size="4"/>
                                <label for="mode">Mode:</label>
                            </li>
                        </ol>
                }
            </fieldset>
        </form>
};

declare function deploy:package($collection as xs:string, $expathConf as element()) {
    let $name := concat($expathConf/@abbrev, "-", $expathConf/@version, ".xar")
    let $xar := compression:zip(xs:anyURI($collection), true(), $collection)
    let $mkcol := deploy:mkcol("/db/system/repo", (), ())
    return
        xmldb:store("/db/system/repo", $name, $xar, "application/zip")
};

declare function deploy:download($collection as xs:string, $expathConf as element()) {
    let $name := concat($expathConf/@abbrev, "-", $expathConf/@version, ".xar")
    let $xar := compression:zip(xs:anyURI($collection), true(), $collection)
    return (
        response:set-header("Content-Disposition", concat("attachment; filename=", $name)),
        response:stream-binary($xar, "application/zip", $name)
    )
};

declare function deploy:deploy($collection as xs:string, $target as xs:string, $expathConf as element(),
    $repoConf as element()) {
    let $null := util:declare-option("exist:serialize", "method=json media-type=application/json")
    let $port := request:get-server-port()
    let $pkg := deploy:package($collection, $expathConf)
    let $url := concat('http://localhost:',$port,'/exist/rest',$pkg)
    let $null := (
        repo:remove($expathConf/@name),
        repo:install($url),
        repo:deploy($expathConf/@name, $target)
    )
    return
        <info>{substring-after($target, "/db/")}</info>
};

declare function deploy:get-info-from-descriptor($collection as xs:string) {
    let $expathConf := doc(concat($collection, "/expath-pkg.xml"))/expath:package
    let $repoConf := doc(concat($collection, "/repo.xml"))/repo:meta
    let $user := xmldb:get-current-user()
    let $auth := if ($user) then xmldb:is-admin-user($user) else false()
    return
        <info xmlns:json="http://json.org" root="{$collection}" abbrev="{$expathConf/@abbrev}">
            <target>{$repoConf/repo:target/string()}</target>
            <deployed>{$repoConf/repo:deployed/string()}</deployed>
            <isAdmin json:literal="true">{$auth}</isAdmin>
        </info>
};

declare function deploy:get-info($collection as xs:string) {
    let $null := util:declare-option("exist:serialize", "method=json media-type=application/json")
    let $root := deploy:get-app-root($collection)
    return
        if ($root) then
            deploy:get-info-from-descriptor($root)
        else
            <info/>
};

let $target := request:get-parameter("target", ())
let $collectionParam := request:get-parameter("collection", ())
let $collection :=
    if ($collectionParam) then
        let $root := deploy:get-app-root($collectionParam)
        return
            if ($root) then $root else $collectionParam
    else
        $target
let $info := request:get-parameter("info", ())
let $deploy := request:get-parameter("deploy", ())
let $download := request:get-parameter("download", ())
let $expathConf := if ($collection) then xcollection($collection)/expath:package else ()
let $repoConf := if ($collection) then xcollection($collection)/repo:meta else ()
let $abbrev := request:get-parameter("abbrev", ())
return
    try {
        if ($download) then
            deploy:download($collection, $expathConf)
        else if ($info) then
            deploy:get-info($info)
        else if ($deploy) then
            deploy:deploy($collection, $target, $expathConf, $repoConf)
        else if ($abbrev) then
            deploy:store($collection, $expathConf)
        else
            deploy:view($collection, $expathConf, $repoConf)
    } catch exerr:EXXQDY0003 {
        response:set-status-code(403),
        <span>You don't have permissions to access or write the application archive.
            Please correct the location or log in as a different user.</span>
    } catch exerr:EXREPOINSTALL001 {
        response:set-status-code(404),
        <p>Failed to install application.</p>
    }
