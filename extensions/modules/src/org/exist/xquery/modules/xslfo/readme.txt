This module relies on the presence of Apache FOP and as such you will need to have the following jar files in your EXIST_HOME/lib/user folder:

	fop.jar
	batik-all-1.7.jar
	xmlgraphics-commons-1.3.1.jar
	avalon-framework-api-4.3.jar
	avalon-framework-impl-4.3.jar

If you set:

	include.module.xslfo = true

in EXIST_HOME/extensions/modules/local.build.properties, then the above jars will be downloaded automatically for you by the eXist build process, if they are not already present.

This has been tested with Apache FOP 0.9.5