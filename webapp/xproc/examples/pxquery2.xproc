<p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc"
            xmlns:c="http://www.w3.org/ns/xproc-step"
            xmlns:xproc="http://xproc.net/xproc">
           <p:xquery>
		               <p:input port="query">
    	               <p:data href="/db/xproc/examples/helloworld.xq"
    	                       wrapper="c:query"
    	                       content-type="plain/text"
   		                       xproc:escape="false"/>
		               </p:input>
		   </p:xquery>
</p:pipeline>