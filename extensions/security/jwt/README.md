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
                <property>...</property>
                <metadata-property>..</metadata-property>
                <whitelist><principal>..</principal><principal>..</principal></whitelist>
                <blacklist><principal>..</principal><principal>..</principal></blacklist>
            </account>
            <group>
                <claim>...</claim>
                <property>...</property>
                <metadata-property>..</metadata-property>
                <dba><principal>..</principal><principal>..</principal></dba>
                <whitelist><principal>..</principal><principal>..</principal></whitelist>
                <blacklist><principal>..</principal><principal>..</principal></blacklist>
            </group>
        </context>
    </realm>
	...
</security-manager>
```

For the following JWT

```json
{
    "sub": "00uixa271s6x7qt8I0h7",
    "ver": 1,
    "iss": "https://${yourOktaDomain}",
    "aud": "0oaoiuhhch8VRtBnC0h7",
    "iat": 1574201516,
    "exp": 1574205116,
    "jti": "ID.ewMNfSvcpuqyS93OgVeCN3F2LseqROkyYjz7DNb9yhs",
    "amr": [
        "pwd",
        "mfa",
        "kba"
    ],
    "idp": "00oixa26ycdNcX0VT0h7",
    "nonce": "UBGW",
    "auth_time": 1574201433,
    "groups": [
        "Everyone",
        "IT"
    ]
}
```

Here is the realm description


```xml
<realm id="JWT" version="1.0" principals-are-case-insensitive="true">
    <context>
        <domain>domain.here</domain>
        <secret>...</secret>
        <account>
            <property key="name">idp</property>
        </account>
        <group>
            <claim>groups</claim>
        </group>
    </context>
</realm>
```

If the `groups` entry contained JSON objects like the folllowing:

```json
{
    "sub": "00uixa271s6x7qt8I0h7",
    "ver": 1,
    "iss": "https://${yourOktaDomain}",
    "aud": "0oaoiuhhch8VRtBnC0h7",
    "iat": 1574201516,
    "exp": 1574205116,
    "jti": "ID.ewMNfSvcpuqyS93OgVeCN3F2LseqROkyYjz7DNb9yhs",
    "amr": [
        "pwd",
        "mfa",
        "kba"
    ],
    "idp": "00oixa26ycdNcX0VT0h7",
    "nonce": "UBGW",
    "auth_time": 1574201433,
    "groups": [
        {
          "name" : "Everyone"
        },
        {
          "name" : "IT"
        }
    ]
}
```

Then the realm description would be:


```xml
<realm id="JWT" version="1.0" principals-are-case-insensitive="true">
    <context>
        <domain>domain.here</domain>
        <secret>...</secret>
        <account>
            <property key="name">idp</property>
        </account>
        <group>
            <claim>groups</claim>
            <property key="name">name</property>
        </group>
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
    %rest:header-param("Authorization", "{$authorization}")
    %rest:produces("application/json")
    %output:media-type("application/json")
    %output:method("json")
function whoami:get($authorization as xs:string*)
as map(*)
{
    let $login := login:authenticate($authorization[1])
...
}
```

Here is the sample login function.

```xquery
module namespace login = "http://example.com/modules/ns/login";

import module namespace jwt = "http://exist-db.org/security/jwt/xquery"
    at "java:org.exist.security.realm.jwt.xquery.JWTModule";

declare function login:authenticate($authorization as xs:string?)
as empty-sequence()
{
    if ($authorization)
    then
        if (fn:starts-with($authorization, "Bearer"))
        then jwt:authorize($authorization)
        else ()
    else ()

};
```
