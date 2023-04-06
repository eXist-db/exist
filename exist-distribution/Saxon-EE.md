# How to use Saxon PE or EE 

eXistDB uses an embedded copy of Saxon HE as an XSLT transformer.
If the user wishes to take advantage of the extra features of Saxon PE or Saxon EE,
and has licensed that software, these instructions will enable them to use the extra
Saxon features from within eXist, for example when invoking `fn:transform()`.

In the instructions below, `$EXIST_HOME` refers to the root directory into which eXist has been installed.
This is the directory which is prompted for (and or defaulted) by the eXist installer.

## Replace the Saxon JAR file

 * Replace the existing `$EXIST_HOME/lib/Saxon-HE-9.9.1-8.jar` with `saxon9ee.jar` fetched by downloading `https://www.saxonica.com/download/SaxonEE9-9-1-8J.zip` and unzipping.
 * At present only Saxon EE (or PE) version 9.9.1.8 is supported.
 * Confirm the JAR is present at `$EXIST_HOME/lib/saxon9ee.jar`

## Update configuration

In both `$EXIST_HOME/etc/client.xml` and `$EXIST_HOME/etc/startup.xml` replace the dependency
```
<dependency>
        <groupId>net.sf.saxon</groupId>
        <artifactId>Saxon-HE</artifactId>
        <version>9.9.1-8</version>
        <relativePath>Saxon-HE-9.9.1-8.jar</relativePath>
</dependency>
```
with a dependency on the JAR you just installed
```
<dependency>
        <groupId>net.sf.saxon</groupId>
        <artifactId>Saxon-EE</artifactId>
        <version>9.9.1-8</version>
        <relativePath>saxon9ee.jar</relativePath>
</dependency>
```

## Add your license file

Place the `saxon-license.lic` file in `$EXIST_HOME/lib`

## Update the config file

At a mimimun, you will need to replace
```
<configuration edition="HE" 
  xmlns="http://saxon.sf.net/ns/configuration"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://saxon.sf.net/ns/configuration config.xsd">
</configuration>
```
with
```
<configuration edition="EE" 
  xmlns="http://saxon.sf.net/ns/configuration"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://saxon.sf.net/ns/configuration config.xsd">
</configuration>
```
in the existing `$EXIST_HOME/etc/saxon-config.xml`

You may have specific configuration which you wish to set up.

Example instantiated configuration files for the enterprise edition can be found by visiting
     https://saxonica.com/html/download/download_page.html
  
For documentation on the contents of a Saxon configuration file, see
     http://www.saxonica.com/html/documentation/configuration/configuration-file/index.html

## (Re)start eXist

Using the normal control mechanism.

The Saxon configuration is loaded lazily. So you will need to cause this to happen by (for example) calling `fn:transform` from xquery in eXide.

You should then see a log message in `$EXIST_HOME/logs/exist.log` similar to this
```
Saxon - licensed features are SCHEMA_VALIDATION ENTERPRISE_XSLT ENTERPRISE_XQUERY PROFESSIONAL_EDITION.
```
