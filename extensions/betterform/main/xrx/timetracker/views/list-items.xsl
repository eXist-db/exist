<xsl:stylesheet version="2.0"
                xmlns:html="http://www.w3.org/1999/xhtml"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xf="http://www.w3.org/2002/xforms"
                xmlns:ev="http://www.w3.org/2001/xml-events"
                xmlns:ts="http://www.w3c.org/MarkUp/Forms/XForms/Test/11"
                exclude-result-prefixes="html ev xsl xf">

    <!-- Copyright 2010 Lars Windauer, Joern Turner -->
    <xsl:output method="xhtml" omit-xml-declaration="yes" indent="yes"/>


    <xsl:template match="/">
        <div id="resultTable">
            <div>Overall Duration: <xsl:value-of select="/data/project/duration"/></div>
            <table id="taskTable">
                <tr>
                    <th class="summary">Date</th>
                    <th class="summary">Project</th>
                    <th class="summary">Duration</th>
                    <th class="summary">User(s)</th>
                    <th class="summary">Task</th>
                    <th class="summary">Note</th>
                    <th class="summary">Status</th>
                    <th class="summary">Billable</th>
                    <th class="summary">Billed</th>
                </tr>

                <xsl:apply-templates select="*/task"/>
            </table>
        </div>
    </xsl:template>

    <xsl:template match="task">
        <tr>
            <td><xsl:value-of select="date"/></td>
            <td><xsl:value-of select="project"/></td>
            <td><xsl:value-of select="concat(duration/@hours,':',duration/@minutes)"/></td>
            <td><xsl:value-of select="who"/></td>
            <td><xsl:value-of select="what"/></td>
            <td><xsl:value-of select="note"/></td>
            <td><xsl:value-of select="status"/></td>
            <td><xsl:value-of select="billable"/></td>
            <td><xsl:value-of select="billed/@date"/></td>
        </tr>
    </xsl:template>
</xsl:stylesheet>
