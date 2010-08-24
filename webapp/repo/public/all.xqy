xquery version "1.0";

declare option exist:serialize "method=xml media-type=text/xml indent=yes omit-xml-declaration=no";

declare variable $repo-packages-uri :=  concat("http://",request:get-hostname(),':',request:get-server-port(),'/exist/rest/db/repo/');

declare variable $repo-coll := "/db/repo";

let $feed := request:get-parameter("feed", ())
return
if ($feed) then
    <feed xmlns="http://www.w3.org/2005/Atom">
        <title>eXist Public Repository Feed</title>
        <link href="{concat("http://",request:get-hostname(),':',request:get-server-port(),'/exist/repo/public/all/')}" rel="self" />
        <link href="http://{request:get-hostname()}"/>
        <id>urn:uuid:76c60a80-9d39-1d19-91Cb-1390af3900e6</id>
        <author>
            <name>eXist-db</name>
        </author>
        {

            let $repocol :=  if (collection($repo-coll)) then () else xmldb:create-collection('/db','repo')
            let $files := if (collection($repo-coll)) then collection($repo-coll)/util:document-name(.) else ()
            for $file in $files[ends-with(.,'.xar')]
            return
                <entry>
                    <title>{$file}</title>
                    <link href="{$repo-packages-uri}{$file}" />
                    <link rel="alternate" type="text/html" href="{$repo-packages-uri}{$file}"/>
                    <summary>eXistdb repo package: {$file}</summary>
                </entry>

        }
    </feed>
else 
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

