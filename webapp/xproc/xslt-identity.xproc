<?xml version="1.0" encoding="UTF-8"?>
<p:pipeline xmlns:c="http://www.w3.org/ns/xproc-step" xmlns:p="http://www.w3.org/ns/xproc"
    name="pipeline">
    <p:input port="source" primary="true">
        <p:inline>
            <html>
                <body>
                    <h1>It Worked</h1>
                    <p>I was passed through an XSLT stylesheet.</p>
                </body>
            </html>
        </p:inline>
    </p:input>
    <p:xslt>
        <p:input port="stylesheet">
            <p:inline>
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
                    <xsl:template match="node()|@*">
                        <xsl:copy>
                            <xsl:apply-templates/>
                        </xsl:copy>
                    </xsl:template>
                </xsl:stylesheet>
            </p:inline>
        </p:input>
    </p:xslt>
</p:pipeline>
