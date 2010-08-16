xquery version "1.0";

(: MODS XRX Application :)

import module namespace style = "http://exist-db.org/mods-style" at "modules/style.xqm";

let $title := 'Library Management System Demo XRX Applications'

let $content := 
<div class="content">
   <p>Welcome to the Library Management System eXist Demo Applications.</p>
   <p>This site uses eXist and XForms to allows you to perform basic database operations 
   CRUDS (Create, Read/Search, Update, Delete and Search) operations on MODS records and related items:
      
     <ol>
          <h3>Apps</h3>
          <li>
              <a href="apps/index.xq">List of Apps</a> A list of demo applications
          </li>
     </ol>
    </p>
 </div>
     
return
    style:assemble-page($title, $content)