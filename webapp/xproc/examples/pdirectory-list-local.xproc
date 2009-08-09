<p:pipeline name="pipeline"
            xmlns:p="http://www.w3.org/ns/xproc"
            xmlns:c="http://www.w3.org/ns/xproc-step">
    <!-- NOTE - requires existdb File module to be enabled //-->
    <p:directory-list path="file://." include-filter='*.*'/>
</p:pipeline>
