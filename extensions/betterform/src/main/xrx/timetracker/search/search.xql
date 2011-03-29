xquery version "1.0";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes";

let $data-collection := '/db/betterform/apps/timetracker/data/task'
let $q := request:get-parameter('q', "")

(: put the search results into memory using the eXist any keyword ampersand equals comparison :)
let $search-results := collection($data-collection)/*/task[ft:query(*, $q)]
let $count := count($search-results)

return
<html>
    <head>
       <title>Term Search Results</title>
     </head>
     <body>
        <h3>Term Search Results</h3>
        <p><b>Search results for:</b>&quot;{$q}&quot; <b> In Collection: </b>{$data-collection}</p>
        <p><b>Tasks Found: </b>{$count}</p>
     <ol>{
           for $task in $search-results
              let $created := $task/created
              let $note := $task/note
              order by $created
          return
            <li>
               <a href="../edit/edit-item.xql?timestamp={$created}&amp;mode=edit">{$created}</a>
            </li>
      }</ol>
      <a href="search-form.html">New Search</a><br/>
      <a href="../views/list-items.xql">App Home</a>
   </body>
</html>
