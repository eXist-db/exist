-----
Build
-----

This module relies on the presence of the following jar files in your EXIST_HOME/lib/user folder:

	calabash.jar
	saxon9-s9api.jar


If you are building from source then the module build process will try and download
these files for you if you enable the module.

Please read XML Calabash documentation for information on other dependencies related to using specific
optional XProc steps.

To build set in local.build.properties:

	include.module.xmlcalabash = true

To use xmlcalabash uncomment in conf.xml

    <module class="org.exist.xquery.modules.xmlcalabash.XMLCalabashModule"
          uri="http://xmlcalabash.com" />

This has been tested with XML Calabash v0.9.23


-----
Usage
-----

upload an XProc pipeline somewhere into eXist (ex. /db/test/hello.xproc)

<?xml version="1.0"?>
<p:declare-step version="1.0" xmlns:p="http://www.w3.org/ns/xproc">
<p:input port="source">
  <p:inline><doc>Helloworld</doc></p:inline>
</p:input>
<p:output port="result"/>
<p:identity/>
</p:declare-step>


invoke using xmlcalabash:process (in sandbox for example)

let $result := xmlcalabash:process("xmldb:exist:///db/test/hello.xproc","-")
return
   $result


------
Status
------

Currently there are a few limitations with this extension

* func signature will change soon to accept xml for pipeline, output, as well as specify input/output/parameter ports and options ... for now its primitive

* function returns a string (which you can parse back into xml using eXist function) ... need to stay this way for a little bit during testing, will add smart serialization in a bit

* p:xquery has no context with eXist ... this is a big limitation, but there are several ways around this

* use xmldb:exist type URI

* no documentation, will probably add to existing XProc area

I wanted to 'throw over the wall' the draft version of this extension so I can get feedback now.
