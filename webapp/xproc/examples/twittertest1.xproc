<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc"
   xmlns:xproc="http://xproc.net/xproc" xmlns:twitter="http://exist-db.org/twitter" name="pipeline">

   <p:declare-step type="twitter:query-stored">
       <p:output port="result" primary="true"/>
       <p:xquery>
           <p:input port="query">
               <p:inline><c:query>&lt;b/&gt;</c:query></p:inline>
           </p:input>
       </p:xquery>
   </p:declare-step>

   <twitter:query-stored name="test"/>
</p:pipeline>