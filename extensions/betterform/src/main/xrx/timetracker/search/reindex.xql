xquery version "1.0";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

let $data-collection := '/db/betterform/apps/timetracker/data/task'

let $login := xmldb:login($data-collection, 'admin', 'admin')
let $start-time := util:system-time()
let $reindex := xmldb:reindex($data-collection)
let $runtime-ms := ((util:system-time() - $start-time) div xs:dayTimeDuration('PT1S'))  * 1000

return
<html>
 <head>
	<title>Reindex</title>
 </head>
 <body>
 <h1>Reindex</h1>
 <p>The index for {$data-collection} was updated in {$runtime-ms} milliseconds.</p>
 <a href="../views/list-items.xql">App Home</a>
 </body>
</html>











