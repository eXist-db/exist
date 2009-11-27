<?xml version="1.0" encoding="UTF-8"?>
<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc" name="pipeline">
<p:identity>
<p:input port="source">
    <p:pipe step="pipeline" port="a"/>
</p:input>
</p:identity>
</p:pipeline>
