To enable LDAP authentication you need to make sure that the file /db/system/security/config.xml content something similar to that below 

<security-manager xmlns="http://exist-db.org/Configuration" ...
	...
    <realm id="LDAP">
        <context>
            <url>ldap://directory.mydomain.com:389</url>
            <domain>...</domain>
            <principalPattern>...</principalPattern>            
            <search>
                <base>ou=department,dc=directory,dc=mydomain,dc=com</base>
                <default-username>some-ldap-user</default-username>
                <default-password>some-ldap-password</default-password>
				<account>
                    <search-filter-prefix>(&amp;(objectClass=user)(sAMAccountName=${account-name}))</search-filter-prefix>
                    <search-attribute>...</search-attribute>
                    <metadata-search-attribute>..</metadata-search-attribute>
                    <whitelist><principal>..</principal><principal>..</principal></whitelist>
                    <blacklist><principal>..</principal><principal>..</principal></blacklist>
                </account
                <group>
                    <search-filter-prefix>(&amp;(objectClass=group)(sAMAccountName=${group-name}))</group-search-filter>
                    <search-attribute>...</search-attribute>
                    <metadata-search-attribute>..</metadata-search-attribute>
                    <whitelist><principal>..</principal><principal>..</principal></whitelist>
                    <blacklist><principal>..</principal><principal>..</principal></blacklist>
                </group>
            </search>
            <transformation><add-group>...</add-group></transformation>
        </context>
    </realm>
	...
</security-manager>

url - the URL to your LDAP directory server.
base - the LDAP base to use when resolving users and groups