ScaleImageJAI
=============

A general purpose servlet to scale (and crop) images using fast Java Advanced Imaging.
The servlet can work with images stored either in the database or the file system. It 
is fast enough to scale images on the fly and processes TIFF, JPEG, PNG and other 
formats. Output will always be a PNG.

Rendered images can be cached on a file system path.

Installation
------------

Get the Java Advanced Imaging jars for your platform from

https://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-java-client-419417.html 

Please choose the CLASSPATH installation bundle. Open the downloaded archive and 
copy the contents of the lib/ folder to $EXIST_HOME/lib/user.

- If you are on MacOSX, you can just extract the jai_codec.jar and jai_core.jar files from the Linux CLASSPATH installation bundle and place these in $EXIST_HOME/lib/user

You should now be able to compile the servlet from EXIST_HOME with

./build.sh -f extensions/images/build.xml

Next, you need to register the servlet in EXIST_HOME/webapp/WEB-INF/web.xml as follows:

<servlet>
	<servlet-name>ScaleImageJAI</servlet-name>
	<servlet-class>org.exist.http.servlets.ScaleImageJAI</servlet-class>

	<init-param>
		<param-name>base</param-name>
		<param-value>xmldb:exist:///db</param-value>
	</init-param>

	<init-param>
	    <param-name>output-dir</param-name>
	    <param-value>/path/to/cache/directory</param-value>
	</init-param>
</servlet>

where "output-dir" should point to an existing directory on your server. To use 
the servlet from eXist, make sure your 
EXIST_HOME/webapp/WEB-INF/controller-config.xml has a mapping:

<forward pattern="/images" servlet="ScaleImageJAI"/>

Usage
-----

After restarting eXist, you should be able to access any image stored in the db
with an URL like this:

http://localhost:8080/exist/images/scale/path/to/image.tif?s=512

Please note that /path/to/image should not include the leading /db for the db
root collection.

The general structure of the URL is:

/images/action/path?parameters

where action can be either "scale" or "crop". "scale" accepts one parameter: "s=size"
to specify the desired output size. If you specify 512, the larger side of the output
image will have 512 pixels.

"crop" takes 4 parameters: "x", "y", "w", "h", which define the bounding box to crop.
