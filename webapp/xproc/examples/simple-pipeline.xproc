<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" name="simple-pipeline">
    <p:xslt>
        <p:input port="stylesheet">
           <p:document href="xmldb:exist:///db/xproc/examples/stylesheet.xml"/>
        </p:input>
    </p:xslt>
</p:declare-step>
