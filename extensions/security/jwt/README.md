# JSON Web Token (JWT) Authentication Realm

The JWT Realm is enabled by default in the build configuration file `extensions/security/pom.xml` and include `<module>jwt</module>` in the `<modules/>` element.
To enable JWT authentication you need to make sure that the file /db/system/security/config.xml content something similar to that below

```xml
<security-manager xmlns="http://exist-db.org/Configuration" ...
	...
    <realm id="JWT">
        <context>
            <domain>...</domain>
            <secret>...</secret>
            <account>
                <identifier>$.sub</identifier>
                <email>$.email</email>
                <language></language>
                <description></description>
                <country></country>
                <name>$.name</name>
                <firstname>$.['given_name']</firstname>
                <friendly>$.nickname</friendly>
                <lastname>$.['family_name']</lastname>
                <timezone></timezone>
                <whitelist>
                    <principal>..</principal>
                    <principal>..</principal>
                </whitelist>
                <blacklist>
                    <principal>..</principal>
                    <principal>..</principal>
                </blacklist>
            </account>
            <group>
                <identifier>$.['https://example.com/auth'].groups[*]</identifier>
                <dba>
                    <principal>..</principal>
                    <principal>..</principal>
                </dba>
                <whitelist>
                    <principal>..</principal>
                    <principal>..</principal>
                </whitelist>
                <blacklist>
                    <principal>..</principal>
                    <principal>..</principal>
                </blacklist>
            </group>
        </context>
    </realm>
	...
</security-manager>
```

Here are the metadata-search-attribute keys:

* http://axschema.org/contact/email
* http://axschema.org/pref/language
* http://exist-db.org/security/description
* http://axschema.org/contact/country/home
* http://axschema.org/namePerson
* http://axschema.org/namePerson/first
* http://axschema.org/namePerson/friendly
* http://axschema.org/namePerson/last
* http://axschema.org/pref/timezone


For the following JWT

```json
{
  "https://example.com/auth": {
    "groups": [
      "IT",
      "member"
    ],
    "roles": ["Editor"]
  },
  "nickname": "loren.cahlander",
  "name": "Loren Cahlander",
  "updated_at": "2022-05-04T18:14:26.482Z",
  "email": "loren.cahlander@example.com",
  "email_verified": true,
  "iss": "https://dev-1nrabvoy.us.auth0.com/",
  "sub": "auth0|626029dab5995b0068540953",
  "aud": "sRX5zX0ylWgHZ5OtRr137x5RXyCCO19L",
  "iat": 1651770465,
  "exp": 1651806465,
  "nonce": "VZP0NI1Lmw8YCt-radQdzdKsxQt5KtuloABUC5Qan7g"
}
```

Here is the realm description

```xml
<realm id="JWT" version="1.0" principals-are-case-insensitive="true">
    <context>
        <domain>domain.here</domain>
        <search>
            <account>
                <identifier>$.sub</identifier>
                <email>$.email</email>
                <language></language>
                <description></description>
                <country></country>
                <name>$.name</name>
                <firstname>$.['given_name']</firstname>
                <friendly>$.nickname</friendly>
                <lastname>$.['family_name']</lastname>
                <timezone></timezone>
            </account>
            <group>
                <identifier>$.['https://example.com/auth'].groups[*]</identifier>
            </group>
        </search>
        <transformation>
            <add-group>guest</add-group>
        </transformation>
    </context>
</realm>
```

If the `groups` entry contained JSON objects like the folllowing:

```json
{
  "https://example.com/auth": {
    "groups": [
      {"name": "IT"},
      {"name": "member"}
    ],
    "roles": ["Editor"]
  },
  "nickname": "loren.cahlander",
  "name": "Loren Cahlander",
  "updated_at": "2022-05-04T18:14:26.482Z",
  "email": "loren.cahlander@example.com",
  "email_verified": true,
  "iss": "https://dev-1nrabvoy.us.auth0.com/",
  "sub": "auth0|626029dab5995b0068540953",
  "aud": "sRX5zX0ylWgHZ5OtRr137x5RXyCCO19L",
  "iat": 1651770465,
  "exp": 1651806465,
  "nonce": "VZP0NI1Lmw8YCt-radQdzdKsxQt5KtuloABUC5Qan7g"
}
```

Then the realm description would be:

```xml
<realm id="JWT" version="1.0" principals-are-case-insensitive="true">
    <context>
        <domain>domain.here</domain>
        <search>
            <account>
                <identifier>$.sub</identifier>
                <email>$.email</email>
                <language></language>
                <description></description>
                <country></country>
                <name>$.name</name>
                <firstname>$.['given_name']</firstname>
                <friendly>$.nickname</friendly>
                <lastname>$.['family_name']</lastname>
                <timezone></timezone>
            </account>
            <group>
                <identifier>$.['https://example.com/auth'].groups[*]</identifier>
            </group>
        </search>
        <transformation>
            <add-group>guest</add-group>
        </transformation>
    </context>
</realm>
```
## Using the JWT with RestXQ

Here is a sample RestXQ function with the Authorization header
parameter being passed in.

```xquery
(:~
Get the details of the current user.
@param $authorization The authorization token for RBAC
@return
@custom:openapi-tag Security
 :)
declare
    %rest:GET
    %rest:path("/sample/who-am-i")
    %rest:header-param("JWT", "{$authorization}")
    %rest:produces("application/json")
    %output:media-type("application/json")
    %output:method("json")
function whoami:get($authorization as xs:string*)
as map(*)
{
    let $jwt := $authorization || "@test"
    let $login := xmldb:login("/db", $jwt, $jwt)
...
}
```
