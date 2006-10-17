<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!--
        Patch Cocoon's gump.xml file. Add project definition for the eXist block.
        We also need to patch the xmldb block as it uses an incompatible XML:DB
        version.
     -->
    <xsl:output method="xml" indent="yes"/>

    <xsl:template match="module">
        <module>
            <xsl:copy-of select="@*"/>
            <xsl:apply-templates/>

            <xsl:if test="not(project[@name='cocoon-block-exist'])">
                <project name="cocoon-block-exist" status="stable" dir="src/blocks/exist">
                    <url href="http://exist-db.org"/>
                    <description>
                        eXist native XML database
                    </description>

                    <package>org.apache.cocoon</package>

                    <ant target="gump-block">
                        <property name="block-name" value="exist"/>
                        <property name="version" value="@@DATE@@"/>
                    </ant>

                    <depend project="cocoon" inherit="all"/>
                    <depend project="cocoon-block-xmldb"/>
                    <depend project="antlr"/>
                    <depend project="commons-pool"/>
                    <depend project="commons-fileupload"/>

                    <work nested="tools/anttasks"/>
                    <home nested="build/cocoon-@@DATE@@"/>

                    <library name="exist"/>
                    <library name="exist-optional"/>
                    <library name="exist-modules"/>
                    <library name="start"/>
                    <library name="antlr"/>
                    <library name="commons-pool"/>
                    <library name="xmlrpc-1.2-patched"/>
                    <library name="commons-fileupload"/>
                    <library name="excalibur-cli"/>
                    <library name="jgroups-all"/>
                    <library name="jEdit-syntax"/>
                    <library name="jline"/>
                    <library name="resolver"/>
                </project>
            </xsl:if>
        </module>
    </xsl:template>

    <xsl:template match="project[@name = 'cocoon-block-xmldb']">
        <project>
            <xsl:apply-templates select="@*|*[local-name(.) != 'library']"/>
            <library name="xmldb"/>
            <library name="xindice"/>
        </project>
    </xsl:template>

    <xsl:template match="*|@*|node()|comment()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()|comment()"/>
        </xsl:copy>
    </xsl:template>
</xsl:transform>