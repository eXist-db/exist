xquery version "1.0";

declare namespace request="http://exist-db.org/xquery/request";

let $name := request:request-parameter("name", "")
let $names := ( request:get-session-attribute("names"), $name )
let $dummy := request:set-session-attribute("names", $names)
return 
<html>
  <form method="GET">
    Please enter your name: 
    <input type="text" size="40" name="name" />
    <input type="submit" />
  </form>
  
  Hello { $names } !

</html>
