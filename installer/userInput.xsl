<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0">
    <xsl:output indent="yes"/>
    <xsl:template match="apps">
        <!-- Auto-generated file. Please do not edit. -->
        <userInput>
            <panel order="0">
                <field type="title" align="left" txt="Set Data Directory and Admin Password" bold="false" size="1.33"/>
                <field type="staticText" align="left" txt="Please select a directory where eXist will keep its data files. On Vista and Windows 7, this should be outside the usual 'Program Files' directory:"/>
                <field type="dir" align="center" variable="dataDir">
                    <spec txt="Data dir: " size="20" set="$INSTALL_PATH/webapp/WEB-INF/data" mustExist="false" create="true"/>
                </field>
                <field type="divider" align="bottom"/>
                <field type="space"/>
                <field type="password" variable="adminPasswd" align="left">
                    <description align="left" txt="Please enter a password for user 'admin', the database administrator:"/>
                    <validator class="org.exist.izpack.PasswordValidator" txt="Passwords did not match!"/>
                    <spec>
                        <pwd txt="Enter password: " size="20" set=""/>
                        <pwd txt="Retype password: " size="20" set=""/>
                    </spec>
                </field>
            </panel>
            <panel order="1">
                <field type="title" align="left" txt="Select Application Packages" bold="false" size="1.33"/>
                <field type="staticText" align="left" txt="Please select the applications which should be installed. You can install more applications any time later."/>
                <xsl:apply-templates/>
            </panel>
        </userInput>
    </xsl:template>
    <xsl:template match="app">
        <field type="check" variable="apps.{@name}">
            <spec txt="{.}" true="{@name}" false=""
                set="true"/>
        </field>
    </xsl:template>
</xsl:stylesheet>