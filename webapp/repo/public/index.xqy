xquery version "1.0";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace package="http://expath.org/ns/pkg";

declare variable $repo-coll := "/db/repo";

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

declare function local:process-action() as element()*
{
    let $repocol :=  if (collection($repo-coll)) then () else xmldb:create-collection('/db','repo')
    let $action := request:get-parameter("action", ())
    return
        util:catch("java.lang.Exception",
            if($action eq "Upload Package") then
              local:upload()
            else
              (),
            <div class="error">
                An error occurred while processing the action:<br/>
                {$util:exception-message}
            </div>
        )
};

declare function local:upload() as element()
{
    let $name := request:get-parameter("name", ()),
    $docName := if($name) then $name else request:get-uploaded-file-name("upload"),
    $file := request:get-uploaded-file-data("upload") return

        <div class="process">
            <h3>Actions:</h3>
            <ul>
                {
                if (contains($docName,'.xar')) then
                    (<li>uploaded package: {$docName}</li>,
                   xdb:decode-uri(xs:anyURI(xdb:store($repo-coll, xdb:encode-uri($docName), $file)))
                   ,
                    let $meta := compression:unzip($file, util:function(xs:QName("local:entry-filter"), 3), (),  util:function(xs:QName("local:entry-data"), 4), ())
                    let $package := $meta//package:package
                    let $repo := $meta//repo:meta
                    return
                     xdb:store($repo-coll, concat(substring-before($docName,'.xar'),'.xml'), <meta>{$package}{$repo}</meta>)
                   )
                else
                    <li><span style="color:#FF2400">Error uploading - Must be a valid Package archive (.xar file extension)</span></li>
                }
            </ul>
    </div>
};

<html>
    <body>
    <h1><a href=".">Public Repository</a></h1>

    {local:process-action()}
    <h2>Package Listing</h2>
    <table>
     <tr>
            <th>Package Name</th>
            <th>Description</th>
            <th>Date Created</th>
            <th>Author</th>
            <th>License</th>
            <th>Website</th>

     </tr>
     {
     let $files := if (collection($repo-coll)) then collection($repo-coll)/util:document-name(.) else ()
     return
       for $file in $files[contains(.,'.xar')]
       let $name := substring-before($file,'.xar')
       let $package := document(concat($repo-coll,'/',$name,'.xml'))
       return
        <tr>
            <td><a href="{$package//repo:website}" target="website">{$name}</a></td>
            <td>{$package//package:title/text()}</td>
            <td>{xmldb:created($repo-coll, $file)}</td>
            <td>{$package//repo:author}</td>
            <td>{$package//repo:license}</td>
            <td>{$package//repo:website}</td>

        </tr>

     }
     
    </table>

    <h2>Add Package</h2>
    <form method="POST" enctype="multipart/form-data">
    <table>
    <tr>
        <td><input type="submit" name="action" value="Upload Package"/></td>
        <td><input type="file" size="30" name="upload"/></td>
    </tr>
    </table>
    </form>

    </body>
</html>