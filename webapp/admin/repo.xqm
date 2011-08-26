xquery version "1.0";

module namespace repomanager="http://exist-db.org/xquery/admin-interface/repo";

declare namespace package="http://expath.org/ns/pkg";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

declare variable $repomanager:coll := "/db/system/repo";
declare variable $repomanager:repo-uri := 
    if (request:get-parameter("repository-url", ())) then
        request:get-parameter("repository-url", ())
    else
        "http://demo.exist-db.org/exist/apps/public-repo/retrieve.html"
        (: "http://192.168.2.104:8080/exist/apps/public-repo/retrieve.html" :)
;

declare function local:entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{
    if (starts-with($path, "icon")) then
        <icon>{$path}</icon>
    else
    	<entry>
    		<path>{$path}</path>
    		<type>{$type}</type>
    		<data>{$data}</data>
    	</entry>
};

declare function local:entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean
{
	starts-with($path, "icon") or $path = ("repo.xml", "expath-pkg.xml")
};

declare function repomanager:publicrepo() as element()
{
    let $package-url := request:get-parameter("package-url", ())
    return
        <div class="process">
            <h3>Actions:</h3>
            <ul>
                {
                if (ends-with($package-url,'.xar')) then (
                    <li>Downloaded package: {$package-url}</li>,

                    let $http-response := httpclient:get(xs:anyURI($package-url), false(), ())
                    let $name := tokenize($package-url, "/")[last()]
                    let $package-mimetype := "application/xar"
                    let $package-data := xs:base64Binary($http-response/httpclient:body/text())
                    let $stored := xmldb:store($repomanager:coll, $name, $package-data, $package-mimetype)
                    let $meta := 
                        compression:unzip(
                            util:binary-doc($stored), util:function(xs:QName("local:entry-filter"), 3), 
                            (),  util:function(xs:QName("local:entry-data"), 4), ()
                        )
                    let $package := $meta//package:package/string(@name)
                    let $type := $meta//repo:meta//repo:type/string()
                    let $install :=
                        repo:install(concat('http://localhost:',request:get-server-port(),'/exist/rest',$stored))
                    let $deploy :=
                        if ($type eq "application") then
                            repo:deploy($package)
                        else
                            ()
                    return
                        <li>Package installed.</li>
                )
                else
                    <li><span style="color:#FF2400">Error uploading - Must be a valid Package archive (.xar file extension)</span></li>
                }
            </ul>
            <span><i>Important: installed XQuery library mappings will not become visible until eXist is restarted.</i></span>
    </div>
};

declare function repomanager:upload() as element()
{
    <div class="process">
        <h3>Actions:</h3>
        <ul>
        {
            let $name := request:get-parameter("name", ())
            let $repocol :=  if (collection($repomanager:coll)) then () else xmldb:create-collection('/db/system','repo')
            let $docName := if($name) then $name else request:get-uploaded-file-name("upload")
            let $file := request:get-uploaded-file-data("upload")
            return
                if ($docName) then
                    let $stored := xdb:store($repomanager:coll, xdb:encode-uri($docName), $file)
                    let $meta := 
                        compression:unzip(
                            util:binary-doc($stored), util:function(xs:QName("local:entry-filter"), 3), 
                            (),  util:function(xs:QName("local:entry-data"), 4), ()
                        )
                    let $package := $meta//package:package/string(@name)
                    let $type := $meta//repo:meta//repo:type/string()
                    let $install := 
                        repo:install(concat('http://localhost:',request:get-server-port(),'/exist/rest',$stored))
                    let $deployed :=
                        if ($type eq "application") then
                            repo:deploy($package)
                        else
                            ()
                    return
                        <li>Package uploaded and installed: {$name}</li>
                else
                    <li><span style="color:#FF2400">Error uploading - Must be a valid Package archive (.xar file extension)</span></li>
        }
        </ul>
    </div>
};

declare function repomanager:activate() as element()
{
    let $name := request:get-parameter("name", ())
    let $package := request:get-parameter("package", ())
    let $type := request:get-parameter("type", ())
    let $hostname := request:get-hostname()            (:  pkg-repo.jar needs http or file scheme :)
    let $port := request:get-server-port()
     return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>Installed package: {$name}</li>
                {
                    repo:install(concat('http://localhost:',$port,'/exist/rest',$repomanager:coll,'/',$name,'.xar')),
                    if ($type eq "application") then
                        repo:deploy($package)
                    else
                        ()
                }
            </ul>
    </div>
};

declare function repomanager:deactivate() as element()
{
    let $name := request:get-parameter("name", ())
    let $package := request:get-parameter("package", ())
    let $type := request:get-parameter("type", ())
    let $undeploy :=
        if ($type eq "application") then
            repo:undeploy($package)
        else
            ()
    let $remove := repo:remove($package)
    (: let $delete := xmldb:remove($repomanager:coll, $name) :)
    return
        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>Uninstalled package: {$name}</li>
            </ul>
        </div>
};

declare function repomanager:process-action() as element()*
{
    let $action := request:get-parameter("action", ()) return
        util:catch("java.lang.Exception",
            if($action eq "activate") then
            (
                repomanager:activate()
            )
            else if($action eq "deactivate") then
            (
                repomanager:deactivate()
            )
            else if($action eq "Upload Package") then
            (
                repomanager:upload()
            )
            else if($action eq "download") then
            (
                repomanager:publicrepo()
            )else(),

            <div class="error">
                An error occurred while processing the action:<br/>
                {$util:exception-message}
            </div>
        )
};

declare function repomanager:all-packages() {
    for $repo in repo:list()
    return
        <package url="{$repo}">
        {
            for $r in ("repo.xml", "expath-pkg.xml")
            let $meta := repo:get-resource($repo, $r)
            let $data := if (exists($meta)) then util:binary-to-string($meta) else ()
            return
                if (exists($data)) then
                    util:parse($data)
                else
                    ()
        }
        {
            let $icon := repo:get-resource($repo, "icon.png")
            return
                if (exists($icon)) then
                    <icon>icon.png</icon>
                else
                    ()
        }
        </package>
};

declare function repomanager:view-installed() {
    let $packages := repomanager:all-packages()
    return
        <ul id="installed">
        {
            for $package in $packages
            let $iconURL :=
                if ($package/icon) then
                    concat("get-icon.xql?package=", $package/@url)
                else
                    "images/package.png"
            let $pkg-name := $package/package:package/string(@name)
            let $pkg-abbrev := $package/package:package/string(@abbrev)
            let $type := $package//repo:type/string()
            let $version := $package/package:package/@version/string()
            let $app-url :=
				if ($package//repo:target) then
                	concat(
                    	request:get-context-path(), "/apps", 
                    	substring-after($package//repo:target, "/db"), "/"
                	)
				else
					()
            return
                <li class="package">
                    <div class="icon">
                        <img src="{$iconURL}" alt="{$package//package:title}" width="64"/>
                    </div>
                    <h3>{$package//package:title/string()} ({$version})</h3>
                    <div class="details">
                        <img class="close-details" src="images/close.png" alt="Close" title="Close"/>
                        <table>
                            <tr>
                                <th>Title:</th>
                                <td>{ $package//package:title/text() }</td>
                            </tr>
							{
								if ($app-url) then
									<tr>
										<th>Local URL:</th>
										<td>
											<a href="{$app-url}" target="_new">{$app-url}</a>
										</td>
									</tr>
								else
									()
							}
                            <tr>
                                <th>Author(s):</th>
                                <td>
                                    <ul>
                                    {
                                        for $author in $package//repo:author
                                        return
                                            <li>{$author/text()}</li>
                                    }
                                    </ul>
                                </td>
                            </tr>
                            <tr>
                                <th>Version:</th>
                                <td>{ $version }</td>
                            </tr>
                            <tr>
                                <th>Description:</th>
                                <td>{ $package//repo:description/text() }</td>
                            </tr>
                            <tr>
                                <th>License:</th>
                                <td>{ $package//repo:license/text() }</td>
                            </tr>
                            <tr>
                                <th>Website:</th>
                                <td><a href="{$package//repo:website}">{ $package//repo:website/text() }</a></td>
                            </tr>
                            <tr>
                                <td colspan="2" class="download">
                                    <form method="POST" action="admin.xql">
                                        <input type="hidden" name="panel" value="repo"/>
                                        <input type="hidden" name="name" value="{$pkg-abbrev}-{$version}.xar"/>
                                        <input type="hidden" name="package" value="{$pkg-name}"/>
                                        <input type="hidden" name="type" value="{$type}"/>
                                        <button type="submit" name="action" value="deactivate">
                                            <img src="images/remove.png" alt="Uninstall" title="Uninstall { $package//package:title/text() }"/>
                                        </button>
                                    </form>
                                </td>
                            </tr>
                        </table>
                    </div>
                </li>
        }
        </ul>
};

declare function repomanager:show-public() {
    <div class="public-repo-form" id="public">
        <input name="repository-url" size="60" value="{$repomanager:repo-uri}"/>
        <button name="retrieve-repo" id="retrieve-repo">Retrieve packages</button>
        
        <div id="loading-indicator">
            <p>Retrieving packages ...</p>
            <img src="images/ajax-loader.gif"/>
        </div>
        
        <div id="packages"></div>
        <div class="clearfix"></div>
    </div>
};

declare function repomanager:main() as element() {
    let $action := lower-case(request:get-parameter("action", "set repository"))
    let $repocol :=  if (collection($repomanager:coll)) then () else xmldb:create-collection('/db/system','repo')

    return
        <div class="panel">
            <h1>Package Repository</h1>
            { repomanager:process-action() }
            <ul class="tabs clearfix">
                <li><a href="#installed">Installed</a></li>
                <li><a href="#public">Public Repo</a></li>
                <li><a href="#upload">Upload</a></li>
            </ul>
            <div class="tab-container">
                <div class="content">
                    { repomanager:view-installed() }
                </div>
                <div class="content">
                    { repomanager:show-public() }
                </div>
                <div class="content" id="upload">
                    <form method="POST" enctype="multipart/form-data">
                        <input type="hidden" name="panel" value="repo"/>
                        <table>
                            <tr>
                                <td><input type="submit" name="action" value="Upload Package"/></td>
                                <td><input type="file" size="30" name="upload"/></td>
                            </tr>
                        </table>
                        <span><i>Note: You can upload example .xar packages located under webapp/repo/packages</i></span>
                    </form>
                </div>
            </div>
        </div>
};