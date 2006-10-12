XFormsFilter Copyright 2006 Adam Retter <adam.retter@devon.gov.uk>

XFormsFilter is a Java Servlet filter that works with eXist and uses Chiba
to extend eXist (when run as a Servlet) with XForms support. At the moment only the non-scripted mode
of Chiba is supported however it should be trivial to add scripted mode support.


Licence
=======
XFormsFilter
Copyright (C) 2006 Adam Retter, Devon Portal Project <adam.retter@devon.gov.uk>
www.devonline.gov.uk

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

Included in this package are source files from the Chiba
project - www.sourceforge.net/projects/chiba. Chiba is released seperately
under the Artistic Licence.


Building
========
This is not strictly nessecary for use as XFormsFilter.jar is provided,
However if you wish to build this project from source using Eclipse a .project
file has been provided, but you will need to place the following JAR
files in the XFormsFilter/lib folder -

chiba-1.2.0.jar					http://www.sourceforge.net/projects/chiba
commons-fileupload-1.1.jar		http://jakarta.apache.org/commons/fileupload
commons-httpclient-3.0.1.jar	http://jakarta.apache.org/commons/httpclient
dwr-1.1.1.jar					http://getahead.ltd.uk/dwr/download
javax.servlet.jar				Can be copied from $EXIST_HOME/lib/core or downloaded as part of the J2EE from http://java.sun.com/javaee/downloads/index.jsp
log4j-1.2.13.jar				http://logging.apache.org/log4j

At the time of writting these were the current jar's that are known to work,
you may however try never versions but these have not yet been tested.

Once you have built with Eclipse or using the javac compiler (not described
here), you will need to JAR up the class files, something like the following
should work -

cd $EXIST_HOME/tools/XFormsFilter
jar cvf XFormsFilter.jar org/ uk/ README.txt


Installing
==========
It is recommended that eXist is shutdown until you reach step 8. You Should also
make a copy of your eXist Home folder $EXIST_HOME and a backup of your database
before attempting this in case anything should go wrong.

1) By default the XFormsFilter will expect to generate XHTML 1.1 compatible
output (if you wish to have HTML 4.01 output skip to step 2), for XHTML 1.1
output to be generated eXist must first be configured to use Saxon. Follow
the steps outlined here - http://wiki.exist-db.org/space/Howtos/Adding+XSLT+2.0+(Saxon)

2) Copy XFormsFilter.jar into $EXIST_HOME/lib/user

3) The JAR files described in the BUILD documentation (above) will also need
to be copied to $EXIST_HOME/lib/user

4) There are some additional JAR files needed for running Chiba that must also
be copied to $EXIST_HOME/lib/user

commons-codec-1.3.jar			http://jakarta.apache.org/commons/codec
javax-activation-1.0.2.jar		Downloaded as part of the J2EE from http://java.sun.com/javaee/downloads/index.jsp
javax-mail-1.3.jar				http://java.sun.com/products/javamail

5) Chiba uses a cutom patched version of the Apache Jakarta Commons JXPath to
work around a few known problems in JXPath. eXist already includes a JXPath
JAR as part of its Cocoon integration. 

You must delete the file $EXIST_HOME/lib/cocoon/commons-jxpath-1.2.jar and place
commons-jxpath-1.2-patched.jar from the Chiba Core project in
$EXIST_HOME/lib/cocoon, it is available from - http://chiba.cvs.sourceforge.net/*checkout*/chiba/chiba-sandbox/lib/commons-jxpath-1.2-patched.jar

6) You must configure your servlet container to load the XFormsServlet and setup
the mapping so that it works with eXist. Below are details for configuring the
embedded Jetty that ships with eXist and runs using startup.bat or startup.sh,
configuration for other servlet containers should be similar.

Copy the $EXIST_HOME/XFormsServlet/web.xml file to $EXIST_HOME/webapp/WEB-INF

It is worth taking a look at the web.xml file as this is where the configuration
options for XFormsFilter are stored. These are covered in more detail in the
Configuration section below.

WARNING - Unfortunately this web.xml will disable Apache Axis which provides
eXist's webservices interface. For some reason XFormsFilter/Chiba does not
play nicely with Apache Axis. I would like to resolve this eventually.
However this is of no concern unless you access eXist webservices directly.

7) Symlink, or copy the included Stylesheets.

If you want XHTML 1.1 output -

cp $EXIST_HOME/tools/XFormsFilter/xslt/xhtml-form-controls.xsl $EXIST_HOME/
cp $EXIST_HOME/tools/XFormsFilter/xslt/xhtml-ui.xsl $EXIST_HOME/

If you want HTML 4.01 -

cp $EXIST_HOME/tools/XFormsFilter/xslt/html-form-controls.xsl $EXIST_HOME/
cp $EXIST_HOME/tools/XFormsFilter/xslt/ui.xsl $EXIST_HOME/

8) Store the CSS in the database (this step is optional or customisable,
read about CHIBA_CSS in the configuration section).

Create the collection /db/xforms and store the file $EXIST_HOME/XFormsFilter/styles/xforms.css


Configuration
=============
These options are declared in web.xml which you should have copied as part of
the install process.

The default values should work fine for Linux if you wish to use XHTML 1.1
output and you installed eXist to /usr/local/eXist, otherwise make the
appropriate changes.

CHIBA_DEBUG - may be either "true" or "false"and determines if Chiba should log
additional debugging information.

CHIBA_CONFIG - the path and filename of the Chiba config file. A Default is
provided in $EXIST_HOME/XFormsFilter. It is unlikely you will need to modify
this file.

CHIBA_TEMP_UPLOAD_DESTINATION - the location that Chiba uses to store temporarily
store files when they are uploaded from the web-browser.

CHIBA_STYLESHEET_PATH - just the path to where Chiba can find the main
XSL Stylesheet.

CHIBA_STYLESHEET_FILE - the filename of the XSL stylesheet on the path
CHIBA_STYLESHEET_PATH that should be used for rendering the XForm. For XHTML 1.1
output this is "xhtml.xsl" for HTML 4.01 output this is "html.xsl"

CHIBA_CSS - The CSS file to link to in the XHTML or HTML output. May be either 
a full URL or a relative URL from the server root. The Chiba Web Stylesheet
xforms.css is provided in $EXIST_HOME/XFormsFilter/xslt.
