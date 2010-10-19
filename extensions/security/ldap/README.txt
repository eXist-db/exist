To enable LDAP authentication you need to replace the file /db/system/security/config.xml with something similar to that below and then restart eXist-db -

<security-manager xmlns="http://exist-db.org/Configuration" last-account-id="3" last-group-id="3" version="2.0">
    <realm id="LDAP">
        <context>
            <url>ldap://directory.mydomain.com:389</url>
            <search>
                <base>ou=department,dc=directory,dc=mydomain,dc=com</base>
                <default-username>some-ldap-user</default-username>
                <default-password>some-ldap-password</default-password>
                <account-search-filter>(&amp;(objectClass=user)(sAMAccountName=${account-name}))</account-search-filter>
                <group-search-filter>(&amp;(objectClass=group)(sAMAccountName=${group-name}))</group-search-filter>
            </search>
        </context>
    </realm>
</security-manager>

url - the URL to your LDAP directory server.
base - the LDAP base to use when resolving users and groups