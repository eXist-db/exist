<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    
    <xsl:template match="build">
        <project basedir="." default="jar" name="indexes">
            
            <!-- This is a generated build file. Do not edit. Change the stylesheet -->
            <!-- generate.xsl instead. -->
            
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
                <echo message="Cleaning additional index modules ..."/>
                <delete file="build.xml" failonerror="false"/>
                <iterate target="clean"/>
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
        <macrodef name="iterate">
            <attribute name="target"/>
            <sequential>
                <xsl:apply-templates select="index"/>
            </sequential>
        </macrodef>
    </xsl:template>
    
    <xsl:template match="index">
        <ant antfile="{@antfile}" dir="{@dir}" target="@{{target}}">
            <property name="module" value="{@name}"/>
        </ant>
    </xsl:template>
    
</xsl:stylesheet>
