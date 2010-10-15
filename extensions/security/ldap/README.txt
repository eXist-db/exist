To enable LDAP authentication you need to replace the file /db/system/security/config.xml with something similar to that below and then restart eXist-db -

<security-manager xmlns="http://exist-db.org/Configuration" last-account-id="3" last-group-id="3" version="2.0">
    <realm id="LDAP">
        <context>
            <url>ldap://directory.mydomain.com:389</url>
            <base>ou=department,dc=directory,dc=mydomain,dc=com</base>
        </context>
    </realm>
</security-manager>

url - the URL to your LDAP directory server.
base - the LDAP base to use when resolving users and groups