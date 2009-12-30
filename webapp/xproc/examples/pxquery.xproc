<p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc"
            xmlns:c="http://www.w3.org/ns/xproc-step">
<p:xquery>
   <p:input port="query">
       <p:inline>
           <c:query xmlns:c="http://www.w3.org/ns/xproc-step" xproc:escape="true">
               let $r := 'this pipeline successfully processed' return $r 
           </c:query>
       </p:inline>
   </p:input>
</p:xquery>
</p:pipeline>