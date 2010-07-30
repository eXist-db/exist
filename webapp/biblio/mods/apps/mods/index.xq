xquery version "1.0";

(: MODS XRX Application :)

import module namespace style = "http://www.danmccreary.com/library" at "../../modules/style.xqm";

let $title := 'MODS XRX Demo'

let $content := 
    <div class="content">
       <p>Welcome to the Item Manager.</p>
       <div class="debug">
          $style:site-images={$style:site-images}<br/>
       </div>
       <p>This demo uses eXist and XForms to allows you to perform basic database operations CRUD (Create, Read/Search, Update, Delete) operations on Items:
          <ol>
              <li>
                  <a href="views/list-items.xq">List</a> List of all Items
              </li>
              <li>
                  <a href="edit/edit.xq?new=true&amp;tab=title">New</a> Create New Item (Titles)
              </li>
              <li>
                  <a href="search/search.xq">Search</a> Search for Items
              </li>
              <li>
                  <a href="views/list-categories.xq">List Categories</a> List all Item Classifiers including status, category and tags
              </li>
              <li>
                  <a href="views/metrics.xq">Item Metrics</a> Counts of various Item Metrics
              </li>
          </ol>
          
          <ol>
              <h3>XML Web Services</h3>
              <li>
                  <a href="edit/all-codes.xq">Code Table Web Service</a> This is a list of all the codes that will be used in the edit forms.
              </li>
              <li>
                  <a href="schemas/get-enumerated-values.xq">Enums from XML Schema</a> A tool to dump all the XML Schema enumerations
                  and put them in the XForms item/value/label format.
              </li>
         </ol>
         
         <ol>
              <h3>Administrative Tools</h3>
              <li>
                  <a href="admin/list-code-tables.xq">List Code Tables</a> This is a list of all the codes that will be used in the edit forms.
              </li>
              
         </ol>
         
          <ol>
              <h3>Analysis Reports</h3>
              <li>
                  <a href="analysis/first-level-elements.xq">First Level Elements</a> A report of all of the first level
                  elements used in our sample data for creating new instances.
              </li>
              <li>
                  <a href="analysis/tab-report.xq">Tab Report</a> A database report of each tab and the path expressions used.
              </li>
              <li>
                  <a href="analysis/forms-body-metrics.xq">Forms Body Report</a> Analysis of each tab body of the form.
              </li>
         </ol>
         
         
        </p>
     </div>
     
return
    style:assemble-page($title, $content)