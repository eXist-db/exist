# eXist-db LDAP Security Module

To enable LDAP authentication, add a `<realm id="LDAP">` entry to the security manager's
configuration document at `/db/system/security/config.xml`. The realm implementation class is
resolved from the `id` attribute (`LDAP` -> `org.exist.security.realm.ldap.LDAPRealm`,
`ActiveDirectory` -> `org.exist.security.realm.activedirectory.ActiveDirectoryRealm`), so no
other registration is needed.

```xml
<security-manager xmlns="http://exist-db.org/Configuration" version="2.0">
    <authentication-entry-point>/authentication/login</authentication-entry-point>
    <realm id="exist" version="1.0">
        <!-- the built-in realm; always required -->
    </realm>
    <realm id="LDAP" version="1.0" principals-are-case-insensitive="true">
        <context>
            <authentication>simple</authentication>
            <use-ssl>false</use-ssl>
            <url>ldap://directory.mydomain.com:389</url>
            <domain>mydomain.com</domain>
            <search>
                <base>ou=department,dc=directory,dc=mydomain,dc=com</base>
                <default-username>some-ldap-user</default-username>
                <default-password>some-ldap-password</default-password>
                <account>
                    <search-filter-prefix>objectClass=user</search-filter-prefix>
                    <search-attribute key="name">sAMAccountName</search-attribute>
                    <search-attribute key="dn">distinguishedName</search-attribute>
                    <search-attribute key="objectSid">objectSid</search-attribute>
                    <search-attribute key="primaryGroupID">primaryGroupID</search-attribute>
                    <search-attribute key="memberOf">memberOf</search-attribute>
                    <metadata-search-attribute key="http://axschema.org/contact/email">mail</metadata-search-attribute>
                    <whitelist><principal>admin</principal></whitelist>
                    <blacklist><principal>guest</principal></blacklist>
                </account>
                <group>
                    <search-filter-prefix>objectClass=group</search-filter-prefix>
                    <search-attribute key="name">sAMAccountName</search-attribute>
                    <search-attribute key="dn">distinguishedName</search-attribute>
                    <search-attribute key="objectSid">objectSid</search-attribute>
                    <search-attribute key="member">member</search-attribute>
                </group>
            </search>
            <transformation><add-group>dba</add-group></transformation>
        </context>
    </realm>
</security-manager>
```

## `<context>` elements

| Element | Required | Meaning |
|---|---|---|
| `authentication` | no (default `simple`) | JNDI authentication mode, e.g. `simple`. |
| `use-ssl` | no (default `false`) | Use `ldaps://`. |
| `url` | yes | URL of the directory server. |
| `domain` | no | Appended to/stripped from usernames as a `user@domain` suffix (see `addDomainPostfix`/`removeDomainPostfix` in `LDAPRealm`). |
| `search` | yes | The search configuration, below. |
| `transformation` / `add-group` | no | Extra eXist group(s) (e.g. `dba`) to grant every account from this realm, in addition to its LDAP groups. |

## `<search>` elements

| Element | Required | Meaning |
|---|---|---|
| `base` | yes | The LDAP search base, e.g. `dc=example,dc=org`. |
| `default-username` / `default-password` | yes | Credentials used for realm-initiated searches (listing accounts/groups, resolving group membership) when no user is bound. |
| `account` | yes | See below. |
| `group` | yes | See below. |

## `<account>` / `<group>` elements

Both share the same shape:

| Element | Required | Meaning |
|---|---|---|
| `search-filter-prefix` | yes | The filter's fixed clause, **without** the enclosing `(...)` or the leading `&` - eXist wraps it as `(&(<search-filter-prefix>)(<the actual search attribute>=<value>))`. For example `objectClass=user`, not `(&(objectClass=user)(sAMAccountName=...))`. |
| `search-attribute` | at least `name`; others as needed | One element per mapping, `key` is one of `name`, `dn`, `objectSid`, `primaryGroupID`, `memberOf` (account) / `member` (group) - see below - and the element's text content is the actual LDAP attribute name in your directory. |
| `metadata-search-attribute` | no | Same shape as `search-attribute`, but `key` is an [axschema.org](http://axschema.org) URI (e.g. `http://axschema.org/contact/email`) copied onto the eXist account as metadata. Account only. |
| `whitelist` / `blacklist` | no | Each holds `<principal>name</principal>` entries. If a whitelist is present, only listed principals are visible; blacklisted principals are always excluded. Matched against the name with any `domain` suffix stripped. |

### `search-attribute` keys

| Key | Used for |
|---|---|
| `name` | The principal's own name (`uid`, `sAMAccountName`, `cn`, whatever your directory uses). |
| `dn` | The entry's own distinguished name, returned as a **regular attribute value**, not the LDAP operational attribute. Most directories don't return an entry's DN as a plain attribute by default; either configure your directory to expose one (e.g. LDAP's operational `entryDN`, if your server returns it for an unauthenticated attribute request) or store a copy of the DN in an unused attribute at entry-creation time. Required for resolving group membership (`memberOf`/`member` below). |
| `objectSid` | Account: the account's own SID. Group: the group's own SID. Both are plain strings from eXist's point of view - they don't have to be real Active Directory SIDs, just unique per account/group. |
| `primaryGroupID` | Account only. Combined with the account's own `objectSid` to compute its primary group's SID: take `objectSid`, drop everything after (and including) the last `-`, then append `primaryGroupID`. For example, an account with `objectSid = S-1-5-21-111-222-333` and `primaryGroupID = 500` resolves to primary-group SID `S-1-5-21-111-222-500` - **not** `S-1-5-21-111-222-333-500`. The primary group's own `objectSid` search-attribute must equal that computed value exactly. This matches real Active Directory's SID scheme (the account's SID is `<domain>-<accountRID>`; swap the RID for the primary group's RID to get the group's SID) but works the same way against any directory, real AD or not, as long as you set these two attributes up consistently. |
| `memberOf` (account) / `member` (group) | Group membership, in whichever direction your directory tracks it: `memberOf` is an attribute on the *account* holding its groups' DNs (e.g. via an OpenLDAP `memberof` overlay); `member` is an attribute on the *group* holding its members' DNs (`groupOfNames`-style). Both use the `dn` search-attribute above to match. |

## Testing locally

A minimal directory to test against (works with any LDAP server; the example below uses
[`osixia/openldap`](https://hub.docker.com/r/osixia/openldap)):

```sh
docker run -d --name exist-test-ldap -p 3389:389 \
  -e LDAP_DOMAIN=example.org -e LDAP_ADMIN_PASSWORD=admin osixia/openldap:1.5.0
```

Seed it (`ldapadd -x -D "cn=admin,dc=example,dc=org" -w admin -f seed.ldif`):

```ldif
dn: ou=people,dc=example,dc=org
objectClass: organizationalUnit
ou: people

dn: ou=groups,dc=example,dc=org
objectClass: organizationalUnit
ou: groups

dn: uid=foo,ou=people,dc=example,dc=org
objectClass: inetOrgPerson
cn: foo
sn: foo
uid: foo@example.org
description: uid=foo,ou=people,dc=example,dc=org
employeeNumber: S-1-5-21-111-222-333
employeeType: 500

dn: cn=testgroup,ou=groups,dc=example,dc=org
objectClass: groupOfNames
objectClass: extensibleObject
cn: testgroup
member: uid=foo,ou=people,dc=example,dc=org
employeeNumber: S-1-5-21-111-222-500
```

This repurposes standard `inetOrgPerson`/`groupOfNames` attributes as stand-ins for the
AD-specific ones (`description` for `dn`, `employeeNumber` for `objectSid`, `employeeType` for
`primaryGroupID`) so no custom schema is needed. See
`extensions/security/ldap/src/test/java/org/exist/security/realm/ldap/LDAPAccountCachingIT.java`
for the matching `<realm>` configuration and a runnable reproduction/regression test built on
exactly this setup.
