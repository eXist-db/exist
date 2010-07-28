<xsl:stylesheet version="2.0"
                xmlns:html="http://www.w3.org/1999/xhtml"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xf="http://www.w3.org/2002/xforms"
                xmlns:ev="http://www.w3.org/2001/xml-events"
                xmlns:ts="http://www.w3c.org/MarkUp/Forms/XForms/Test/11"
                exclude-result-prefixes="html ev xsl xf">

    <!-- Copyright 2010 Lars Windauer, Joern Turner -->
    <xsl:output method="xhtml" omit-xml-declaration="yes" indent="yes"/>

    <xsl:variable name="project" select="/data/project/name"/>
    <xsl:template match="/">
        <html>
            <head>
                <title>betterFORM <xsl:value-of select="$project"/>Timetracker</title>
                <style type="text/css">
                    body{
                        font-family:sans-serif;
                        margin:20px;
                        background:url(resources/images/bgOne.gif) repeat-x fixed;
                    }
                    table{
                        width:800px;
                        margin-left:auto;
                        margin-right:auto;
                    }

                    td{
                        background-color:lightSteelBlue;
                    }
                    .totals td{
                        text-align:center;
                        font-weight:bold;
                        font-size:12pt;
                        padding:10px;
                    }
                    .true{font-weight:bold;}

                    .pageheader {
                        display:block;
                        font-size:x-large;
                        font-weight:bold;
                        margin-bottom:20px;
                    }
                    #summary{
                        font-size:12pt;
                        background-color:SteelBlue;
                        padding:10px;
                    }
                    .chapterHeader{
                        background:SteelBlue;
                        font-size:12pt;
                        padding:10px;
                    }
                </style>
            </head>
            <body>
                <div class="pageheader">Timetracker Results for Project: <xsl:value-of select="$project"/></div>
                <p/>
                <div>Overall Duration: <xsl:value-of select="/data/project/duration"/></div>
                <table>
                    <tr>
                        <td class="summary">Date</td>
                        <td class="summary">Duration</td>
                        <td class="summary">User(s)</td>
                        <td class="summary">Task</td>
                        <td class="summary">Note</td>
                        <td class="summary">Status</td>
                        <td class="summary">Billable</td>
                        <td class="summary">Billed</td>
                    </tr>

                    <xsl:apply-templates select="/data/project/task"/>
                </table>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="task">
        <tr>
            <td><xsl:value-of select="date"/></td>
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
