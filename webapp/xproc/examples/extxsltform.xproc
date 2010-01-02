<p:pipeline xmlns="http://www.w3.org/1999/xhtml"
    xmlns:c="http://www.w3.org/ns/xproc-step"
    xmlns:p="http://www.w3.org/ns/xproc"
    xmlns:ext="http://xproc.net/xproc/ext"
name="pipeline">
   <ext:xsltforms>
       <p:input port="source">
           <p:document href="/db/xproc/examples/xform.xml"/>
       </p:input>
   </ext:xsltforms>
</p:pipeline>   