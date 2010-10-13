xquery version "1.0";

(: MODS XRX Application :)

import module namespace style = "http://exist-db.org/mods-style" at "../../modules/style.xqm";

let $title := 'MODS Application Index Page'

let $content := 
    <div class="content">
       <p>Welcome to the eXist MODS Application.</p>
       <p>This demonstration application uses eXist with XQuery and XForms to allow you to perform basic database operations operations on MODS records:</p>
          <ul>
              <li>
                  <a href="../../../../../../biblio/mods/apps/mods/search/index.xml">Search</a> Search for MODS records
              </li>
              
              <li>
                  <a href="views/list-items.xq">List</a> List all MODS records created with the MODS editor.
              </li>
              <li>
                  <a href="views/document-types.xq">Create New Record From Template</a> Create a new MODS record using a predefined template
              </li>
              <li>
                  <a href="edit/edit.xq?id=new">Create New Default Record</a> Create a new MODS record using default tabs
              </li>
              <!--
              <li>
                  <a href="edit/edit.xq?id=new&amp;user=dan">New in {xmldb:get-current-user()} Home</a> Create a new MODS record using user data collection
              </li>
              -->
              <li>
                  <a href="edit/edit.xq?id=new&amp;show-all=true">Create New Full Record</a> Create a new MODS record will all tabs
              </li>
              <li>
                  <a href="admin/index.xq">Admin Page</a>.
              </li>
              
              
          </ul>
     </div>
     
return
    style:assemble-page($title, $content)