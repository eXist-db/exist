This module relies on the presence of Apache FOP and as such you will need to have the following jar files in your EXIST_HOME/lib/user folder:

	fop-2.1.jar
	xmlgraphics-commons-2.1.jar
	batik-svg-dom-1.8.jar
    batik-bridge-1.8.jar
    batik-awt-util-1.8.jar
    batik-transcoder-1.8.jar
    batik-extension-1.8.jar
    batik-ext-1.8.jar
	avalon-framework-api-4.3.1.jar
	avalon-framework-impl-4.3.1.jar

If you set:

	include.module.xslfo = true

in EXIST_HOME/extensions/modules/local.build.properties, then the above jars will be downloaded automatically for you by the eXist build process, if they are not already present.

This has been tested with Apache FOP 2.1