<?xml version="1.0" encoding="iso-8859-1" ?>


<!-- 

  Basic XMLForm processing stylesheet.  
  Converts XMLForm tags to HTML tags.
  
  Syntax is borrowed from the XForms standard.
  http://www.w3.org/TR/2002/WD-xforms-20020118/
  
  This stylesheet is usually applied at the end of a 
  transformation process after laying out the xmlform
  tags on the page is complete. At this stage xmlform tags 
  are rendered in device specific format.
  
  Different widgets are broken into templates 
  to allow customization in importing stylesheets

  author: Ivelin Ivanov, ivelin@apache.org, June 2002
  author: Andrew Timberlake <andrew@timberlake.co.za>, June 2002
  author: Michael Ratliff, mratliff@collegenet.com <mratliff@collegenet.com>, May 2002
  author: Torsten Curdt, tcurdt@dff.st, March 2002
  author: Simon Price <price@bristol.ac.uk>, September 2002
  author: Konstantin Piroumian <kpiroumian@protek.com>, September 2002
  author: Robert Ellis Parrott <parrott@fas.harvard.edu>, October 2002
-->

<xsl:stylesheet version="1.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:xf="http://apache.org/cocoon/xmlform/1.0">

   <xsl:output method = "xml" omit-xml-declaration = "no"  /> 
  

   <xsl:template match="/">
     <xsl:apply-templates />
   </xsl:template>


   <xsl:template match="xf:form">
      <form>
         <xsl:copy-of select="@*"/>

         <!-- the xf:form/@view attributed is sent back to the server as a hidden field -->
         <input type="hidden" name="cocoon-xmlform-view" value="{@view}"/>
         
         <!-- render the child form controls -->
         <xsl:apply-templates />
         
      </form>
   </xsl:template>


   <xsl:template match="xf:output">
      [<xsl:value-of select="xf:value/text()"/>]
   </xsl:template>


   <xsl:template match="xf:textbox">
      <!-- the ref attribute is assigned to html:name, which is how it is linked to the model -->
      <input name="{@ref}" type="textbox" value="{xf:value/text()}">
        <!-- copy all attributes from the original markup, except for "ref" -->
        <xsl:copy-of select="@*[not(name()='ref')]"/>
        <xsl:apply-templates select="xf:hint"/>
      </input>
   </xsl:template>


   <xsl:template match="xf:textarea">
      <textarea name="{@ref}" >
        <xsl:copy-of select="@*[not(name()='ref')]"/>
        <xsl:value-of select="xf:value/text()"/>
        <xsl:apply-templates select="xf:hint"/>
      </textarea>
   </xsl:template>


   <xsl:template match="xf:password">
      <input name="{@ref}" type="password" value="{xf:value/text()}">
        <xsl:copy-of select="@*[not(name()='ref')]"/>
        <xsl:apply-templates select="xf:hint"/>
      </input>
   </xsl:template>


   <xsl:template match="xf:hidden">
      <input name="{@ref}" type="hidden" value="{xf:value/text()}">
        <xsl:copy-of select="@*[not(name()='ref')]"/>
      </input>
   </xsl:template>


   <xsl:template match="xf:selectBoolean">
      <input name="{@ref}" type="checkbox" value="true">
        <xsl:copy-of select="@*[not(name()='ref')]"/>
          <xsl:if test="xf:value/text() = 'true'">
          <xsl:attribute name="checked"/>
        </xsl:if>
        <xsl:apply-templates select="xf:hint"/>
      </input>
   </xsl:template>


   <xsl:template match="xf:selectOne | xf:selectOne[@selectUIType='listbox']">
     <select name="{@ref}">
     <xsl:copy-of select="@*[not(name()='ref')]"/>
     <!-- all currently selected nodes are listed as value elements -->
       <xsl:variable name="selected" select="xf:value"/>
       <xsl:for-each select="xf:item">
         <option value="{xf:value}">
           <!-- If the current item value matches one of the selected values -->
           <!-- mark it as selected in the listbox -->
           <xsl:if test="$selected = xf:value">
             <xsl:attribute name="selected"/>
           </xsl:if>
           <xsl:value-of select="xf:caption"/>
         </option>
       </xsl:for-each>
     </select>
   </xsl:template>

   
   <xsl:template match="xf:selectOne[@selectUIType='radio']">
        <xsl:variable name="selected" select="xf:value"/>
        <xsl:variable name="ref" select="@ref"/>
        <xsl:for-each select="xf:item">
            <input name="{$ref}" type="radio" value="{xf:value}">
                <xsl:copy-of select="@*[not(name()='ref')]"/>
                <xsl:if test="xf:value = $selected">
                    <xsl:attribute name="checked"/>
                </xsl:if>
            </input>
            <xsl:value-of select="xf:caption"/>
            <br/>
        </xsl:for-each>
   </xsl:template>

   
   <xsl:template match="xf:selectMany | xf:selectMany[@selectUIType='listbox']">
     <xsl:variable name="selected" select="xf:value"/>
     <select name="{@ref}">
       <xsl:copy-of select="@*[not(name()='ref')]"/>
       <xsl:attribute name="multiple"/>
       <xsl:for-each select="xf:item">
         <option value="{xf:value}">
           <xsl:if test="xf:value = $selected">
             <xsl:attribute name="selected"/>
           </xsl:if>
           <xsl:value-of select="xf:caption"/>
         </option>
       </xsl:for-each>
     </select>  
   </xsl:template>

   
   <xsl:template match="xf:selectMany[@selectUIType='checkbox']">
        <xsl:variable name="selected" select="xf:value"/>
        <xsl:variable name="ref" select="@ref"/>
        <xsl:for-each select="xf:item">
            <input name="{$ref}" type="checkbox" value="{xf:value}">
                <xsl:copy-of select="@*[not(name()='ref')]"/>
                <xsl:if test="xf:value = $selected">
                  <xsl:attribute name="checked"/>
                </xsl:if>
            </input>
            <xsl:value-of select="xf:caption"/>
            <br/>
        </xsl:for-each>
   </xsl:template>

   
   
   <xsl:template match="xf:submit">
       <!-- the id attribute of the submit control is sent to the server -->
       <!-- as a conventional Cocoon Action parameter of the form cocoon-action-* -->
      <input name="cocoon-action-{@id}" type="submit" value="{xf:caption/text()}">
        <xsl:copy-of select="@*[not(name()='id')]"/>
        <xsl:apply-templates select="xf:hint"/>
      </input>
   </xsl:template>
   
   <xsl:template match="xf:hint">
          <xsl:attribute name="title"><xsl:value-of select="."/></xsl:attribute>
   </xsl:template>


   <!-- copy all the rest of the markup which is not recognized above -->
   <xsl:template match="*">
      <xsl:copy><xsl:copy-of select="@*" /><xsl:apply-templates /></xsl:copy>
   </xsl:template>

   <xsl:template match="text()">
      <xsl:value-of select="." />
   </xsl:template>

</xsl:stylesheet>

