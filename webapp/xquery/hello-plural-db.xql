xquery version "1.0";
(: $Id: hello-plural-db.xq 6743 2007-10-19 14:42:59Z deliriumsky $ :)

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

let $name := request:get-parameter("name", "")

(: The first time store the document holding the names :)
let $collection := "xmldb:exist:///db",
$dummy := 
 if ( not ( doc( "/db/names.xml" )) ) then
  xmldb:store($collection, "names.xml", <names/> )
else <dummy/>

(: Create an XUpdate document :)
let $xupdate :=
<xupdate:modifications version="1.0"
  xmlns:xupdate="http://www.xmldb.org/xupdate" >
  <xupdate:append select=" doc( '/db/names.xml' )/names">
    <xupdate:element name="name">{$name}</xupdate:element>
  </xupdate:append>
</xupdate:modifications>

let $dummy2 := 
 if ( $name and not ( doc( "/db/names.xml" )/names/name = $name ) ) then
xmldb:update($collection, $xupdate)
  else <dummy/>
let $names-from-db := doc( "/db/names.xml" )
let $names := $names-from-db
return 
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>XQuery-XUpdate Example</title>
  </head>
<body>
  <form method="GET">
    Please enter your name: 
    <input type="text" size="40" name="name" />
    <input type="submit" />
  </form>
  
  Hello { $names } !

<br/><a href="source/hello-plural-db.xq">See the XQuery source of this page</a>
</body>
</html>
