<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step"
   xmlns:p="http://www.w3.org/ns/xproc"
   xmlns:xproc="http://xproc.net/xproc" name="aaa">>
   <p:input port="source"/>
   <p:output port="result"/>
   <p:xquery>
       <p:input port="source"/>
       <p:input port="query">
           <p:inline>
               <c:query>
                   request:get-parameter-names()
               </c:query>
           </p:inline>
       </p:input>
   </p:xquery>
</p:pipeline>