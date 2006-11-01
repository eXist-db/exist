2006-11-01
eXist Solaris 10 Service Management Framework
Adam Retter <adam.retter@devon.gov.uk>


About
=====
Two sets of SMF configuration and script files have been provided for Solaris 10. These allow
eXist to be installed as a service and managed by SMF in either Jetty or Standalone startup
configurations.

The Solaris SMF provides a powerfull mechanism for managing eXist as a Service and provides
advanced features such as Self-Healing. eXist will be automatically started in this way at
boot time and shutdown with the system.


Notes for eXist SMF
===================
By default it expects eXist to be installed to /eXist. If you wish
to change this then change the EXIST_HOME variable in the appropriate svc-eXist-* file.

By default it expects to run eXist under the user account "exist" and the group "daemon".
If you wish to change this then change the values of the user and group attributes in the
appropriate eXist-*.xmf.xml file.


Installing eXist into SMF
=========================
1) Create the user "exist" in the group "daemon"

2) chown -R exist:daemon /eXist

3) Choose either the eXist Jetty or eXist Standalone configuration, only one may be used.

Adjust the following for your chosen configuration (Standalone configuration is shown here) -

4) Become Super User - su root

5) Copy svc-eXist-standalone to /lib/svc/method (if you are using
Zones then this should be done in the Global Zone)

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
