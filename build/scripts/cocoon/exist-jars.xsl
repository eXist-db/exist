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
                    <title>exist-versioning</title>
                    <description>Optional libraries for the eXist native XML db:
                        versioning module.</description>
                    <used-by>exist</used-by>
                    <lib>optional/exist-versioning.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>exist-xprocxq</title>
                    <description>Optional libraries for the eXist native XML db:
                        XProc module.</description>
                    <used-by>exist</used-by>
                    <lib>optional/xprocxq.jar</lib>
                    <homepage>http://exist-db.org</homepage>
                </file>
                <file>
                    <title>exist-lucene</title>
                    <description>Optional libraries for the eXist native XML db:
                        lucene index module.</description>
                    <used-by>exist</used-by>
                    <lib>optional/exist-lucene-module.jar</lib>
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
                    <lib>optional/commons-fileupload-1.2.1.jar</lib>
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
                    <lib>optional/jgroups-all-2.2.6.jar</lib>
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
                    <lib>optional/jline-0.9.94.jar</lib>
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
                    <title>XML-RPC Common</title>
                    <description>Java implementation of XML-RPC, a popular protocol that uses XML over
                        HTTP to implement remote procedure calls.</description>
                    <used-by>exist</used-by>
                    <lib>optional/xmlrpc-common-3.1.3.jar</lib>
                    <homepage>http://ws.apache.org/xmlrpc/</homepage>
                </file>
                <file>
                    <title>XML-RPC Server</title>
                    <description>Java implementation of XML-RPC, a popular protocol that uses XML over
                        HTTP to implement remote procedure calls.</description>
                    <used-by>exist</used-by>
                    <lib>optional/xmlrpc-server-3.1.3.jar</lib>
                    <homepage>http://ws.apache.org/xmlrpc/</homepage>
                </file>
                <file>
                    <title>XML-RPC Client</title>
                    <description>Java implementation of XML-RPC, a popular protocol that uses XML over
                        HTTP to implement remote procedure calls.</description>
                    <used-by>exist</used-by>
                    <lib>optional/xmlrpc-client-3.1.3.jar</lib>
                    <homepage>http://ws.apache.org/xmlrpc/</homepage>
                </file>
                <file>
                    <title>Lucene utils</title>
                    <description>
                        jakarta-lucene is a search engine toolkit designed for indexing and
                        searching of documents.
                    </description>
                    <used-by>exist</used-by>
                    <lib>optional/lucene-regex-2.4.1.jar</lib>
                    <homepage>http://lucene.apache.org/</homepage>
                </file>
                
            </xsl:if>
        </jars>
    </xsl:template>

    <xsl:template match="file[starts-with(lib, 'optional/xmldb-')]">
        <!-- drop the existing XML:DB entries -->
    </xsl:template>

    <!-- Use patched jar for XML-RPC -->
    <xsl:template match="file[title = 'XML-RPC']">
    </xsl:template>

    <xsl:template match="file[contains(title, 'Antlr')]/lib">
        <lib>optional/antlr-2.7.7.jar</lib>
    </xsl:template>
    
    <xsl:template match="file[contains(title, 'Commons Pool')]/lib">
        <lib>optional/commons-pool-1.5.4.jar</lib>
    </xsl:template>
    
    <xsl:template match="file[title = 'Search engine']/lib">
        <lib>optional/lucene-core-2.4.1.jar</lib>
    </xsl:template>
    
    <!-- Use patched jar for XML-RPC -->
    <xsl:template match="file[title = 'Log4j']/lib">
        <lib>core/log4j-1.2.16.jar</lib>
    </xsl:template>

    <xsl:template match="*|@*|node()|comment()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()|comment()"/>
        </xsl:copy>
    </xsl:template>

</xsl:transform>
