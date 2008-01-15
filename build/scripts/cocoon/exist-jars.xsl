<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <!-- Fix Cocoon's jars.xml file. We need to add eXist's jars and remove
          the XML:DB API source jars that come with Cocoon. eXist needs newer
          versions of these jars.
     -->
    <xsl:output method="xml" indent="yes"/>

    <xsl:template match="jars">
        <jars>
            <xsl:apply-templates/>
            <xsl:if test="not(file[title = 'exist-optional'])">
                <file>
                    <title>exist</title>
                    <description>Core libraries for the eXist native XML db</description>
                    <used-by>exist</used-by>
                    <lib>optional/exist.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>exist-optional</title>
                    <description>Optional libraries for the eXist native XML db</description>
                    <used-by>exist</used-by>
                    <lib>optional/exist-optional.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>exist-modules</title>
                    <description>XQuery extension modules for the eXist native XML db</description>
                    <used-by>exist</used-by>
                    <lib>optional/exist-modules.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>exist-ngram</title>
                    <description>Optional libraries for the eXist native XML db:
                        n-gram index module.</description>
                    <used-by>exist</used-by>
                    <lib>optional/exist-ngram-module.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>eXist bootstrap loader</title>
                    <description>Bootstrap loader for the eXist native XML db</description>
                    <used-by>exist</used-by>
                    <lib>optional/start.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>XML:DB APIs</title>
                    <description>The XML:DB APIs</description>
                    <used-by>exist</used-by>
                    <lib>optional/xmldb.jar</lib>
                    <homepage>http://xmldb-org.sourceforge.net/</homepage>
                </file>
                <file>
                    <title>Commons File Upload</title>
                    <description>Apache commons library for file uploads</description>
                    <used-by>exist</used-by>
                    <lib>optional/commons-fileupload-1.1.1.jar</lib>
                    <homepage>http://jakarta.apache.org/commons/fileupload/</homepage>
                </file>
                <file>
                    <title>Excalibur CLI</title>
                    <description>Excalibur command-line argument parser</description>
                    <used-by>exist</used-by>
                    <lib>optional/excalibur-cli-1.0.jar</lib>
                    <homepage>http://jakarta.apache.org/</homepage>
                </file>
                <file>
                    <title>JGroups</title>
                    <description>JGroups</description>
                    <used-by>exist</used-by>
                    <lib>optional/jgroups-all.jar</lib>
                    <homepage>http://jgroups.org/</homepage>
                </file>
                <file>
                    <title>jEdit Syntax</title>
                    <description>jEdit syntax highlighter</description>
                    <used-by>exist</used-by>
                    <lib>optional/jEdit-syntax.jar</lib>
                    <homepage>http://jedit.org/</homepage>
                </file>
                <file>
                    <title>JLine</title>
                    <description>JLine</description>
                    <used-by>exist</used-by>
                    <lib>optional/jline-0_9_5.jar</lib>
                    <homepage>http://jline.sourceforge.net/</homepage>
                </file>
                <file>
                    <title>Resolver</title>
                    <description>Resolver</description>
                    <used-by>exist</used-by>
                    <lib>optional/resolver-1.2.jar</lib>
                    <homepage>http://jedit.org/</homepage>
                </file>
                <file>
                    <title>StAX</title>
                    <description>StAX interfaces</description>
                    <used-by>exist</used-by>
                    <lib>endorsed/stax-api-1.0.1.jar</lib>
					<homepage>http://stax.codehaus.org/</homepage>
                </file>
            </xsl:if>
        </jars>
    </xsl:template>

    <xsl:template match="file[starts-with(lib, 'optional/xmldb-')]">
        <!-- drop the existing XML:DB entries -->
    </xsl:template>

    <!-- Use patched jar for XML-RPC -->
    <xsl:template match="file[title = 'XML-RPC']/lib">
        <lib>optional/xmlrpc-1.2-patched.jar</lib>
    </xsl:template>

    <!-- Use patched jar for XML-RPC -->
    <xsl:template match="file[title = 'Log4j']/lib">
        <lib>core/log4j-1.2.15.jar</lib>
    </xsl:template>

    <xsl:template match="*|@*|node()|comment()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()|comment()"/>
        </xsl:copy>
    </xsl:template>

</xsl:transform>
