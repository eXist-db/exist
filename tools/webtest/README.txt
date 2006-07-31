The Ant scripts in this directory are used for an automated test of eXist's web integration and 
HTTP based interfaces. The Ant script requires Canoo's WebTest distribution. It will be downloaded
and installed when you run the script the first time.

Usage: from the top-level directory of your eXist installation run:

build.sh -f tools/webtest/build.xml

The actual XQuery and XML files for the tests can be found in directory webapp/webtest.