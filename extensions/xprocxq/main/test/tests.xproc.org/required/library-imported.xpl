<?xml version="1.0" encoding="UTF-8"?>

<p:library xmlns:p="http://www.w3.org/ns/xproc" xmlns:test="http://acme.com/test">

  <!-- pipeline -->
  <p:declare-step type="test:pipeline" xmlns:test2="http://acme.com/test2">
    <p:input port="source"/>
    <p:output port="result"/>
    
    <p:declare-step type="test2:test-step">
      <p:input port="source"/>
      <p:output port="result"/>
      <p:identity/>
    </p:declare-step>
  
    <p:try>
      <p:group>
        <test2:test-step/>
        <p:identity>
          <p:input port="source">
            <p:inline><been-there/></p:inline>
          </p:input>
        </p:identity>
      </p:group>
      <p:catch name="catch">
        <p:group>
          <p:identity>
            <p:input port="source">
              <p:pipe step="catch" port="error"/>
            </p:input>
          </p:identity>
        </p:group>
      </p:catch>
    </p:try>
  </p:declare-step>

  <p:declare-step type="test:test-step">
    <p:input port="source"/>
    <p:output port="result"/>
    <p:identity/>
  </p:declare-step>
  
</p:library>