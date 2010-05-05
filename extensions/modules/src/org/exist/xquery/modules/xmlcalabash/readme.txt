This module relies on the presence of the following jar files in your EXIST_HOME/lib/user folder:

	calabash.jar
	saxon9pe.jar or saxon9he.jar or saxon9ee.jar


If you are building from source then the module build process will try and download
these files for you if you enable the module.

Please read XML Calabash documentation for information on other dependencies related to using specific
optional XProc steps.

To build set in local.build.properties:

	include.module.xmlcalabash = true

To use xmlcalabash uncomment in conf.xml

    <module class="org.exist.xquery.modules.xmlcalabash.XMLCalabashModule"
          uri="http://xmlcalabash.com" />

This has been tested with XML Calabash v0.9.21