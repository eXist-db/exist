xquery version "1.0";

import module namespace file="http://exist-db.org/xquery/file";

declare option exist:serialize "method=xml media-type=text/xml indent=yes omit-xml-declaration=no";

declare variable $repo-packages-uri := "http://127.0.0.1:8080/exist/rest/db/repo/";
declare variable $repo-coll := "/db/repo";

<packages repo-url="{$repo-packages-uri}" description="package repository">
    {
    let $repocol :=  if (collection($repo-coll)) then () else xmldb:create-collection('/db','repo')    
    let $files := if (collection($repo-coll)) then collection($repo-coll)/util:document-name(.) else ()
    for $file in $files[ends-with(.,'.xar')]
    return
            <package name="{$file}">
            <url>{$repo-packages-uri}{$file}</url>
            </package>
    }
</packages>

