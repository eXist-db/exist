<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc"
   xmlns:xproc="http://xproc.net/xproc" xmlns:t="http://exist-db.org/twitter" name="pipeline">

   <p:declare-step type="t:query-stored">
       <p:output port="result" primary="true"/>
       <p:xquery>
           <p:input port="query">
               <p:inline><c:query>&lt;a/&gt;</c:query></p:inline>
           </p:input>
       </p:xquery>
   </p:declare-step>

   <t:query-stored name="test"/>
</p:pipeline>