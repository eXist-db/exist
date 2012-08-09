<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0">
    <xsl:output indent="yes"/>
    
    <xsl:template match="apps">
        <!-- Auto-generated file. Please do not edit. -->
        <processing>
            <logfiledir>$INSTALL_PATH</logfiledir>
            <job name="Set password for admin user ...">
                <os family="windows"/>
                <executefile name="$INSTALL_PATH/bin/setup.bat">
                    <arg>pass:$adminPasswd</arg>
                    <xsl:apply-templates/>
                </executefile>
            </job>
            <job name="Set password for admin user ...">
                <os family="unix"/>
                <executefile name="$INSTALL_PATH/bin/setup.sh">
                    <arg>pass:$adminPasswd</arg>
                    <xsl:apply-templates/>
                </executefile>
            </job>
        </processing>
    </xsl:template>
    
    <xsl:template match="app">
        <arg>$apps.<xsl:value-of select="@name"/></arg>
    </xsl:template>
</xsl:stylesheet>