2009-09-16
eXist Solaris 10/Open Solaris SMF (Service Management Framework)
Adam Retter <adam@exist-db.org>


About
=====
Two sets of SMF configuration and script files have been provided for Solaris 10 and OpenSolaris. These allow
eXist to be installed as a service and managed by SMF in either Jetty or Standalone startup
configurations.

The Solaris SMF provides a powerful mechanism for managing eXist as a Service and provides
advanced features such as Self-Healing. eXist will be automatically started in this way at
boot time and shutdown with the system.


Notes for eXist SMF setup
=========================
By default the scripts expect eXist to be installed to /opt/eXist. If you wish
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
# useradd -c "eXist Database" -d /home/exist -g exist -m -s /bin/bash exist

You must ensure that the users home directory exists otherwise the service
will fail to start


2) Make sure that eXist is owned by the exist user and exist group -

# chown -R exist:exist /opt/eXist


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

# svccfg import /var/svc/manifest/application/eXist-standalone.smf.xml


9) Enable and start the eXist Standalone service -

# svcadm -v enable eXist


You can then view the status of the service using `svcs -a | grep eXist"
If the service does not start then the log file /var/svc/log/application-eXist:default.log may yield some clues.
