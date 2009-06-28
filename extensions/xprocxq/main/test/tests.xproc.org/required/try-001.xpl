<p:pipeline name="pipeline" xmlns:p="http://www.w3.org/ns/xproc">

<p:try name="try">
  <p:group>
    <p:output port="result"/>
    <p:compare fail-if-not-equal="true">
      <p:input port="alternate">
	<p:inline><doc/></p:inline>
      </p:input>
    </p:compare>
    <p:identity>
      <p:input port="source">
	<p:inline><p>p:compare succeeded</p></p:inline>
      </p:input>
    </p:identity>
  </p:group>
  <p:catch>
    <p:output port="result"/>
    <p:identity>
      <p:input port="source">
	<p:inline><p>p:compare failed</p></p:inline>
      </p:input>
    </p:identity>
  </p:catch>
</p:try>

</p:pipeline>
