p:declare-step xmlns:foo="http://acme.com/test" xmlns:p="http://www.w3.org/ns/xproc" xmlns:xproc="http://xproc.net/xproc" name="aaa">
    <p:input port="source"/>
    <p:output port="result"/>
    <p:declare-step type="foo:bar">
        <p:input port="source"/>
        <p:count/>
    </p:declare-step>
    <foo:bar name="aaadasfads"/>
</p:declare-step>