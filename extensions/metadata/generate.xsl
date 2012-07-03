<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    
    <xsl:template match="build">
        <project basedir="." default="jar" name="metadata">
            
            <xsl:comment>This is a generated build file. Do not edit. Change the stylesheet
generate.xsl instead.</xsl:comment>
            
		    <!--
		        Get values from properties files. Note that the values in "local.build.properties" 
		        are leading to "build.properties".
    		-->
    		<property file="../local.build.properties"/>
    		<property file="../build.properties"/>

            <property file="local.properties"/>
            <property file="build.properties"/>
            
            <condition property="include.metadata.config">
                <istrue value="${{include.metadata}}"/>
            </condition>

            <xsl:apply-templates select="backends"/>
            
            <target name="compile">
	            <ant antfile="build.xml" dir="interface" target="compile">
    	            <!--  <property name="module" value="metadata"/> -->
        	    </ant>

                <echo message="---------------------------"/>
                <echo message="Compiling backends"/>
                <echo message="---------------------------"/>

                <iterate target="compile"/>
            </target>
            
            <target name="compile-tests">
	            <ant antfile="build.xml" dir="interface" target="compile-tests">
    	            <!--  <property name="module" value="metadata"/> -->
        	    </ant>

                <echo message="---------------------------"/>
                <echo message="Compiling backends tests"/>
                <echo message="---------------------------"/>

                <iterate target="compile-tests"/>
            </target>
            
            <target name="jar">
	            <ant antfile="build.xml" dir="interface" target="jar">
    	            <!--  <property name="module" value="metadata"/> -->
        	    </ant>

                <echo message="---------------------------"/>
                <echo message="Creating jars for backend"/>
                <echo message="---------------------------"/>

                <iterate target="jar"/>
            </target>
            
            <target name="clean">
	            <ant antfile="build.xml" dir="interface" target="clean">
    	            <!--  <property name="module" value="metadata"/> -->
        	    </ant>

                <echo message="-------------------------------------"/>
                <echo message="Cleaning backends ..."/>
                <echo message="-------------------------------------"/>

                <iterate target="clean"/>
                <delete file="build.xml" failonerror="false"/>
            </target>
            
            <target name="all-clean">

	            <ant antfile="build.xml" dir="interface" target="all-clean">
    	            <!--  <property name="module" value="metadata"/> -->
        	    </ant>

                <iterate target="all-clean"/>
            </target>
            
            <target name="test">
	            <ant antfile="build.xml" dir="interface" target="test">
    	            <!--  <property name="module" value="metadata"/> -->
        	    </ant>

                <echo message="------------------------------------------"/>
                <echo message="Running tests on backends"/>
                <echo message="------------------------------------------"/>

                <iterate target="test"/>
            </target>
            
        </project>
    </xsl:template>
    
    <xsl:template match="backends">
        <xsl:for-each select="backend">
            <condition property="include.metadata.{@name}.config">
                <istrue value="${{include.metadata.{@name}}}"/>
            </condition>
        </xsl:for-each>
        <xsl:apply-templates select="backend"/>
        <macrodef name="iterate">
            <attribute name="target"/>
            <sequential>
                <xsl:for-each select="backend">
                    <antcall target="{@name}">
                        <param name="target" value="@{{target}}"/>
                    </antcall>
                </xsl:for-each>
            </sequential>
        </macrodef>
    </xsl:template>
    
    <xsl:template match="backend">
        <target name="{@name}" if="include.metadata.{@name}.config">
            <ant antfile="{@antfile}" dir="{@dir}" target="${{target}}">
                <property name="module" value="{@name}"/>
            </ant>
        </target>
    </xsl:template>
    
</xsl:stylesheet>
