SOAPServer Copyright 2007 Adam Retter <adam.retter@devon.gov.uk>
Contributions by José María Fernández

SOAPServer is a Server module for the eXist EXistServlet that allows
webservices to be written in XQuery. Allowing for easy transportation
of XML data which can also be query dependant.
The SOAPServer provides both document literal and RPC encoded styles.


Notes
=====
The SOAP Server operates on XQuery Modules stored in the database. These modules must
have a .xqws (XQuery Web Service) extension instead of a .xqm extension to be recognised as webservices.


Installing
==========
The SOAP Server uses XSLT to create descriptions for XQWS (XQuery Web Service) functions and also
to return a SOAP Response. This enables the user to customize their descriptions and the SOAP Response
if needed by altering the XSLT files.
 
The SOAP Server expects the requisite XSLT files to be found in the /db/system/webservice collection of the database.
 
1) Create the collection "webservice" in the /db/system collection
 
2) Store the .xslt files found in $EXIST_HOME/tools/SOAPServer into the /db/system/webservice collection

3) If you are using Saxon instead of Xalan, you will need to make a small change to soap.response.xslt, details are in the file.


Thats it :-)
 
 
Examples
========
 
For an example see $EXIST_HOME/tools/SOAPServer/echo.xqws, to use this you will need to store it anywhere in the db,
and call it from the REST e.g. http://localhost:8080/exist/rest/db/mycollection/echo.xqws
 
The WSDL document for an XQWS is available by appending ?WSDL onto the Request URL.
 