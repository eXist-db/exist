xquery version "1.0";

(: MODS XRX Application :)

import module namespace style = "http://exist-db.org/mods-style" at "../../modules/style.xqm";

let $title := 'MODS XRX Application'

let $content := 
    <div class="content">
       <p>Welcome to the MODS XRX Application.</p>
       <p>This demonstration application uses eXist and XForms to allows you to perform basic database operations CRUDS (Create, Read/Search, Update, Delete and Search) operations on MODS records:
          <ol>
              <!--
              <li>
                  <a href="search/search.xq">Search</a> Search for MODS records
              </li>
              -->
              <li>
                  <a href="views/list-items.xq">List</a> List of all MODS records in test collection sorted by document title
              </li>
              <li>
                  <a href="edit/edit.xq?id=new">New</a> Create a new MODS record using default tabs
              </li>
              
              <li>
                  <a href="edit/edit.xq?id=new&amp;show-all=true">New Full</a> Create a new MODS record will all tabs
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
         
         <ol>
              <h3>Unit Tests</h3>
              <li>
                  <a href="unit-tests/edit-body-div-attribute-test.xq">Edit Body Files</a> A report of all of the files
                  use to build the body elements.
              </li>
         </ol>
         
         
        </p>
     </div>
     
return
    style:assemble-page($title, $content)