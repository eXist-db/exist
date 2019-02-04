xquery version "1.0";

declare namespace compression = "http://exist-db.org/xquery/compression";
declare namespace util = "http://exist-db.org/xquery/util";

(:~
:
: Simple example showing how to use compression:unzip()
:
: @author Adam Retter <adam@exist-db.org>
:)


declare function local:entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{
    (: TODO - Replace this code with your implementation e.g. you may want to store the data :)

	<entry>
		<path>{$path}</path>
		<type>{$type}</type>
		<data>{$data}</data>
	</entry>
};

declare function local:entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean
{
    (: TODO - Replace this code with your implementation :)

	true()
};

let $zip := util:binary-doc("/db/example.zip") return
	compression:unzip($zip, util:function(xs:QName("local:entry-filter"), 3), (),  util:function(xs:QName("local:entry-data"), 4), ())