<p:pipeline xmlns:p="http://www.w3.org/ns/xproc" xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:compress="http://exist-db.org/xquery/compression"
name="pipeline">
    <p:import href="resource:net/xproc/xprocxq/lib/compression-library.xml"/>
    <compress:zip name="aaa"/>
</p:pipeline>