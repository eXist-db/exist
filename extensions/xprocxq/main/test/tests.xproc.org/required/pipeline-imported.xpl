<p:pipeline name="main" type="foo:imported"
            xmlns:p="http://www.w3.org/ns/xproc"
            xmlns:foo="http://acme.com/test">

  <p:identity>
    <p:input port="source">
      <p:pipe step="main" port="source"/>
    </p:input>
  </p:identity>
  
</p:pipeline>