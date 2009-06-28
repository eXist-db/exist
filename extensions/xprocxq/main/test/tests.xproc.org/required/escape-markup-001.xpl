<p:declare-step name="main" xmlns:p="http://www.w3.org/ns/xproc">
<p:output port="result"/>

<p:escape-markup>
  <p:input port="source">
    <p:inline><body-wrapper><doc><p>foo&amp;bar;</p></doc></body-wrapper></p:inline>
  </p:input>
</p:escape-markup>

</p:declare-step>
