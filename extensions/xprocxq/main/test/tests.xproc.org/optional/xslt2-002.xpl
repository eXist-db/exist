<p:declare-step name="main"
		xmlns:p="http://www.w3.org/ns/xproc">
<p:input port="parameters" kind="parameter"/>
<p:input port="source"/>
<p:input port="style"/>
<p:output port="result"/>

<p:xslt name="xslt2" version="2.0">
  <p:input port="source">
    <p:pipe step="main" port="source"/>
  </p:input>
  <p:input port="stylesheet">
    <p:pipe step="main" port="style"/>
  </p:input>
</p:xslt>

<p:sink/>

<p:identity>
  <p:input port="source">
    <p:pipe step="xslt2" port="secondary"/>
  </p:input>
</p:identity>

</p:declare-step>
