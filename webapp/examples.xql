xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/examples";
declare namespace package="http://expath.org/ns/pkg";

declare variable $ex:default-packages :=
    <packages>
        <package uri="http://exist-db.org/apps/demo">
            <abbrev>demo</abbrev>
            <title>eXist-db Demo Apps</title>
        </package>
    </packages>
;

declare function local:display-package($name as xs:string) 
{
    let $pkg := local:metadata($name)
    let $pkg-name := $pkg/package:package/string(@name)
    let $pkg-title := $pkg/package:package/package:title
    let $pkg-abbrev := $pkg/package:package/string(@abbrev)
    let $app-url :=
				if ($pkg//repo:target) then
                	concat(
                    	request:get-context-path(), "/apps", 
                    	substring-after($pkg//repo:target, "/db"), "/"
                	)
				else
					()
    return
        <tr>
            <td class="status installed">Yes</td>
            <td>{$pkg-abbrev}</td>
            <td><a href="{$app-url}">{$pkg-title/text()}</a></td>
        </tr>
};

declare function local:metadata($name as xs:string)
{
    <package url="{$name}">
    {
        for $r in ("repo.xml", "expath-pkg.xml")
        let $meta := repo:get-resource($name, $r)
        let $data := if (exists($meta)) then util:binary-to-string($meta) else ()
        return
            if (exists($data)) then
                util:parse($data)
            else
                ()
    }
    {
        let $icon := repo:get-resource($name, "icon.png")
        return
            if (exists($icon)) then
                <icon>icon.png</icon>
            else
                ()
    }
    </package>
};

declare function local:list-packages($packages as element(packages)*) 
{
    <table class="packages">
        <thead>
            <tr>
                <th class="status">Installed</th>
                <th>Short Name</th>
                <th>Name</th>
            </tr>
        </thead>
    {
        let $all := repo:list()
        for $pkg in $packages/package
        return
            if (index-of($all, $pkg/@uri/string())) then
                local:display-package($pkg/@uri/string())
            else
                <tr>
                    <td class="status not-installed">No</td>
                    <td>{$pkg/abbrev/text()}</td>
                    <td>{$pkg/title/text()}</td>
                </tr>
    }
    </table> 
}; 

<book xmlns:xi="http://www.w3.org/2001/XInclude">
    <bookinfo>
        <graphic fileref="logo.jpg"/>
        <productname>Open Source Native XML Database</productname>
        <title>eXist-db Demo</title>
        <date>January 2012</date>
        <author>
            <firstname>Wolfgang</firstname>
            <surname>Meier</surname>
        </author>
    </bookinfo>
    <xi:include href="sidebar.xml"/>
    <chapter>
        <title>eXist-db Demo</title>
        
        <para>We're in the process of moving all examples and demos into separate
        application packages to make it easier for users to view or change them. The
        main set of examples is contained in the <emphasis>DemoApps</emphasis> package.</para>
        
        <para>The table below shows if the demo apps have been installed into this eXist-db instance.
        If yes, click on the package name to navigate to the main page of the package. If a package
        has not yet been installed, please go to the <a href="admin/admin.xql?panel=repo">Package 
        Repository</a> page in the admin web app. You can install the demo apps from the 
        <emphasis>Public Repo</emphasis>.</para>
        
        { local:list-packages($ex:default-packages) }
    </chapter>
</book>