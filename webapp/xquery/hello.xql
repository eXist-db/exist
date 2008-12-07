xquery version "1.0";
(: $Id: hello.xq 6434 2007-08-28 18:59:23Z ellefj $ :)
(: How to do plain dynamic Web pages with XQuery and eXist extensions :)

declare namespace request="http://exist-db.org/xquery/request";

let $name := request:get-parameter("name", " you")
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
  
  Hello { $name } !

<br/><a href="source/hello.xq">See the XQuery source of this page</a>
</body>
</html>
