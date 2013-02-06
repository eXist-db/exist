2013-02-06
eXist Solaris 10/11/Open Solaris SMF (Service Management Framework)
Adam Retter <adam@exist-db.org>


About
=====
Two sets of SMF configuration and script files have been provided for Solaris 10, 11 and OpenSolaris. These allow
eXist to be installed as a service and managed by SMF in either Jetty or Standalone startup
configurations.

The Solaris SMF provides a powerful mechanism for managing eXist as a Service and provides
advanced features such as Self-Healing. eXist will be automatically started in this way at
boot time and shutdown with the system.


Notes for eXist SMF setup
=========================
By default the scripts expect eXist to be installed to /opt/eXist-db. If you wish
to change this then change the EXIST_HOME variable in the appropriate svc-eXist-* file.
The scripts also expect the admin password of eXist to be "admin" you need to change this
to your password by setting the EXIST_ADMIN variable in the appropriate svc-eXist-* file.

By default it expects to run eXist under the user account "exist" and the group "exist".
If you wish to change this then change the values of the user and group attributes in the
appropriate eXist-*.xmf.xml file.


Installing eXist into SMF
=========================
The following commands must be run as the root user, or under OpenSolaris they may be prefixed by pfexec presuming that you have the Primary Administrator profile.

1) Create the user "exist" in the group "exist" -

# groupadd exist
# useradd -c "eXist Database" -g exist -m -s /bin/bash exist

You must ensure that the users home directory exists otherwise the service
will fail to start


2) Make sure that eXist is owned by the exist user and exist group -

# chown -R exist:exist /opt/eXist-db


3) Choose either the eXist Jetty or eXist Standalone configuration, only one may be used.

Adjust the following for your chosen configuration (Standalone configuration is shown here) -


4) Place the service script svc-eXist-standalone in /lib/svc/method -

# cp svc-eXist-standalone /lib/svc/method

NOTE - If you are using Zones then this can be done in the Global Zone, otherwise if you are in a non-global zone this is not possible as /lib/svc/method is read-only. For operation in a non-global zone you can place the svc-eXist-standalone file in a different location of your choosing and update the eXist-standalone.smf.xml file to reflect this.
You must still ensure that this service script file is owned by root:bin with 555 permissions!


5) Set the correct permissions and ownership of the service script -

# chown root:bin /lib/svc/method/svc-eXist-standalone
# chmod 555 /lib/svc/method/svc-eXist-standalone


6) Place the service manifest in the correct location - 

# cp eXist-standalone.smf.xml /var/svc/manifest/application

NOTE - This operation must be performed in the zone where you will be running eXist. If this is done in the global zone it will also be inherited to any subsequently created zones.


7) Set the correct permissions and ownership of the service manifest -

# chown root:sys /var/svc/manifest/application/eXist-standalone.smf.xml
# chmod 444 /var/svc/manifest/application/eXist-standalone.smf.xml


8) Install the eXist Standalone Service into the Solaris SMF -

If you are on Solaris 10 use -

# svccfg import /var/svc/manifest/application/eXist-standalone.smf.xml

If you are on Solaris 11 use - 

# sudo svcadm restart svc:/system/manifest-import


########
# NOTE #
########
Before starting eXist, you must ensure that the admin password in the svc-eXist-standalone script matches the actual eXist password. By default eXist ships with no password whatsover.
To set the admin password of eXist to "admin" - 

Using two terminals makes the process much easier -

Terminal 1
----------
Start up an interactive instance of the eXist server -

$ su root
# su - exist
$ /opt/eXist-db/bin/startup.sh

Terminal 2
----------
Connect to eXist with the admin client and set the admin password -

$ su root
# su - exist
$ /opt/eXist-db/bin/client.sh -s

exist:/db>passwd admin
password: admin
re-enter password: admin
exist:/db>quit

You can then check that shutting down eXist with the admin password now works, if successful you should then see the server in Terminal 1 shutdown -

$ /opt/eXist-db/bin/shutdown.sh -p admin --uri=xmldb:exist://localhost:8080/exist/xmlrpc
########


9) Enable and start the eXist Standalone service -

# svcadm -v enable eXist


You can then view the status of the service using `svcs -a | grep eXist"
If the service does not start then the log file /var/svc/log/application-eXist:default.log may yield some clues.
