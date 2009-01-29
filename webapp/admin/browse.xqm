xquery version "1.0";
(: $Id$ :)
(:
    Module: display and browse collections.
:)

module namespace browse="http://exist-db.org/xquery/admin-interface/browse";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace v="http://exist-db.org/versioning";

import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "dates.xqm";

(:
    Main function: outputs the page.
:)
declare function browse:main() as element()
{
    let $colName := request:get-parameter("collection", "/db") return
        <div class="panel">
            { browse:process-action($colName) }
            <div class="panel-head">Browsing Collection: {xdb:decode-uri(xs:anyURI($colName))}</div>
            <form method="POST" enctype="multipart/form-data">
                {
                    browse:display-collection($colName)
                }
                <table class="actions" cellspacing="0">
                    <tr><td colspan="3"><input type="submit" name="action" value="Remove Selected"/></td></tr>
                
                    <tr>
                        <td><input type="submit" name="action" value="Create Collection"/></td>
                        <td>New collection:<br/>
                        <input type="text" name="create" size="40"/></td>
                        <td/>
                    </tr>
                    
                    <!--tr>
                        <td><input type="submit" name="action" value="Store"/></td>
                        <td>Path to file on server:<br/>
                        <input type="text" name="uri" size="40"/></td>
                        <td>Store as:<br/>
                        <input type="text" name="name" size="20"/></td>
                    </tr-->
                    <tr>
                        <td><input type="submit" name="action" value="Upload"/></td>
                        <td><input type="file" size="30" name="upload"/></td>
                        <td>Store as:<br/>
                        <input type="text" name="name" size="20"/></td>
                    </tr>
                </table>
                
                <input type="hidden" name="collection" value="{$colName}"/>
                <input type="hidden" name="panel" value="browse"/>
            </form>
        </div>
};

(:
    Process an action.
:)
declare function browse:process-action($colName as xs:string) as element()*
{
    let $action := request:get-parameter("action", ()) return
        util:catch("java.lang.Exception",
            if($action eq "Remove Selected") then
            (
                browse:remove()
            )
            else if($action eq "Create Collection") then
            (
                browse:create-collection($colName)
            )
            else if($action eq "Store") then
            (
                browse:store($colName)
            )
            else if($action eq "Upload") then
            (
                browse:upload($colName)
            )else(),
            
            <div class="error">
                An error occurred while processing the action:<br/>
                {$util:exception-message}
            </div>
        )
};

(:
    Store uploaded content.
:)
declare function browse:upload($colName as xs:string) as element()
{
    let $name := request:get-parameter("name", ()),
    $docName := if($name) then $name else request:get-uploaded-file-name("upload"),
    $file := request:get-uploaded-file("upload") return
    
        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>Storing uploaded content to: {$docName}</li>
                {
                    xdb:decode-uri(xs:anyURI(xdb:store($colName, xdb:encode-uri($docName), $file)))
                }
            </ul>
    </div>
};

(:
    Store files from an URI.
    
    Allowing this opens a security whole as a user can
    upload arbitrary files on the server if the process is running
    as root.
:)
declare function browse:store($colName as xs:string) as element()
{
    let $uri := request:get-parameter("uri", ()),
    $path := if(starts-with($uri, "file:")) then $uri else concat("file:", $uri),
    $docName := request:get-parameter("name", ()) return
        
        <div class="process">
            <h3>Actions:</h3>
            <ul>
                <li>Storing resources from URI: {$path}</li>
                {
                    xdb:store($colName, $docName, xs:anyURI($path))
                }
            </ul>
        </div>
};

(:
    Remove a set of resources.
:)
declare function browse:remove() as element()
{
    let $resources := request:get-parameter("resource", ()) return
        
        <div class="process">
            <h3>Actions:</h3>
            <ul>
                {
                    for $resource in $resources
                    return
                        browse:remove-resource($resource)
                }
            </ul>
        </div>
};

(:
    Remove a resource.
:)
declare function browse:remove-resource($resource as xs:string) as element()* 
{
    let $isBinary := util:binary-doc-available($resource),
    $doc := if ($isBinary) then $resource else doc($resource) return
        
        if($doc)then
        (
            <li>Removing document: {xdb:decode-uri(xs:anyURI($resource))} ...</li>,
            xdb:remove(util:collection-name($doc), util:document-name($doc))
        )
        else
        (
            <li>Removing collection: {xdb:decode-uri(xs:anyURI($resource))} ...</li>,
            xdb:remove($resource)
        )
};

(:
    Create a collection.
:)
declare function browse:create-collection($parentColName as xs:string) as element()
{
    let $newcol := request:get-parameter("create", ()) return
        
        <div class="process">
            <h3>Actions:</h3>
            <ul>
            {
                if($newcol) then
                (
                    let $col := xdb:create-collection($parentColName, xdb:encode-uri($newcol)) return
                        <li>Created collection: {xdb:decode-uri(xs:anyURI(util:collection-name($col)))}.</li>
                )
                else
                (
                    <li>No name specified for new collection!</li>
                )
            }
            </ul>
        </div>
};

(:
    Display the contents of a collection in a table view.
:)
declare function browse:display-collection($colName as xs:string) as element()
{
    <table cellspacing="0" cellpadding="5" class="browse">
        <tr>
            <th/>
            <th>Name</th>
            <th>Permissions</th>
            <th>Owner</th>
            <th>Group</th>
            <th>Created</th>
            <th>Modified</th>
            <th>Size (KB)</th>
			<th>Revision</th>
        </tr>
        <tr>
            <td/>
            <td><a href="?panel=browse&amp;collection={browse:get-parent-collection(xdb:encode-uri($colName))}">Up</a></td>
            <td/>
            <td/>
            <td/>
            <td/>
            <td/>
            <td/>
			<td/>
        </tr>
        {
            browse:display-child-collections($colName),
            browse:display-child-resources($colName)
        }
    </table>
};

declare function browse:display-child-collections($colName as xs:string) as element()*
{
    for $child in xdb:get-child-collections($colName)
        let $path := concat($colName, '/', $child),
        $created := xdb:created($path)
    order by $child return
        <tr>
            <td><input type="checkbox" name="resource" value="{$path}"/></td>
            <td><a href="?panel=browse&amp;collection={xdb:encode-uri($path)}">{xdb:decode-uri(xs:anyURI($child))}</a></td>
            <td class="perm">{xdb:permissions-to-string(xdb:get-permissions($path))}</td>
            <td>{xdb:get-owner($path)}</td>
            <td>{xdb:get-group($path)}</td>
            <td>{date:format-dateTime($created)}</td>
            <td/>
            <td/>
			<td/>
        </tr>
};

declare function browse:display-child-resources($colName as xs:string) as element()* 
{
    for $child in xdb:get-child-resources($colName)
    let $restUri := concat(request:get-context-path(), '/rest')
    order by $child 
	return
        <tr>
            <td><input type="checkbox" name="resource" value="{$colName}/{$child}"/></td>
            <td><a target="_new" href="{$restUri}/{xdb:encode-uri($colName)}/{xdb:encode-uri($child)}">{xdb:decode-uri(xs:anyURI($child))}</a></td>
            <td class="perm">{xdb:permissions-to-string(xdb:get-permissions($colName, $child))}</td>
            <td>{xdb:get-owner($colName, $child)}</td>
            <td>{xdb:get-group($colName, $child)}</td>
            <td>{date:format-dateTime(xdb:created($colName, $child))}</td>
            <td>{date:format-dateTime(xdb:last-modified($colName, $child))}</td>
            <td>{fn:ceiling(xdb:size($colName, $child) div 1024)}</td>
			<td>
				<a href="?panel=revisions&amp;resource={$colName}/{$child}">
				{browse:last-revision($colName, $child)//v:properties/v:revision/string()}
				</a>
			</td>
        </tr>
};

declare function browse:last-revision($collection as xs:string, $resource as xs:string) {
	let $vCollection := concat("/db/system/versions", $collection)
	let $versions :=
		for $version in collection($vCollection)/v:version[
			v:properties[v:document = $resource]
		]
		order by xs:long($version/v:properties/v:revision) descending
		return $version
	return
		$versions[1]
};

(:
    Get the name of the parent collection from a specified collection path.
:)
declare function browse:get-parent-collection($path as xs:string) as xs:string
{
    if($path eq "/db") then
        $path
    else
        replace($path, "/[^/]*$", "")
};
