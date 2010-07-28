<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright (c) 2010. betterForm Project - http://www.betterform.de
  ~ Licensed under the terms of BSD License
  -->

<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xforms="http://www.w3.org/2002/xforms"
                xmlns:bf="http://betterform.sourceforge.net/xforms"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="bf xforms xsl xsd">

    <xsl:variable name="data-prefix" select="'d_'"/>
    <xsl:variable name="trigger-prefix" select="'t_'"/>
    <xsl:variable name="remove-upload-prefix" select="'ru_'"/>


    <!-- change this to your ShowAttachmentServlet -->

    <!-- This stylesheet contains a collection of templates which map XForms controls to HTML controls. -->
    <xsl:output method="html" version="4.01" indent="yes"/>


    <!-- ######################################################################################################## -->
    <!-- This stylesheet serves as a 'library' for HTML form controls. It contains only named templates and may   -->
    <!-- be re-used in different layout-stylesheets to create the naked controls.                                 -->
    <!-- ######################################################################################################## -->

    <xsl:template name="select1">
        <xsl:variable name="schemaValue" select="bf:data/@bf:schema-value"/>
        <xsl:variable name="navindex" select="if (exists(@navindex)) then @navindex else '0'"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>
        <xsl:variable name="parent" select="."/>
        <xsl:variable name="incremental" select="if (exists(@incremental)) then @incremental else 'true'"/>
        <xsl:variable name="handler">
            <xsl:choose>
                <xsl:when test="$incremental='false'">onblur</xsl:when>
                <xsl:otherwise>onchange</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="size">
            <xsl:choose>
                <xsl:when test="@size"><xsl:value-of select="@size"/></xsl:when>
                <xsl:otherwise>5</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="datatype"><xsl:call-template name="getType"/></xsl:variable>

        <xsl:if test="exists(.//xforms:itemset)"><xsl:text>
</xsl:text>            <script type="text/javascript">dojo.require("betterform.ui.select.OptGroup");</script><xsl:text>
</xsl:text>
        </xsl:if>
        
        <xsl:choose>
            <xsl:when test="@appearance='compact'">
                <select id="{concat($id,'-value')}"
                        name="{$name}"
                        size="{$size}"
                        dataType="{$datatype}"
                        controlType="select1List"
                        class="xfValue"
                        title="{normalize-space(xforms:hint)}"
                        tabindex="{$navindex}"
                        schemaValue="{bf:data/@bf:schema-value}"
                        incremental="{$incremental}">
                    <xsl:call-template name="build-items">
                        <xsl:with-param name="parent" select="$parent"/>
                    </xsl:call-template>
                </select>
                <!-- handle itemset prototype -->
                <xsl:if test="not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/bf:data/xforms:item">
                        <xsl:call-template name="build-item-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>

            </xsl:when>
            <xsl:when test="@appearance='full'">
                <span id="{$id}-value"
                      controlType="select1RadioButton"
                      class="xfValue"                      
                      incremental="{$incremental}">
                    <xsl:call-template name="build-radiobuttons">
                        <xsl:with-param name="id" select="$id"/>
                        <xsl:with-param name="name" select="$name"/>
                        <xsl:with-param name="parent" select="$parent"/>
                        <xsl:with-param name="navindex" select="$navindex"/>
                    </xsl:call-template>
                </span>
                    <!-- handle itemset prototype -->
                    <xsl:if test="not(ancestor::xforms:repeat)">
                        <xsl:for-each select="xforms:itemset/bf:data/xforms:item">
                            <xsl:call-template name="build-radiobutton-prototype">
                                <xsl:with-param name="item-id" select="@id"/>
                                <xsl:with-param name="itemset-id" select="../../@id"/>
                                <xsl:with-param name="name" select="$name"/>
                                <xsl:with-param name="parent" select="$parent"/>
                                <xsl:with-param name="navindex" select="$navindex"/>
                            </xsl:call-template>
                        </xsl:for-each>
                    </xsl:if>

                <!-- create hidden parameter for identification and deselection -->
            </xsl:when>
            <xsl:otherwise>
                <!-- No appearance or appearance='minimal'-->
                <xsl:choose>
                    <!-- Open Selection -->
                    <xsl:when test="@selection='open'">
                        <select id="{concat($id,'-value')}"
                                name="{$name}"
                                class="xfValue"
                                size="1"
                                dataType="{$datatype}"
                                controlType="select1ComboBoxOpen"
                                title="{normalize-space(xforms:hint)}"
                                tabindex="{$navindex}"
                                schemaValue="{bf:data/@bf:schema-value}"
                                autocomplete="true"
                                incremental="{$incremental}">
                            <xsl:call-template name="build-items">
                                <xsl:with-param name="parent" select="$parent"/>
                            </xsl:call-template>
                        </select>
                    </xsl:when>
                    <!-- Standard Minimal Select -->
                    <xsl:otherwise>
                        <select id="{$id}-value"
                                name="{$name}"
                                class="xfValue"
                                dataType="{$datatype}"
                                controlType="select1ComboBox"
                                title="{normalize-space(xforms:hint)}"
                                tabindex="{$navindex}"
                                schemaValue="{bf:data/@bf:schema-value}"
                                incremental="{$incremental}">
                            <xsl:call-template name="build-items">
                                <xsl:with-param name="parent" select="$parent"/>
                            </xsl:call-template>
                        </select>
                    </xsl:otherwise>
                </xsl:choose>
        </xsl:otherwise>
        </xsl:choose>
    </xsl:template>


    <xsl:template name="select">       
        <xsl:variable name="navindex" select="if (exists(@navindex)) then @navindex else '0'"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="selection" select="@selection"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>
        <xsl:variable name="parent" select="."/>
        <xsl:variable name="incremental" select="if (exists(@incremental)) then @incremental else 'true'"/>
        <xsl:variable name="schemaValue" select="bf:data/@bf:schema-value"/>
        <xsl:variable name="datatype"><xsl:call-template name="getType"/></xsl:variable>

        <xsl:if test="exists(.//xforms:itemset)">
            <script type="text/javascript">dojo.require("betterform.ui.select.OptGroup");</script><xsl:text>
</xsl:text>
        </xsl:if>
        <xsl:choose>
            <!-- only 'full' is supported as explicit case and renders a group of checkboxes. All other values
            of appearance will be matched and represented as a list control. -->
            <xsl:when test="@appearance='full'">
                <span id="{$parent/@id}-value"
                      name="{$name}"
                      class="xfValue CheckBoxGroup"
                      selection="{$selection}"
                      controlType="selectCheckBox"
                      dataType="{$datatype}"
                      title="{normalize-space(xforms:hint)}"
                      schemaValue="{bf:data/@bf:schema-value}"
                      incremental="{$incremental}">
                    <xsl:for-each select="$parent/xforms:item|$parent/xforms:choices|$parent/xforms:itemset">
                        <xsl:call-template name="build-checkboxes-list">
                            <xsl:with-param name="name" select="$name"/>
                            <xsl:with-param name="parent" select="$parent"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </span>
                <!-- handle itemset prototype -->
                <xsl:if test="not(ancestor::xforms:repeat)">
                    <xsl:for-each select="xforms:itemset/bf:data/xforms:item">
                        <xsl:call-template name="build-checkbox-prototype">
                            <xsl:with-param name="item-id" select="@id"/>
                            <xsl:with-param name="itemset-id" select="../../@id"/>
                            <xsl:with-param name="name" select="$name"/>
                            <xsl:with-param name="parent" select="$parent"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:if>
            </xsl:when>
            <xsl:otherwise>
                <select id="{concat($id,'-value')}"
                        name="{$name}"
                        size="{@size}"
                        multiple="true"
                        controlType="selectList"
                        dataType="{$datatype}"
                        class="xfValue"
                        title="{normalize-space(xforms:hint)}"
                        tabindex="{$navindex}"
                        schemaValue="{bf:data/@bf:schema-value}"
                        selection="{$selection}"
                        incremental="{$incremental}">
                    <xsl:call-template name="build-items">
                        <xsl:with-param name="parent" select="$parent"/>
                    </xsl:call-template>
                </select>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>


    <!-- build submit -->
    <!-- todo: align with trigger template -->
    <xsl:template name="submit">
        <xsl:param name="classes"/>
    </xsl:template>

    <!-- build trigger -->
    <xsl:template name="trigger">
        <xsl:param name="classes"/>
        <xsl:variable name="navindex" select="if (exists(@navindex)) then @navindex else '0'"/>
        <xsl:variable name="id" select="@id"/>
        <xsl:variable name="appearance" select="@appearance"/>
        <xsl:variable name="name" select="concat($data-prefix,$id)"/>
        <xsl:variable name="hint" select="if(exists(xforms-hint) and exists(@accesskey)) then concat(normalize-space(xforms:hint),'- KEY: [ALT]+ ',@accesskey) else normalize-space(xforms:hint)"/>
        <xsl:variable name="src" select="@src" />
        <xsl:variable name="incremental" />
        <xsl:variable name="control-classes">
            <xsl:call-template name="assemble-control-classes">
                <xsl:with-param name="appearance" select="$appearance"/>
            </xsl:call-template>
        </xsl:variable>
        <span id="{$id}" class="{$control-classes}" dojoType="betterform.ui.Control">
        <!-- minimal appearance only supported in scripted mode -->
            <xsl:choose>
                <xsl:when test="$appearance='minimal'">
                        <span id="{$id}-value"
                              appearance="{@appearance}"
                              controlType="minimalTrigger"
                              name="{$name}"
                              class="xfValue {@class}"
                              title="{$hint}"
                              navindex="{$navindex}"
                              accesskey="{@accesskey}"
                              label="{xforms:label}"
                              source="{$src}">
                              <xsl:apply-templates select="@*[not(name()='class')][not(name()='id')][not(name()='appearance')][not(name()='src')]"/>
                        </span>
                </xsl:when>
                <xsl:when test="$appearance='imageTrigger'">
                    <button id="{$id}-value"
                            appearance="{@appearance}"
                            controlType="trigger"
                            label="{xforms:label}"
                            name="{$name}"
                            type="button"
                            class="xfValue"
                            title="{$hint}"
                            navindex="{$navindex}"
                            accesskey="{@accesskey}"
                            source="{$src}">
                            <xsl:apply-templates select="@*[not(name()='class')][not(name()='id')][not(name()='appearance')][not(name()='src')]"/>
                        <span id="{$id}-label" class="buttonLabel">
                            <xsl:apply-templates select="xforms:label"/>
                        </span>
                    </button>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:variable name="source" select="if (contains(@mediatype, 'image/')) then xforms:label else $src"/>
                    <button id="{$id}-value"
                            appearance="{@appearance}"
                            controlType="trigger"
                            label="{xforms:label}"
                            name="{$name}"
                            type="button"
                            class="xfValue"
                            title="{$hint}"
                            navindex="{$navindex}"
                            accesskey="{@accesskey}"
                            source="{$source}"/>
                </xsl:otherwise>
            </xsl:choose>
        </span>
    </xsl:template>



    <!-- ######################################################################################################## -->
    <!-- ########################################## HELPER TEMPLATES FOR SELECT, SELECT1 ######################## -->
    <!-- ######################################################################################################## -->

    <xsl:template name="build-items">
        <xsl:param name="parent"/>
        <xsl:if test="local-name($parent) ='select1' and ($parent/@appearance='minimal' or not(exists($parent/@appearance)))">

            <xsl:variable name="aggregatedEmptyLabel" >
                <xsl:for-each select="$parent//*[not(exists(ancestor::bf:data))]/xforms:label">
                    <xsl:if test=". =''">true</xsl:if>
                </xsl:for-each>
            </xsl:variable>
            <xsl:variable name="hasEmptyLabel" as="xsd:boolean">
                <xsl:choose>
                    <xsl:when test="contains($aggregatedEmptyLabel, 'true')">true</xsl:when>
                    <xsl:otherwise>false</xsl:otherwise>
                </xsl:choose>
            </xsl:variable>

            <xsl:if test="not($hasEmptyLabel)">
                <option value="" class="xfSelectorItem">
                    <xsl:if test="string-length($parent/bf:data/text()) = 0">
                        <xsl:attribute name="selected">selected</xsl:attribute>
                    </xsl:if>
                </option>
            </xsl:if>
        </xsl:if>
		<!-- add an empty item, because otherwise deselection is not possible -->
<!--
        <xsl:if test="$parent/bf:data/@bf:required='false'">
		<option value="">
			<xsl:if test="string-length($parent/bf:data/text()) = 0">
				<xsl:attribute name="selected">selected</xsl:attribute>
			</xsl:if>
		</option>
        </xsl:if>
-->
		<xsl:for-each select="$parent/xforms:itemset|$parent/xforms:item|$parent/xforms:choices">
			<xsl:call-template name="build-items-list"/>
		</xsl:for-each>
    </xsl:template>

    <xsl:template name="build-items-list">
    	<xsl:choose>
    		<xsl:when test="local-name(.) = 'choices'">    		    
    		    <xsl:call-template name="build-items-choices"/>
    		</xsl:when>
    		<xsl:when test="local-name(.) = 'itemset'">
    			<xsl:call-template name="build-items-itemset"/>
    		</xsl:when>
    		<xsl:when test="local-name(.) = 'item'">
    			<xsl:call-template name="build-items-item"/>
    		</xsl:when>
    	</xsl:choose>
    </xsl:template>

	<xsl:template name="build-items-choices">
	        <xsl:if test="exists(xforms:label)"> 
	            <option id="{@id}" value="{xforms:label}" class="xfSelectorItem"><xsl:value-of select="xforms:label" /></option>
	        </xsl:if>
	        <xsl:for-each select="xforms:itemset|xforms:item|xforms:choices">
	           <xsl:call-template name="build-items-list"/>
	        </xsl:for-each>
	</xsl:template>

    <xsl:template name="build-items-itemset">
		<optgroup id="{@id}" dojoType="betterform.ui.select.OptGroup" label="">
			<xsl:for-each select="xforms:item">
				<xsl:call-template name="build-items-item"/>
            </xsl:for-each>
		</optgroup>
	</xsl:template>

	<xsl:template name="build-items-item">
        <xsl:variable name="itemValue">
            <xsl:choose>
                <xsl:when test="exists(xforms:copy)"><xsl:value-of select="xforms:copy/@id"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="xforms:value"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        <option id="{@id}" value="{$itemValue}" title="{normalize-space(xforms:hint)}" class="xfSelectorItem">
            <xsl:if test="@selected='true'">
                <xsl:attribute name="selected">selected</xsl:attribute>
            </xsl:if>
            <xsl:value-of select="xforms:label"/>
        </option>
    </xsl:template>

    <xsl:template name="build-item-prototype">
        <xsl:param name="item-id"/>
        <xsl:param name="itemset-id"/>

        <select id="{$itemset-id}-prototype" class="xfSelectorPrototype">
            <option id="{$item-id}-value" class="xfSelectorPrototype">
	           	<xsl:choose>
    	       		<xsl:when test="xforms:copy">
	    	   			<xsl:attribute name="value" select="xforms:copy/@id"/>
	              		<xsl:attribute name="title" select="xforms:copy/@id"/>
    	          	</xsl:when>
        	      	<xsl:otherwise>
            	   		<xsl:attribute name="value" select="normalize-space(xforms:value)"/>
              			<xsl:attribute name="title" select="normalize-space(xforms:hint)"/>
                	</xsl:otherwise>
				</xsl:choose>
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="selected">selected</xsl:attribute>
                </xsl:if>
                <xsl:value-of select="xforms:label"/>
            </option>
        </select>
    </xsl:template>


    <xsl:template name="build-checkboxes-list">
    	<xsl:param name="name"/>
        <xsl:param name="parent"/>
    	<xsl:choose>
    		<xsl:when test="local-name(.) = 'choices'">
    			<xsl:call-template name="build-checkboxes-choices">
            		<xsl:with-param name="name" select="$name"/>
            		<xsl:with-param name="parent" select="$parent"/>
            	</xsl:call-template>
    		</xsl:when>
    		<xsl:when test="local-name(.) = 'itemset'">
    			<xsl:call-template name="build-checkboxes-itemset">
            		<xsl:with-param name="name" select="$name"/>
            		<xsl:with-param name="parent" select="$parent"/>
            	</xsl:call-template>
    		</xsl:when>
    		<xsl:when test="local-name(.) = 'item'">
    			<xsl:call-template name="build-checkboxes-item">
            		<xsl:with-param name="name" select="$name"/>
            		<xsl:with-param name="parent" select="$parent"/>
            	</xsl:call-template>
    		</xsl:when>
    	</xsl:choose>
    </xsl:template>


	<xsl:template name="build-checkboxes-choices">
		<xsl:param name="name"/>
        <xsl:param name="parent"/>
		<xsl:for-each select="xforms:itemset|xforms:item|xforms:choices">
			<xsl:call-template name="build-checkboxes-list">
				<xsl:with-param name="name" select="$name"/>
           		<xsl:with-param name="parent" select="$parent"/>
			</xsl:call-template>
		</xsl:for-each>
	</xsl:template>

    <xsl:template name="build-checkboxes-itemset">
    	<xsl:param name="name"/>
        <xsl:param name="parent"/><script type="text/javascript">dojo.require("betterform.ui.select.CheckBoxItemset");</script><xsl:text>
</xsl:text>
		<span id="{@id}" dojoType="betterform.ui.select.CheckBoxItemset" >
			<xsl:for-each select="xforms:item">
				<xsl:call-template name="build-checkboxes-item">
	           		<xsl:with-param name="name" select="$name"/>
	           		<xsl:with-param name="parent" select="$parent"/>
				</xsl:call-template>
			</xsl:for-each>
		</span>
	</xsl:template>

	<xsl:template name="build-checkboxes-item">
    	<xsl:param name="name"/>
        <xsl:param name="parent"/>
        <xsl:param name="navindex"/><script type="text/javascript">dojo.require("betterform.ui.select.CheckBox");</script><xsl:text>
</xsl:text>
        <span id="{@id}" class="xfSelectorItem">
            <input id="{@id}-value"
                   class="xfCheckBoxValue"
                   type="checkbox"
                   tabindex="0"

                   selectWidgetId="{$parent/@id}-value"
                   name="{$name}"
                   dojotype="betterform.ui.select.CheckBox">
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="checked">checked</xsl:attribute>
                </xsl:if>

                <xsl:choose>
        			<xsl:when test="xforms:copy">
           				<xsl:attribute name="value" select="xforms:copy/@id"/>
	            	</xsl:when>
    	        	<xsl:otherwise>
	    	    		<xsl:attribute name="value" select="xforms:value"/>
    	    		</xsl:otherwise>
        	    </xsl:choose>
                <xsl:attribute name="title">
                    <xsl:choose>
                        <xsl:when test="xforms:hint"><xsl:value-of select="normalize-space(xforms:hint)"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="normalize-space($parent/xforms:hint)"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                <xsl:text> </xsl:text>

            </input>

            <label id="{@id}-label" for="{@id}-value" class="xfCheckBoxLabel">
                <xsl:if test="$parent/bf:data/@bf:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="xforms:label"/>
            </label>
        </span>
	</xsl:template>

    <xsl:template name="build-checkbox-prototype">
        <xsl:param name="item-id"/>
        <xsl:param name="itemset-id"/>
        <xsl:param name="name"/>
        <xsl:param name="parent"/>

        <span id="{$itemset-id}-prototype" class="xfSelectorPrototype">
            <input id="{$item-id}-value" class="xfValue" type="checkbox" name="{$name}">
                <xsl:choose>
	       			<xsl:when test="xforms:copy">
		   				<xsl:attribute name="value"><xsl:value-of select="xforms:copy/@id"/></xsl:attribute>
	              	</xsl:when>
    	        	<xsl:otherwise>
      	 	    		<xsl:attribute name="value"><xsl:value-of select="xforms:value"/></xsl:attribute>
            		</xsl:otherwise>
           	    </xsl:choose>
                <xsl:attribute name="title">
                    <xsl:choose>
                        <xsl:when test="xforms:hint">
                            <xsl:value-of select="normalize-space(xforms:hint)"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="normalize-space($parent/xforms:hint)"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                <xsl:if test="$parent/bf:data/@bf:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="checked">checked</xsl:attribute>
                </xsl:if>
                <xsl:attribute name="onclick">setXFormsValue(this);</xsl:attribute>
                <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
                <xsl:text> </xsl:text>
            </input>
            <span id="{@item-id}-label" class="xfLabel">
                <xsl:if test="$parent/bf:data/@bf:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="xforms:label"/>
            </span>
        </span>
    </xsl:template>

    <!-- overwrite/change this template, if you don't like the way labels are rendered for checkboxes -->
    <xsl:template name="build-radiobuttons">
        <xsl:param name="name"/>
        <xsl:param name="parent"/>
        <xsl:param name="id"/>
        <xsl:param name="navindex"/>
        <!-- handle items, choices and itemsets -->
        <xsl:for-each select="$parent/xforms:item|$parent/xforms:choices|$parent/xforms:itemset">
        	<xsl:call-template name="build-radiobuttons-list">
        		<xsl:with-param name="name" select="$name"/>
        		<xsl:with-param name="parent" select="$parent"/>
        	</xsl:call-template>
        </xsl:for-each>
    </xsl:template>

    <xsl:template name="build-radiobuttons-list">
    	<xsl:param name="name"/>
    	<xsl:param name="parent"/>
        <!-- todo: refactor to handle xforms:choice / xforms:itemset by matching -->
        <xsl:choose>
    		<xsl:when test="local-name(.) = 'choices'">
    			<xsl:call-template name="build-radiobuttons-choices">
            		<xsl:with-param name="name" select="$name"/>
            		<xsl:with-param name="parent" select="$parent"/>
            	</xsl:call-template>
    		</xsl:when>
    		<xsl:when test="local-name(.) = 'itemset'">
    			<xsl:call-template name="build-radiobuttons-itemset">
            		<xsl:with-param name="name" select="$name"/>
            		<xsl:with-param name="parent" select="$parent"/>
            	</xsl:call-template>
    		</xsl:when>
    		<xsl:when test="local-name(.) = 'item'">
    			<xsl:call-template name="build-radiobuttons-item">
            		<xsl:with-param name="name" select="$name"/>
            		<xsl:with-param name="parent" select="$parent"/>
            	</xsl:call-template>
    		</xsl:when>
    	</xsl:choose>
    </xsl:template>

	<xsl:template name="build-radiobuttons-choices">
		<xsl:param name="name"/>
		<xsl:param name="parent"/>
		<xsl:for-each select="xforms:itemset|xforms:item|xforms:choices">
			<xsl:call-template name="build-radiobuttons-list">
				<xsl:with-param name="name" select="$name"/>
           		<xsl:with-param name="parent" select="$parent"/>
			</xsl:call-template>
		</xsl:for-each>
	</xsl:template>

    <xsl:template name="build-radiobuttons-itemset">
    	<xsl:param name="name"/>
    	<xsl:param name="parent"/><script type="text/javascript">dojo.require("betterform.ui.select1.RadioItemset");</script><xsl:text>
</xsl:text>

		<span id="{@id}" dojoType="betterform.ui.select1.RadioItemset" class="xfRadioItemset">
			<xsl:for-each select="xforms:item">
				<xsl:call-template name="build-radiobuttons-item">
	           		<xsl:with-param name="name" select="$name"/>
	           		<xsl:with-param name="parent" select="$parent"/>
				</xsl:call-template>
			</xsl:for-each>
		</span>
	</xsl:template>

	<xsl:template name="build-radiobuttons-item">
    	<xsl:param name="name"/>
    	<xsl:param name="parent"/>
        <xsl:param name="navindex"/>
        <xsl:variable name="parentId" select="$parent/@id"/>
        <span id="{@id}"
              class="xfSelectorItem"
              controlType="radioButtonEntry">
            <input id="{@id}-value"
                   class="xfRadioValue"
                   dataType="radio"
                   controlType="radio"
                   parentId="{$parentId}"
                   name="{$name}"
                   selected="{@selected}"
                   >
                <xsl:if test="string-length($navindex) != 0">
                    <xsl:attribute name="tabindex">
                        <xsl:value-of select="$navindex"/>
                    </xsl:attribute>
                </xsl:if>
                <xsl:attribute name="value">
                    <xsl:choose>
                        <xsl:when test="xforms:copy"><xsl:value-of select="xforms:copy/@id"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="normalize-space(xforms:value)"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                <xsl:attribute name="title">
                    <xsl:choose>
                        <xsl:when test="xforms:hint"><xsl:value-of select="normalize-space(xforms:hint)"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="normalize-space($parent/xforms:hint)"/></xsl:otherwise></xsl:choose>
                </xsl:attribute>
            </input>
            <label id="{@id}-label" for="{@id}-value" class="xfRadioLabel">
                <xsl:if test="$parent/bf:data/@bf:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="xforms:label"/>
            </label>
        </span>
	</xsl:template>

    <xsl:template name="build-radiobutton-prototype">
        <xsl:param name="item-id"/>
        <xsl:param name="itemset-id"/>
        <xsl:param name="name"/>
        <xsl:param name="parent"/>
        <xsl:param name="navindex"/>
        <span id="{$itemset-id}-prototype" class="xfSelectorPrototype">
            <input id="{$item-id}-value" class="xfValue" type="radio" name="{$name}">
                <xsl:if test="string-length($navindex) != 0">
                    <xsl:attribute name="tabindex">
                        <xsl:value-of select="$navindex"/>
                    </xsl:attribute>
                </xsl:if>


                <xsl:attribute name="value">
                    <xsl:choose>
                        <xsl:when test="xforms:copy"><xsl:value-of select="xforms:copy/@id"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="normalize-space(xforms:value)"/></xsl:otherwise>
                    </xsl:choose>
                </xsl:attribute>
                <xsl:attribute name="title">
                    <xsl:choose>
                        <xsl:when test="xforms:hint"><xsl:value-of select="normalize-space(xforms:hint)"/></xsl:when>
                        <xsl:otherwise><xsl:value-of select="normalize-space($parent/xforms:hint)"/></xsl:otherwise></xsl:choose>
                </xsl:attribute>

              <xsl:if test="$parent/bf:data/@bf:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:if test="@selected='true'">
                    <xsl:attribute name="checked">checked</xsl:attribute>
                </xsl:if>
                <xsl:attribute name="onclick">setXFormsValue(this);</xsl:attribute>
                <xsl:attribute name="onkeydown">DWRUtil.onReturn(event, submitFunction);</xsl:attribute>
            </input>
            <span id="{$item-id}-label" class="xfLabel">
                <xsl:if test="$parent/bf:data/@bf:readonly='true'">
                    <xsl:attribute name="disabled">disabled</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="xforms:label" mode="prototype"/>
            </span>
        </span>
    </xsl:template>


</xsl:stylesheet>
