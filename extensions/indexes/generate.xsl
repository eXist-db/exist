<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    
    <xsl:template match="build">
        <project basedir="." default="jar" name="indexes">
            
            <!-- This is a generated build file. Do not edit. Change the stylesheet -->
            <!-- generate.xsl instead. -->
            
            <property file="local.properties"/>
            <property file="build.properties"/>
            
            <xsl:apply-templates select="indexes"/>
            
            <target name="compile">
                <echo message="---------------------------"/>
                <echo message="Compiling additional index modules"/>
                <echo message="---------------------------"/>
                <iterate target="compile"/>
            </target>
            
            <target name="compile-tests">
                <echo message="---------------------------"/>
                <echo message="Compiling additional index module tests"/>
                <echo message="---------------------------"/>
                <iterate target="compile-tests"/>
            </target>
            
            <target name="jar">
                <echo message="---------------------------"/>
                <echo message="Creating jars for additional index modules"/>
                <echo message="---------------------------"/>
                <iterate target="jar"/>
            </target>
            
            <target name="clean">
                <echo message="-------------------------------------"/>
                <echo message="Cleaning additional index modules ..."/>
                <echo message="-------------------------------------"/>
                <iterate target="clean"/>
                <delete file="build.xml" failonerror="false"/>
            </target>
            
            <target name="all-clean">
                <iterate target="all-clean"/>
            </target>
            
            <target name="test">
                <echo message="------------------------------------------"/>
                <echo message="Running tests on additional index modules"/>
                <echo message="------------------------------------------"/>
                <iterate target="test"/>
            </target>
            
        </project>
    </xsl:template>
    
    <xsl:template match="indexes">
        <xsl:for-each select="index">
            <condition property="include.index.{@name}.config">
                <istrue value="${{include.index.{@name}}}"/>
            </condition>
        </xsl:for-each>
        <xsl:apply-templates select="index"/>
        <macrodef name="iterate">
            <attribute name="target"/>
            <sequential>
                <xsl:for-each select="index">
                    <antcall target="{@name}">
                        <param name="target" value="@{{target}}"/>
                    </antcall>
                </xsl:for-each>
            </sequential>
        </macrodef>
    </xsl:template>
    
    <xsl:template match="index">
        <target name="{@name}" if="include.index.{@name}.config">
            <ant antfile="{@antfile}" dir="{@dir}" target="${{target}}">
                <property name="module" value="{@name}"/>
            </ant>
        </target>
    </xsl:template>
    
</xsl:stylesheet>
