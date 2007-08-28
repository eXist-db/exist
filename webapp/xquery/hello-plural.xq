xquery version "1.0";
(: $Id$ :)

declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";

let $name := request:get-parameter("name", "")
let $names := ( session:get-attribute("names"), $name )
let $dummy := session:set-attribute("names", $names)
return 
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
    <title>XQuery Form Example</title>
  </head>
<body>
  <form method="GET">
    Please enter your name: 
    <input type="text" size="40" name="name" />
    <input type="submit" />
  </form>
  
  Hello { $names } !

<br/><a href="source/hello-plural.xq">See the XQuery source of this page</a>
</body>
</html>
