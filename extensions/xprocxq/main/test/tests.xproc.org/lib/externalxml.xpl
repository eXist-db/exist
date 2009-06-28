<p:pipeline name="exec-to-xml"
            type="px:exec-to-xml"
            xmlns:p="http://www.w3.org/ns/xproc"
	    xmlns:px="http://xproc.org/ns/xproc/ex"
            xmlns:c="http://www.w3.org/ns/xproc-step"
            xmlns:err="http://www.w3.org/ns/xproc-error">
<p:input port="source"/>
<p:output port="result"/>
<p:option name="command" required="true"/>
<p:option name="args" select="''"/>

<px:exec name="exec">
  <p:with-option name="command" select="$command"/>
  <p:with-option name="args" select="$args"/>
</px:exec>

<p:unescape-markup/>

<p:unwrap match="/*"/>

</p:pipeline>
