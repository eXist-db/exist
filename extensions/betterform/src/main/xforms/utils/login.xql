xquery version "3.0";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

let $user := request:get-parameter('user', 'noname')
let $password := request:get-parameter('password', 'no-password')
let $loggedIn := xmldb:login("/db", $user, $password)

return
<html>
    <body>
        <textarea>
			{
                if($loggedIn)
                then (
                    <ok/>
                ) else (
                    response:set-status-code(401),
                    <failed/>
                )
			}
        </textarea>
    </body>
</html>


