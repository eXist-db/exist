xquery version "1.0";

declare namespace expath="http://expath.org/ns/pkg";

declare option exist:serialize "method=json media-type=application/json";

declare function local:entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{
 $data
};

declare function local:entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean
{
	$path = ("expath-pkg.xml")
};

declare function local:get-descriptors($collection, $name) {
    let $dataCb := util:function(xs:QName("local:entry-data"), 4)
    let $entryCb := util:function(xs:QName("local:entry-filter"), 3)
    return
        compression:unzip(util:binary-doc(concat($collection, "/", $name)), $entryCb, (), $dataCb, ())
};

declare function local:deploy($collection, $name) {
let $deploy := request:get-parameter("deploy", ())
return
    if ($deploy and ends-with($name, ".xar")) then 
        let $descriptors := local:get-descriptors($collection, $name)
        let $port := request:get-server-port()
        let $url := concat('http://localhost:',$port,'/exist/rest',$collection, "/", $name)
        let $appName := $descriptors/expath:package/@name
        return (
            repo:remove($appName),
            repo:install($url),
            repo:deploy($appName)
        )
    else
        ()
};

declare function local:upload($collection, $name, $data) {
    let $path := xmldb:store($collection, $name, $data)
    let $upload :=
        <result>
           <name>{$name}</name>
           <type>{xmldb:get-mime-type($path)}</type>
           <size>93928</size>
       </result>
    let $deploy := local:deploy($collection, $name)
    return
        $upload
};

let $collection := request:get-parameter("collection", ())
let $name := request:get-uploaded-file-name("file[]")
let $data := request:get-uploaded-file-data("file[]")
return
    util:catch("*",
        local:upload($collection, xmldb:encode($name), $data),
        <result>
           <name>{$name}</name>
           <error>{$util:exception-message}</error>
        </result>
   )
