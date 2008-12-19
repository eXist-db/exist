2006-11-01
eXist Solaris 10 Service Management Framework
Adam Retter <adam.retter@devon.gov.uk>


About
=====
Two sets of SMF configuration and script files have been provided for Solaris 10. These allow
eXist to be installed as a service and managed by SMF in either Jetty or Standalone startup
configurations.

The Solaris SMF provides a powerful mechanism for managing eXist as a Service and provides
advanced features such as Self-Healing. eXist will be automatically started in this way at
boot time and shutdown with the system.


Notes for eXist SMF
===================
By default the scripts expect eXist to be installed to /eXist. If you wish
to change this then change the EXIST_HOME variable in the appropriate svc-eXist-* file.
The scripts also expect the admin password of eXist to be "admin" you need to change this
to your password by setting the EXIST_ADMIN variable in the appropriate svc-eXist-* file.

By default it expects to run eXist under the user account "exist" and the group "exist".
If you wish to change this then change the values of the user and group attributes in the
appropriate eXist-*.xmf.xml file.


Installing eXist into SMF
=========================
1) Create the user "exist" in the group "exist"

e.g. useradd -c "eXist Database" -d /home/exist -g exist -m -s /bin/bash exist

You must ensure that the users home directory exists otherwise the service
will fail to start

2) chown -R exist:exist /eXist

3) Choose either the eXist Jetty or eXist Standalone configuration, only one may be used.

Adjust the following for your chosen configuration (Standalone configuration is shown here) -

4) Become Super User - su root

5) Copy svc-eXist-standalone to /lib/svc/method
If you are using Zones then this can be done in the Global Zone, otherwise if you
are in a zone this is not possible as /lib/svc/method is read-only. However for operation
in a zone you can place the svc-eXist-standalone file in a different location of your
choosing and update the eXist-standalone.smf.xml file to reflect this.
You must still ensure the svc file is owned by root:bin with 555 permissions!

6) chown root:bin /lib/svc/method/svc-eXist-standalone

7) chmod 555 /lib/svc/method/svc-eXist-standalone

8) Copy eXist-standalone.smf.xml to /var/svc/manifest/application (this needs to be done in the
Zone you are using for eXist. If this is done in the Global Zone, it will also be inherited
by any subsequently created Zones).

9) chown root:sys /var/svc/manifest/application/eXist-standalone.smf.xml

10) chmod 444 /var/svc/manifest/application/eXist-standalone.smf.xml

11) Install the eXist Service -
svccfg import /var/svc/manifest/application/eXist-standalone.smf.xml

12) Enable and start the eXist service -
svcadm -v enable eXist

You can then view the status of the service using `svcs -a | grep eXist"
If the service does not start then the log file /var/svc/log/application-eXist:default.log
may yield some clues.