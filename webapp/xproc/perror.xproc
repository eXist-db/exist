<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc" name="pipeline"><p:error xmlns:my="http://www.example.org/error"
         name="bad-document" code="my-error-code-1">
   <p:input port="source">
     <p:inline>
       <message>The document is bad for unexplained reasons.</message>
     </p:inline>
   </p:input>
</p:error>
</p:pipeline>