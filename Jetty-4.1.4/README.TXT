----------------------------------------------------------------------
$Id: README.TXT,v 3.101 2002/09/27 17:09:21 gregwilkins Exp $
----------------------------------------------------------------------

Jetty 4
=======

Jetty is an Open Source HTTP Servlet Server written in 100% Java.
It is designed to be light weight, high performance, embeddable,
extensible and flexible, thus making it an ideal platform for serving
dynamic HTTP requests from any Java application.

Installation
============
Jetty is installed simply by untaring the tgz file.
There are two distributions. The complete distribution has everything
needed to run and is precompiled. The source distribution has no
3rd party jars, no demo, no test harnesses and is not pre compiled.


Starting Jetty
==============

In order to start Jetty the following steps must be taken: 

 1) Setting the classpath to the appropriate jars in $JETTY_HOME/lib 
 
 2) Adding to the classpath any jars from $JETTY_HOME/ext that your 
    java environment does not provide.
 
 3) If using JSPs, add the tools.jar from your JRE lib and ant.jar from jakarta-ant-1.5
    to the classpath.
 
 4) Running "java org.mortbay.jetty.Server <configfile>"


Several different mechanisms have been provided to automate this process
and you should pick the one that best suits your environment.


Jetty start.jar 
===============

The start.jar file in the Jetty distribution may be run with

  java -jar start.jar <configfile> ...

For example

  java -jar start.jar etc/admin.xml etc/demo.xml

On some systems with graphical file browsers, it may be possible to start
jetty by double clicking on this jar file. For more details see 
$JETTY_HOME/launcher/README.txt


JDK 1.4 Quick start
===================

If using jdk 1.4 run:

  cd <jetty install directory>
  java -jar lib/org.mortbay.jetty.jar etc/demo.xml

Where cd and / have been repaced with any non-unix platform versions. 
Browse to http://localhost:8080
  
For the JMX enabled demo run:
  java -jar lib/org.mortbay.jmx.jar etc/demo.mlet

If using JDK 1.3, you can still run this way, but you must add the
libraries from the ext to your classpath.

Note that this start method will not allow JSPs to work, unless
you have the tools.jar from your JDK and ant.jar from jakarta-ant
in your system classpath.

The other start methods below will set this classpath if possible.



Ant Quick Start
===============

If you have the ant java build tool ant installed, you may use that to
start jetty.  The demo can be started with:

  Unix:  ant demo
  Win32: ant.bat demo


The JMX enabled demo can be started with

  Unix:  ant demo.jmx 
  Win32: ant.bat demo.jmx


The default jetty.xml configuration can be started with:

  Unix:  ant run
  Win32: ant.bat run


An alternative configuration file can be run with

  Unix:  ant -Djetty.run=acme.xml run
  Win32: ant.bat -Djetty.run=acme.xml run



Unix Start Scripts
==================

On unix, you can use the supplied shell script to set the classpaths
and run Jetty:

  export JETTY_HOME=<jetty install directory>
  export JAVA_HOME=<JRE install directory>
  $JETTY_HOME/bin/jetty.sh demo

This will start a demo Jetty server on port 8080. Point your browser at
  http://localhost:8080

This script is controlled by the following environment variables:

 JAVA       - executable JVM
 JAVA_HOME  - Home of java, specifically $JAVA_HOME/lib/tools.jar
 JETTY_HOME - Home of jetty
 JETTY_CONF - Location of the script's configuration file, set to
              /etc/jetty.conf, or $JETTY_HOME/etc/jetty.conf by
              default.
 JETTY_RUN  - Directory for jetty pid file
 JETTY_LOG  - Directory for jetty output log

If any of these variables are not set, then frantic searches are made
of likely locations to try and find them.

The /etc/jetty.conf file may contain:
 - Coment lines starting with #
 - References to files, which are considered .xml server configurations, and
   passed to jetty for serving.
 - References to directories (i.e. /etc/jetty.d). Each directory is scanned
   for .xml files, and those found are passed to jetty for serving.

Note: If .xml configuration files are given in the command line, jetty will
not use /etc/jetty.conf. This is useful for running single servers while
testing.

Note: if you don't setup the /etc/jetty.conf file, then running
"jetty.sh start" without a config specified may start multiple
servers (demo, jetty, watchdog) on the same port - causing errors.

Other jetty.sh actions are:
   start -- To start the server.
   stop  -- Stops a server started with "start".
   run   -- Runs Jetty in foreground mode.
   check -- Does nothing. Useful checking the configuration.




Win2000 Start Scripts
=====================
Due to problems with the servlet security model (see below).  It is vital
that windows users use the exact case for drive letters, URLs and filenames.

run:
  set JETTY_HOME=<jetty install directory>
  set JAVA_HOME=<java install directory>
  %JETTY_HOME%\bin\jetty.bat

Browse to:
  http://localhost:8080

If you still have problems:
  - try installing in a path with no spaces in the directory name.

  - Check that the case of JETTY_HOME is exactly the same as reported by
    your system. Also read the section on alias checking in the web
    application security section below.

If this doesn't work - you can try the ant quick start below or you
will need to read some more and setup the environment.



Manual Start
============

If you are using jdk1.4, you should use the path (or non-unix equivalent):

  CLASSPATH=\
    $JETTY_HOME/lib/org.mortbay.jetty.jar:\
    $JETTY_HOME/lib/javax.servlet.jar:\
    $JETTY_HOME/lib/org.apache.jasper.jar


For jdk1.2 or jdk1.3 us the following classpath, you need to use an
alternative jetty jar and add the XML jars. Thus the classpath 
to use is:

  CLASSPATH=\
    $JETTY_HOME/lib/org.mortbay.jetty-jdk1.2.jar:\
    $JETTY_HOME/lib/javax.servlet.jar:\
    $JETTY_HOME/lib/org.apache.jasper.jar:\
    $JETTY_HOME/ext/javax.xml.jaxp.jar:\
    $JETTY_HOME/ext/org.apache.crimson.jar


To use the default SSL you will also need to add the the jar below, 
which is a merge of the jsse, jnet and jcert jars from the JSSE package
(see $JETTY_HOME/demo/webapps/jetty/doc/JsseSSL.html for more details):

    $JETTY_HOME/ext/com.sun.net.ssl.jar


To use JMX the classpath must also include:

    $JETTY_HOME/ext/jmxri.jar
    $JETTY_HOME/ext/jmxtools.jar 


To run JSP with the JDK compiler, you will also need to add the
jar containing the JDK compiler (if you wish to use another compiler
you will have to configure jasper) and the ant.jar from jakarta-ant 1.5 
or later.

If your JVM supports the -server flag, then it can be a useful
option to include when starting Jetty.  It can reduce memory size and
avoid some JVM crashes.


Running the Demo Server
=======================
A Demo server can be run on port 8080 with the commands:

  cd $JETTY_HOME
  java org.mortbay.jetty.Server etc/admin.xml etc/demo.xml


You can access this demo with at http://localhost:8080
The demo also runs a simple admin servlet at http://localhost:8081,
with user username=admin, password=admin.


This script finds the JVM and sets the CLASSPATH, so it can be used as
an /etc/init.d script and also looks for xml config files in
/etc/jetty (among other places).


Configuring the Server
======================

The demo server contains a tutorial on Jetty and other documentation
that should be read before trying to configure Jetty.

The class org.mortbay.jetty.Server reads an xml configuration
file and configures one or more instances of org.mortbay.http.HttpServer:

  java org.mortbay.jetty.Server [configuration.xml]

The class org.mortbay.http.HttpServer is the main class to be
configured.  It has a configuration API and a command line API and
can be run with

  java org.mortbay.http.HttpServer [options | -?]

The org.mortbay.jetty.Server class is simply an xml configuration
frontend to org.mortbay.http.HttpServer.

There is a Jetty extension configuration mechanism for web
applications.  If a file WEB-INF/web-jetty.xml is found, it is treated
as a Jetty XmlConfiguration file and applied to the
WebApplicationContext object when the context is started.



Building from source
====================
An Ant build file is included in build.xml.  This file is configured to
use the jikes compiler from IBM. To use the normal JDK compiler comment
out the jikes line in build.xml.  Ant is available from

    http://jakarta.apache.org/ant/index.html

You can run ant for the following targets:

    ant         - Build source
    ant all     - Build everything
    ant test    - Build and run tests
    ant demo    - Build and run the demo
    ant clean   - Remove generated files


Test Harnesses
==============
The class org.mortbay.http.TestRFC2616 is a test harness linked
to the RFC document.  This and the other test harnesses can be run with

  java org.mortbay.util.TestHarness
  java org.mortbay.http.TestHarness

Note that the test harnesses are not in the jar file, so you will need
to build all the source and run with the source hierarchy in the CLASSPATH.


Running Watchdog Tests
======================
See the etc/watchdog.xml configuration file comments for instructions
on how to run the Jakarta Watchdog 4.0 tests.


Distribution
============
Jetty distributions and information is available from:

   http://jetty.mortbay.org
   http://sourceforge.net/project/jetty
   ftp://jetty.mortbay.org/pub



How to Contribute
=================
Comments, contributions, feedback, bugs, testing, etc. please....

The mailing lists are the best place to start:

  http://www.yahoogroups.com/group/jetty-announce/
  http://www.yahoogroups.com/group/jetty-discuss/
  http://www.yahoogroups.com/group/jetty-support/
  http://sourceforge.net/mail/?group_id=7322



NOTES
=====

HTTP Server vs Servlet Server
-----------------------------
Jetty can be considered both as a HTTP server with it own powerful
extension architecture and as a Servlet Container implementing the
standards for web applications.  The style of configuration of these
two view is significantly different.  The Jetty tutorial provided
by the demo server should be read in detail to understand the 
difference between these two approaches.


JDK 1.4 Support
---------------
Jetty has been developed for JDK 1.2, can be run with 1.2, 1.3 and 1.4.

Furthermore, the new features of JDK1.4 are now being supported in a src1.4
hierarchy.  If Jetty is built and the 1.4 classes are available, the source
in $JETTY_HOME/src1.4 is used to replace or enhance the standard classes.

These extra or enhanced classes are placed in the
org.mortbay.jetty.jar file, which must be in your path instead of 
org.mortbay.jetty-jdk1.2.jar for the enhanced classes to replace the standard
classes.

The SocketChannelListener class uses non blocking IO for idle connections to
greatly reduce the threads needed by Jetty.


Minimal HTTP jar
----------------
A minimal HTTP server can be built with the ant target mini.http.jar.
This builds the lib/org.mortbay.http.jar file which contains:
  + Full HTTP/1.1 server
  + Basic Authentication support
  + ResourceHandler with cache and range support.
  + DumpHandler as dynamic content demo.
  + NCSA request log
  + javax.servlet.http.Cookie support.

The mini server can be run with

  java -jar lib/org.mortbay.http.jar


JettyExtra
-----------
Extra jetty functionality is available via the JettyExtra
package.  This include gtp, tools and SASL support.

JSP Engine
----------
Jetty uses the Jasper JSP engine from jakarta to provide java
server pages. Jetty switched to use Jasper2 for the 4.1.x releases,
as it promises greater performance and standards compliance.
However, jasper2 requires the ant.jar in the classpath and still
has some backwards compatibility issues with older JSPs.

The jasper2 engine can simply be replace with the jasper1 engine
by replacing the org.apache.jasper.jar from a Jetty 4.0 release.


JSP Classpaths
--------------
The context classloader is passed to the Jasper JspServlet and
is used as the parent loader of the JspLoader.
A file classpath is also passed for used by the JSP compiler. Note
that if the context is a packed WAR file, then there is no
reasonable file based classpath that can be passed to the compiler.

If this proves to be a problem for you, then unpack your WAR files
(which is what most other containers do by default anyway).


Unimplemented WebApplication features
-------------------------------------
The following WebApplication tags are not implemented in standalone
jetty.

  <distributable>
  <resource-ref>
  <env-entry>
  <ejb-ref>

When Jetty is used in a J2EE container such JBoss or JOnAS then 
these may be implemented by those containers.


Unix SystemV Init
-----------------
Jetty includes a SystemV Init script that has been tested under
RedHat Linux 6.2. This script allows Jetty to be automatically started
and stoped when the operating system is booted or shutdown.

To use the script, follow these steps:

  - Login as root.

  - Make the script executable:

       chmod u+x $JETTY_HOME/bin/jetty.sysvinit

  - Place a  symbolic link to the script in the /etc/rc.d/init.d directory:

       ln -s $JETTY_HOME/bin/jetty.sysvinit /etc/rc.d/init.d/jetty

    you can also copy the script there. Just remember to make it executable.

  - Edit the script and make sure that the JETTY_HOME and JAVA_HOME
    variables are set properly.

  - Enable the script:

      chkconfig --add jetty
      chkconfig --level 345 jetty on

    you can also use ntsysv if available for this purpose.

To start the jetty server for the first time without rebooting the machine,
execute:

   /etc/rc.d/init.d/jetty start

Check /var/log/messages to see if everything worked fine.

The Jetty SystemV Init script uses the $JETTY_HOME/bin/jetty.sh shell
script to launch Jetty. Read the shell script's documentation above,
or the one in the script's initial comments to learn about available
configuration options.


Win32 Service
-------------
The win32 directory contains instructions of how to start Jetty as
a win32 service.


Web Application Security
------------------------
Jetty makes the following interpretations for the configuration of security
constraints within a web.xml file:

 + Methods PUT, DELETE and GET are disabled unless explicitly enabled.

 + If multiple security-constraints are defined, the most specific
   applies to a request.

 + A security-constraint an empty auth-constraint forbids all access by any
   user:

    <security-constraint>
      <web-resource-collection>
       <web-resource-name>Forbidden</web-resource-name>
        <url-pattern>/auth/noaccess/*</url-pattern>
      </web-resource-collection>
      <auth-constraint/>
    </security-constraint>


 + A security constraint with an auth constraint with a role of "*"
   gives access to all authenticated users:

    <security-constraint>
      <web-resource-collection>
        <web-resource-name>Any User</web-resource-name>
        <url-pattern>/auth/*</url-pattern>
      </web-resource-collection>
      <auth-constraint>
        <role-name>*</role-name>
      </auth-constraint>
    </security-constraint>

 + A security-constraint with no auth-constraint and no data contraint
   gives access to any request:

    <security-constraint>
      <web-resource-collection>
        <web-resource-name>Relax</web-resource-name>
        <url-pattern>/auth/relax/*</url-pattern>
      </web-resource-collection>
    </security-constraint>


 + On platforms without the / file separator or when the system parameter
   org.mortbay.util.FileResource.checkAliases is true, then the
   FileResouce class compares the absolutePath and canonicalPath and
   treats the resource as not found if they do not match.  THIS means
   that win32 platforms need to exactly match the case of drive letters
   and filenames.

 + Dynamic servlets by default, can only be loaded from the context classpath.
   Use ServletHandler.setServeDynamicSystemServlets to control this behaivour.


It is strongly recommended that secure WebApplications take following
approach.  All access should be denied by default with

    <security-constraint>
      <web-resource-collection>
       <web-resource-name>Default</web-resource-name>
        <url-pattern>/</url-pattern>
      </web-resource-collection>
      <auth-constraint/>
    </security-constraint>


Specific access should then be granted with constraints like:

  <security-constraint>
    <web-resource-collection>
      <url-pattern>/public/*</url-pattern>
      <url-pattern>/images/*</url-pattern>
      <http-method>GET</http-method>
      <http-method>HEAD</http-method>
    </web-resource-collection>
    <web-resource-collection>
      <url-pattern>/servlet/*</url-pattern>
      <http-method>GET</http-method>
      <http-method>HEAD</http-method>
      <http-method>POST</http-method>
    </web-resource-collection>
    <auth-constraint>
      <role-name>*</role-name>
    </auth-constraint>
  </security-constraint>


Session Security
----------------
Jetty uses the standard java.util.Random class to generate session IDs.  This
may be insufficient for high security sites.  The SessionManager instance can
be initialized with a more secure random number generator, such as
java.security.SecureRandom. The XML to do this to a webapplication is:

  <Call name="addWebApplication">
    <Arg>/myapp/*</Arg>
    <Arg><SystemProperty name="jetty.home" default="."/>/demo/webapps/myapp</Arg>
    <Arg><SystemProperty name="jetty.home" default="."/>/etc/webdefault.xml</Arg>
    <Arg type="boolean">false</Arg> 
    <Call name="getServletHandler">
      <Set name="sessionManager">
        <New class="org.mortbay.jetty.servlet.HashSessionManager">
          <Arg><New class="java.security.SecureRandom"/></Arg>
        </New>
      </Set>
    </Call>
  </Call>

Note: initialising the SecureRandom object is a one-off time consuming
operation which may cause the initial request to take much longer.


Authentication Realms
---------------------
The authentication mechanisms use and abstract
org.mortbay.http.UserRealm interface for authentication. The default
implementation is a hashtable that is initialized from a properties
file. Jetty also includes org.mortbay.http.JDBCUserRealm for SQL based
authentication data.  Users may simply implement their own Realms and
platforms such as JBoss use this mechanism to link the Jetty
authentication mechanism to their own.


XML Parsing
-----------
Jetty comes with the JAXP 1.1 reference XML parser, which is crimson
from apache.  The jars have been renamed with their main package name.
Jetty does not use any of the DOM classes, so these jars can be
trimmed significantly if you want to reduce size.

Use the org.xml.sax.parser property to specify an alternative SAX parser.

The DTDs used by jetty are resolved as resources from the system
classpath, thus they are stored in org.mortbay.http/web.dtd and
org.mortbay.util.configure.dtd within the source tree and jar file.
Copies are also placed in etc for reference.


DEBUGGING
=========

The debug and logging output of jetty can be controlled with java
System properties:

    DEBUG - If set, debug output is enabled

    DEBUG_PATTERNS - If set to a comma separated list of strings, Then
        debug output is only produced from those classes whose fully
        qualified class name contains one of the strings as a substring.

    DEBUG_VERBOSE - Set to an integer verbosity level for use in
        controlling debug verbosity (see verbose(int))

Using this with the java program:

   java [-DDEBUG [-DDEBUG_PATTERNS="MyClass,my.package"] [-DDEBUG_VERBOSE=n] ] my.package.main

example:

   java -DDEBUG -DDEBUG_PATTERNS=com.acme.MyServlet,HttpHandler org.mortbay.jetty.Server


There is also a debug servlet that can change the debug setting of a
running server.  It is configured by the admin.xml file and is run on
port 8081 as part of the standard Jetty demo.

Finally the JMX MBeans provided by the JettyExtra package also provide
a mechanism to configure debugging and logging at runtime.


Support FAQ
===========
 + This list if for jetty related support questions.  General
   questions about servlets and/or java should not be posted here.

 + Before posting, please check the archives that the question has
   not been asked before by checking the archives at 
       http://groups.yahoo.com/group/jetty-support

 + Please also read the improving documentation at
       http://jetty.mortbay.org/jetty/doc/index.html
   Please read the tutorial, then read it again.

 + Jetty 4 configuration is based around the concept of contexts,
   which were introduced in the 2.2 servlet specification.  It is
   important to understand contexts when configuring Jetty and it may
   be worth while reading the 2.3 servlet specification in addtion to
   the Jetty documentation.

 + Before posting, try increasing the debug output of Jetty.  Debugging 
   control is described in the README file.  Even if you cannot spot the
   problem in the output, it may be valuable to make the trace available
   to whoever helps you with your problem.

 + When posting a support question, please provide as much information as
   possible, which should include:
      - The version of Jetty.
      - The version of java.
      - Your Operating system.
      - Overview of your configuration.
      - What URL was tried.
      - What browser and what it rendered.
      - Details of the problem, including stack traces and/or log files.
      - Details of what you have tried to make it work.
      
 + For JSP problems, consider checking JSP forums first. Jetty uses the
   Jasper engine from jakarta.apache.org - bugs and all.
   If you can write a small JSP that demonstrates your problem, you 
   will get much faster results.

 + For general class loading problems, the solution is often best to start
   with your classes on the system classpath until you are more familiar
   with the configuration of Contexts. 

 + If in doubt about if you should post or not - please post, we are pretty
   friendly and will tell you nicely if it is not appropriate.
