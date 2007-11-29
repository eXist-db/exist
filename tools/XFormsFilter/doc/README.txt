XFormsFilter Copyright 2006 Adam Retter <adam.retter@devon.gov.uk>

XFormsFilter is a Java Servlet filter that works with eXist and uses Chiba
to extend eXist (when run as a Servlet) with XForms support. At the moment only the non-scripted mode
of Chiba is supported however it should be trivial to add scripted mode support.


Licence
=======
XFormsFilter
Copyright (C) 2007 Adam Retter, Devon Portal Project <adam.retter@devon.gov.uk>
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



Prerequisites
=============
XFormsFilter provides an Ant script for building, for this you can use
either the Apache Ant provided with eXist or otherwise you will need
to have Apache Ant installed on your system; Ant is available from either
http://ant.apache.org/ or your systems software repository.

XFormsFilter has a dependency on Chiba-core and Chiba-web and as such
the source code archives need to be present in the XFormsFilter folder.
The Ant build script will attempt to download chiba-core-1.3.0-src.tar.gz and
chiba-web-2.0.0-src.tar.gz from - http://kent.dl.sourceforge.net/sourceforge/chiba
otherwise you may download these manually and place them in ${EXIST_HOME}/tools/XFormsFilter

By default the XFormsFilter will expect to generate XHTML 1.1 compatible
output (if you wish to have HTML 4.01 output see the "Configuration Options" section at the
end of the document). For XHTML 1.1 output to be generated eXist must first be configured to use
Saxon. Follow the steps outlined here - http://atomic.exist-db.org/wiki/HowTo/XSLT2
If you want HTML 4.01 then you do not need Saxon, eXist's default of Xalan will be fine.



Building/Installing
===================
It is recommended that eXist is shutdown during the installation. You Should also
make a copy of your eXist Home folder $EXIST_HOME and a backup of your database
before attempting this, in case anything should go wrong.

If you wish to build and install using Ant provided with eXist then you
can use either the build.sh or build.bat files provided -

Linux/Unix
----------
$ cd $EXIST_HOME/tools/XFormsFilter
$ ./build.sh deploy

Windows
-------
cd %EXIST_HOME%\tools\XFormsFilter
build.bat deploy

XFormsFilter is now installed for eXist.



Configuration
=============
You must configure your servlet container to load the XFormsServlet and setup
the mapping so that it works with eXist. Below are details for configuring the
embedded Jetty that ships with eXist and runs using startup.bat or startup.sh,
configuration for other servlet containers should be similar.

Copy the $EXIST_HOME/tools/XFormsServlet/web.xml file to $EXIST_HOME/webapp/WEB-INF

It is worth taking a look at the web.xml file as this is where the configuration
options for XFormsFilter are stored. These are covered in more detail in the
Configuration Options section below.

WARNING - Unfortunately this web.xml will disable Apache Axis which provides
eXist's webservices interface. For some reason XFormsFilter/Chiba does not
play nicely with Apache Axis. I would like to resolve this eventually.
However this is of no concern unless you access eXist webservices directly,
this will not effect the SOAPServer.

Chiba makes use of a Cascading Style Sheet to layout the (X)HTML generated forms in the
browser, by default the XFormsFilter will look for the file xforms.css in the eXist database
in a /db/system/xformsfilter collection. The default Chiba CSS file can be found in
$EXIST_HOME/tools/XFormsFilter/resources/styles, you will need to store this (or create your own)
in the database as /db/system/xformsfilter/xforms.css.



Configuration Options
=====================
These options are declared in web.xml which you should have copied as part of
the install process.

The default values should work fine for Linux if you wish to use XHTML 1.1
output and you installed eXist to /eXist, otherwise make the
appropriate changes.

chiba.debug - may be either "true" or "false"and determines if Chiba should log
additional debugging information.

chiba.config - the path and filename of the Chiba config file. A Default is
provided in $EXIST_HOME/tools/XFormsFilter. It is unlikely you will need to modify
this file.

chiba.upload - the location that Chiba uses to store temporarily
store files when they are uploaded from the web-browser.

chiba.xslt.cache - Should XSLT be cached

chiba.web.xslt.path - just the path to where Chiba can find the main
XSL Stylesheet.

chiba.web.xslt.default - the filename of the XSL stylesheet on the path
chiba.web.xslt.path that should be used for rendering the XForm. For XHTML 1.1
output this is "xhtml.xsl" for HTML 4.01 output this is "html.xsl"

chiba.CSSPath - just the path for the CSS file xforms.css to link to in the XHTML or HTML output.
This is relative to the servlet context - typically /eXist.
So if you wished to load the CSS from a collection called "mycollection" - /servlet/db/mycollection
Otherwise if you wished to load the CSS from a folder in webapp called "myfolder" - /myfolder
The default Chiba Web Stylesheet xforms.css is provided in $EXIST_HOME/tools/XFormsFilter/resources/styles.

chiba.XFormsSessionChecking - the amount of time (in milliseconds) between checking
for expired XForms sessions.

chiba.XFormsSessionTimeout - the inactivity expiry time of an XFormsSession

