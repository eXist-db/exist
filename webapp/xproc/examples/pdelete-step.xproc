<p:declare-step xmlns:c="http://www.w3.org/ns/xproc-step"
xmlns:p="http://www.w3.org/ns/xproc"
xmlns:ex="http://example.org" version='1.0' name="pipeline">
   <p:input port="source"/>
   <p:output port="result"/>

<p:delete match="ex:a"/>

</p:declare-step>