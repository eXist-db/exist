xquery version "1.0";
(: How to do plain dynamic Web pages with XQuery and eXist extensions :)

declare namespace request="http://exist-db.org/xquery/request";

let $name := request:request-parameter("name", " you")
return 
<html>
  <form method="GET">
    Please enter your name: 
    <input type="text" size="40" name="name" />
    <input type="submit" />
  </form>
  
  Hello { $name } !

</html>
