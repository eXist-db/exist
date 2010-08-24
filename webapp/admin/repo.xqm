xquery version "1.0";

module namespace repomanager="http://exist-db.org/xquery/admin-interface/repo";

declare namespace package="http://expath.org/ns/pkg";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";
import module namespace repo="http://exist-db.org/xquery/repo";

declare variable $repomanager:coll := "/db/system/repo";
declare variable $repomanager:repo-uri := if (request:get-parameter("repository-url", ())) then
              request:get-parameter("repository-url", ())
            else
              "http://demo.exist-db.org/exist/repo/public/all/";


declare function local:entry-data-deploy($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{

	<entry>
		{
		if ($path eq tokenize($path, "/")[last()]) then
		  (<name>{tokenize($path, "/")[last()]}</name>,
           <path>{$path}</path>)		  
		else
		  (<name>{tokenize($path, "/")[last()]}</name>,
		  <path>{
		  let $segments := tokenize($path,"/")[not(position() eq last())]
		  for $segment in $segments
		  return

		    <segment>{$segment}</segment>

		  }</path>)
		}
		<type>{$type}</type>
		<data>{$data}</data>
	</entry>
};

declare function local:entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{

	<entry>
		<path>{$path}</path>
		<type>{$type}</type>
		<data>{$data}</data>
	</entry>
};

declare function local:entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean
{
	true()
};

declare function repomanager:publicrepo() as element()
{
let $package-url := request:get-parameter("package-url", ())
return
        <div class="process">
            <h3>Actions:</h3>
            <ul>
                {
                if (ends-with($package-url,'.xar')) then
                    (<li>uploaded package: {$package-url}</li>,

                    let $http-response := httpclient:get(xs:anyURI($package-url), false(), ())
                    let $name := tokenize($package-url, "/")[last()]
                    return
                    let $package-mimetype := "application/xar",
                    $package-data := xs:base64Binary($http-response/httpclient:body/text())
                    return
                    xmldb:store($repomanager:coll, $name, $package-data, $package-mimetype)
                    )
                else
                    <li><span style="color:#FF2400">Error uploading - Must be a valid Package archive (.xar file extension)</span></li>
                }
            </ul>
    </div>
};

declare function repomanager:upload() as element()
{
    let $name := request:get-parameter("name", ()),
    $repocol :=  if (collection($repomanager:coll)) then () else xmldb:create-collection('/db/system','repo'),
    $docName := if($name) then $name else request:get-uploaded-file-name("upload"),
    $file := request:get-uploaded-file-data("upload") return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                {
                if (contains($docName,'.xar')) then
                    (<li>uploaded package: {$docName}</li>,

                    xdb:decode-uri(xs:anyURI(xdb:store($repomanager:coll, xdb:encode-uri($docName), $file)))
                    )
                else
                    <li><span style="color:#FF2400">Error uploading - Must be a valid Package archive (.xar file extension)</span></li>
                }
            </ul>
    </div>
};

declare function repomanager:deploy() as element()
{
    let $name := request:get-parameter("name", ())
     return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>deployed application: {$name}</li>
                {
                    let $xar := util:binary-doc(concat($repomanager:coll,'/',$name,'.xar'))                
                    let $application := compression:unzip($xar, util:function(xs:QName("local:entry-filter"), 3), (),  util:function(xs:QName("local:entry-data-deploy"), 4), ())
                    let $repo := $application//repo:meta
                    (: let $createrootcoll := xmldb:create-collection() :)
                    return
                        <ul>
                        {
                        for $entry in $application
                        return
                         <li>
                         {if ($entry/type eq 'folder') then
                            let $p := $entry/path/segment/node()
                            let $l := $entry/path/segment[last()]/node()
                            let $p1 := string-join($p,'/')
                            let $colpath := concat('/db/',$p1)
                            return
                                 ('folder created: ',xmldb:create-collection(substring-before($colpath,$l), $l))
                         else
                            let $p := $entry/path/segment/node()
                            let $l := $entry/path/segment[last()]/node()
                            let $p1 := string-join($p,'/')
                            let $colpath := concat('/db/',$p1,'/')
                            return
                                if ($entry/name eq 'repo.xml' or $entry/name eq 'expath-pkg.xml')then
                                 ('repo meta file ignored: ', $entry/name)
                                else
                                 ('file created: ', xmldb:store($colpath, $entry/name/node(), $entry/data/node()) )
                        }
                        </li>
                        }
                        </ul>
                }
            </ul>
    </div>
};

declare function repomanager:activate() as element()
{
    let $name := request:get-parameter("name", ()),
    $hostname := request:get-hostname(),            (:  pkg-repo.jar needs http or file scheme :)
    $port := request:get-server-port()
     return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>activated package: {$name}</li>
                {
                    repo:install(concat('http://',$hostname,':',$port,'/exist/rest',$repomanager:coll,'/',$name,'.xar'))
                }
            </ul>
    </div>
};

declare function repomanager:deactivate() as element()
{
    let $name := request:get-parameter("name", ())
     return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>deactivated package: {$name}</li>
                {
                    repo:remove($name)
                }
            </ul>
    </div>
};

declare function repomanager:remove() as element()
{
    let $name := request:get-parameter("name", ())
     return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>removed package: {$name}</li>
                {xmldb:remove($repomanager:coll,concat($name,'.xar'))}
            </ul>
    </div>
};

declare function repomanager:process-action() as element()*
{
    let $action := request:get-parameter("action", ()) return
        util:catch("java.lang.Exception",
            if($action eq "remove") then
            (
                repomanager:remove()
            )
            else if($action eq "activate") then
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
            else if($action eq "deploy") then
            (
                repomanager:deploy()
            )
            else if($action eq "Download from Public Repository") then
            (
                repomanager:publicrepo()
            )else(),

            <div class="error">
                An error occurred while processing the action:<br/>
                {$util:exception-message}
            </div>
        )
};

declare function repomanager:main() as element() {
    let $action := lower-case(request:get-parameter("action", "set repository"))
    let $repocol :=  if (collection($repomanager:coll)) then () else xmldb:create-collection('/db/system','repo')

    return
        <div class="panel">
        <h1>Package Repository</h1>
        <form method="POST" enctype="multipart/form-data">
        { repomanager:process-action() }
        <table cellspacing="0" cellpadding="5" class="browse">
            <tr>
                <th/>
                <th>Name</th>
                <th>Description</th>
                <th>Date Installed</th>
                <th>Status</th>
                <th>Action</th>
            </tr>
         {
         let $files := if (collection($repomanager:coll)) then collection($repomanager:coll)/util:document-name(.) else ()
         let $repos := repo:list()
         return

            for $file in $files[contains(.,'.xar')]
            let $package-name := substring-before($file,'.xar')
            let $xar := util:binary-doc(concat($repomanager:coll,'/',$file))

            let $meta := compression:unzip($xar, util:function(xs:QName("local:entry-filter"), 3), (),  util:function(xs:QName("local:entry-data"), 4), ())
            let $package := $meta//package:package
            let $repo := $meta//repo:meta

            let $installed := exists($repos[. eq $package-name])
            return
             <tr>
                <td/>
                <td><a href="{$repo//repo:website}" target="website">{$package-name}</a><br/>
                {$repo//repo:type}</td>
                <td>{$repo//repo:description}</td>

                <td>{xmldb:last-modified($repomanager:coll, concat($package-name,'.xar'))}</td>
                <td> 
                {if ($installed) then
                    <span style="color:#00FF00">Active</span>
                else
                    <span style="color:#FF2400">Inactive</span>
                }
                </td>
                <td>
                {
                if($installed) then

                    ( <a href="?panel=repo&amp;action=deactivate&amp;name={$package-name}">deactivate</a>,
                      if ($repo//repo:type eq 'application') then 
                        ( ' | ',<a href="?panel=repo&amp;action=deploy&amp;name={$package-name}">deploy</a> )
                     else
                        ()
                     )
                 else
                 ( <a href="?panel=repo&amp;action=activate&amp;name={$package-name}">activate</a>,' | ',
                 <a href="?panel=repo&amp;action=remove&amp;name={$package-name}">remove</a>
                 )
                }

                </td>
             </tr>
            }
                </table>

                <span><i>Important: You must restart eXistdb to pick up any changes in repository.</i></span>
                <br/><br/>
                <table>
                    <tr>
                        <td><input type="submit" name="action" value="Download from Public Repository"/></td>
                        <td>
                        <select size="5" width="200" style="width:340px" name="package-url">
                           {
                            let $packages := httpclient:get(xs:anyURI($repomanager:repo-uri),false(),())//httpclient:body/node()
                             return
                             for $package in $packages//package[contains(url/.,'.xar')]
                             return
                                <option value="{$package/url}">{tokenize($package/url, "/")[last()]}</option>
                           }
                        </select>
                        </td>
                    </tr>
                    <tr>
                        <td> <a href="{substring-before($repomanager:repo-uri,'all/')}" target="_repo">Public Repository URL</a> :
                        </td>
                        <td><input name="repository-url" size="40" value="{$repomanager:repo-uri}"/></td>
                        <td><input type="submit" name="action" value="set repository"/></td>
                    </tr>

                </table>
                <br/>
                <table>
                    <tr>
                        <td><input type="submit" name="action" value="Upload Package"/></td>
                        <td><input type="file" size="30" name="upload"/></td>
                    </tr>
                </table>
                <span><i>Note: You can upload example .xar packages located under webapp/repo/packages</i></span>
          </form>
        </div>
};
