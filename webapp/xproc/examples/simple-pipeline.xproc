<p:declare-step xmlns:p="http://www.w3.org/ns/xproc" name="simple-pipeline">
    <p:xslt name="step1">
        <p:input port="source">
            <p:pipe step="simple-pipeline" port="source"/>
        </p:input>
        <p:input port="stylesheet">
            <p:document href="/db/xproc/examples/stylesheet.xml"/>
        </p:input>
    </p:xslt>
</p:declare-step>
