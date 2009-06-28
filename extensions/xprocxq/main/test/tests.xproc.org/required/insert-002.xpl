<p:pipeline name="pipeline"
	    xmlns:p="http://www.w3.org/ns/xproc">

<p:insert match="div" position="last-child">
  <p:input port="source">
    <p:pipe step="pipeline" port="source"/>
  </p:input>
  <p:input port="insertion">
    <p:inline><p>New last paragraph</p></p:inline>
  </p:input>
</p:insert>

</p:pipeline>

