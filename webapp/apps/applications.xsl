<?xml version="1.0" encoding="UTF-8"?>

<!-- Format query results for display -->

<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:a="http://exist-db.org/"
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:html="http://www.w3.org/1999/xhtml"
    version="1.0">
    
    <xsl:template match="a:applications">
        <document xmlns:xi="http://www.w3.org/2001/XInclude">
  
        <header>
            <logo src="logo.jpg"/>
            <title>Open Source Native XML Database</title>
            <author email="wolfgang@exist-db.org">Wolfgang M. Meier</author>
            <style href="styles/display.css"/>
        </header>
               
           <!-- include sidebar -->
           <xi:include href="sidebar.xml"/>
       
            <body>
                <section title="Powered By eXist">
                    <p>We frequently get questions about who is using eXist and if there are any life sites running the software.
                    If you are using eXist in some context that might be of interest to other users or may serve as a reference, 
                    please send us a description to be added to this page. Descriptions should be in XML, according to the 
                    <a href="applications.xsd.html">schema</a> that can be downloaded <a href="applications.xsd">here</a>. 
                    A <a href="template.xml">sample entry</a> is also available.</p>
                    
                    <p>Smaller applications are welcome as they are often good examples for other users.
                    The following applications have been submitted so far:</p>

                    <xsl:for-each select="a:application">
                        <xsl:sort select="dc:title/text()"/>
                        <xsl:apply-templates select="."/>
                    </xsl:for-each>
               </section>
           </body>
       </document>
   </xsl:template>
   
   <xsl:template match="a:application">
        <section title="{dc:title}">
            <p><a href="{dc:identifier/text()}"><xsl:value-of select="dc:identifier"/></a></p>
            <p><xsl:apply-templates select="dc:description"/></p>
            <table id="details">
                <xsl:apply-templates select="dc:creator"/>
                <xsl:apply-templates select="dc:publisher"/>
                <xsl:apply-templates select="dc:rights"/>
                
                <xsl:if test="dc:subject">
                    <tr>
                        <td class="heading">Subjects:</td>
                        <td>
                            <xsl:for-each select="dc:subject">
                                <xsl:if test="position() &gt; 1">
                                    <xsl:text>, </xsl:text>
                                </xsl:if>
                                <xsl:value-of select="."/>
                            </xsl:for-each>
                        </td>
                    </tr>
                </xsl:if>
                
                <xsl:apply-templates select="dc:format"/>
                <xsl:apply-templates select="a:status"/>
                
                <xsl:apply-templates select="a:database"/>
                
                <xsl:if test="a:datatype-stored">
                    <tr>
                        <td class="heading">Type of stored data:</td>
                        <td>
                            <xsl:for-each select="a:datatype-stored">
                                <xsl:if test="position() &gt; 1">
                                    <xsl:text>, </xsl:text>
                                </xsl:if>
                                <xsl:value-of select="."/>
                            </xsl:for-each>
                        </td>
                    </tr>
                </xsl:if>
                
                <xsl:if test="a:third-party-feature-used">
                    <tr>
                        <td class="heading">Third-party features used:</td>
                        <td>
                            <xsl:for-each select="a:third-party-feature-used">
                                <xsl:if test="position() &gt; 1">
                                    <xsl:text>, </xsl:text>
                                </xsl:if>
                                <xsl:value-of select="."/>
                            </xsl:for-each>
                        </td>
                    </tr>
                </xsl:if>
                
                <xsl:apply-templates select="a:creator-site|a:demo-site|a:production-site|a:dev-site"/>
                <xsl:apply-templates select="a:dev-notes"/>
            </table>
        </section>
    </xsl:template>
    
    <xsl:template match="dc:creator">
        <tr>
            <td class="heading">Created by:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="dc:publisher">
        <tr>
            <td class="heading">Publisher:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="dc:rights">
        <tr>
            <td class="heading">License/rights statement:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="dc:format">
        <tr>
            <td class="heading">Type of application:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:creator-site">
        <tr>
            <td class="heading">Creator's website:</td>
            <td><a href="{text()}"><xsl:value-of select="."/></a></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:demo-site">
        <tr>
            <td class="heading">Demonstration:</td>
            <td><a href="{text()}"><xsl:value-of select="."/></a></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:production-site">
        <tr>
            <td class="heading">Production website:</td>
            <td><a href="{text()}"><xsl:value-of select="."/></a></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:dev-site">
        <tr>
            <td class="heading">Development website:</td>
            <td><a href="{text()}"><xsl:value-of select="."/></a></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:status">
        <tr>
            <td class="heading">Status:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:database">
        <tr>
            <td class="heading">Database description:</td>
            <td>
                <xsl:for-each select="@*">
                    <xsl:if test="position() &gt; 1">
                        <xsl:text>; </xsl:text>
                    </xsl:if>
                    <span class="highlight"><xsl:value-of select="local-name(.)"/></span>
                    <xsl:text>: </xsl:text>
                    <xsl:value-of select="."/>
                </xsl:for-each>
            </td>
        </tr>
    </xsl:template>
    
    <xsl:template match="a:dev-notes">
        <tr>
            <td class="heading">Development notes:</td>
            <td><xsl:apply-templates/></td>
        </tr>
    </xsl:template>
    
   <xsl:template match="node()|@*" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="node()|@*"/>
		</xsl:copy>
	</xsl:template>
   
</xsl:stylesheet>
