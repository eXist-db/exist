<?xml version="1.0" encoding="UTF-8"?>
<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step"
xmlns:p="http://www.w3.org/ns/xproc" name="test">
    <ext:xproc xmlns:ext="http://xproc.net/xproc/ext" name="test">
        <p:input port="source"></p:input>
        <p:input port="pipeline">
            <p:inline>
                <p:declare-step name="mytest">
                    <p:input port="source"/>
                    <p:output port="result"/>
                    <p:count name="nothertest"/>
                </p:declare-step>
            </p:inline>
        </p:input>
    </ext:xproc>
</p:pipeline>