xquery version "1.0";

module namespace repomanager="http://exist-db.org/xquery/admin-interface/repo";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";
import module namespace repo="http://exist-db.org/xquery/repo";

declare function repomanager:upload() as element()
{
    let $name := request:get-parameter("name", ()),
    $repocol :=  if (collection('/db/system/repo')) then () else xmldb:create-collection('/db/system','repo'),
    $docName := if($name) then $name else request:get-uploaded-file-name("upload"),
    $file := request:get-uploaded-file-data("upload") return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                {
                if (contains($docName,'.xar')) then
                    (<li>uploaded package: {$docName}</li>,

                    xdb:decode-uri(xs:anyURI(xdb:store('/db/system/repo', xdb:encode-uri($docName), $file)))
                    )
                else
                    <li><span style="color:#FF2400">Error uploading - Must be a valid Package archive (.xar file extension)</span></li>
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
                    repo:install(concat('http://',$hostname,':',$port,'/exist/rest/db/system/repo/',$name,'.xar'))
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
                {xmldb:remove('/db/system/repo',concat($name,'.xar'))}
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
            )else(),

            <div class="error">
                An error occurred while processing the action:<br/>
                {$util:exception-message}
            </div>
        )
};

declare function repomanager:main() as element() {
    let $action := lower-case(request:get-parameter("action", "refresh"))
    let $repocol :=  if (collection('/db/system/repo')) then () else xmldb:create-collection('/db/system','repo')

    return
        <div class="panel">
            <h1>Package Repository</h1>
            <form method="POST" enctype="multipart/form-data">


         { repomanager:process-action() }

        <table cellspacing="0" cellpadding="5" class="browse">
            <tr>
                <th/>
                <th>Package</th>
                <th>Date installed</th>
                <th>Installed</th>
                <th>Action</th>
            </tr>
        {
         let $files := if (collection('/db/system/repo')) then collection('/db/system/repo')/util:document-name(.) else ()
         let $repos := repo:list()
         return

            for $file in $files
            let $package-name := substring-before($file,'.xar')
            let $installed := exists($repos[. eq $package-name])
            return
             <tr>
                <td/>
                <td>{$package-name}</td>
                <td>{xmldb:last-modified('/db/system/repo', concat($package-name,'.xar'))}</td>
                <td> 
                {if ($installed) then
                    <span style="color:#00FF00">{$installed}</span>
                else
                    <span style="color:#FF2400">{$installed}</span>
                }
                </td>
                <td>
                {
                if($installed) then
                  <a href="?panel=repo&amp;action=deactivate&amp;name={$package-name}">deactivate</a>

                 else
                 ( <a href="?panel=repo&amp;action=activate&amp;name={$package-name}">activate</a>,' | ',
                 <a href="?panel=repo&amp;action=remove&amp;name={$package-name}">remove</a>
                 )
                }

                </td>
             </tr>
            }
                </table>
               <br/><br/><br/> <br/>
                <table>
                    <!--tr>
                        <td><input type="submit" name="action" value="store"/></td>
                        <td>Path to file on server:<br/>
                        <input type="text" name="uri" size="40"/></td>
                        <td>Store as:<br/>
                        <input type="text" name="name" size="20"/></td>
                    </tr-->
                    <tr>
                        <td><input type="submit" name="action" value="Upload Package"/></td>
                        <td><input type="file" size="30" name="upload"/></td>
                    </tr>
                </table>
                <p> You can find example .xar packages located under webapp/repo/packages</p>
                <p><i>Note: link to eXist-db own public package repository coming soon ....</i>    </p>

          </form>
        </div>
};
