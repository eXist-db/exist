xquery version "3.0";

module namespace login="http://exist-db.org/xquery/app/wiki/session";

import module namespace cache="http://exist-db.org/xquery/cache" at "java:org.exist.xquery.modules.cache.CacheModule";

(:~
    Main entry point into the login module. Checks request parameters to determine the action
    to take. If a parameter "user" is given, try to authenticate this user with the password
    specified in parameter "password". If there's a parameter "logout", clear the current
    user credentials. Without a parameter: check if a user is registered with the module.
    
    The function returns an XML fragment to be included into the dispatch XML or
    the empty set if the user could not be authenticated or the
    session is empty.
    
    When the user logs in, a unique hash is generated. This is stored in the eXist-internal cache. 
    The hash is packed into a cookie and sent back to the client with the normal response.
    The next time the user accesses the page, the hash cookie will be sent by the browser and
    the key is looked up in the cache. The session is then set to the corresponding user.
    
    An expiration date can be set when the user logs in through request parameter "duration". The
    specified value has to be a valid instance of xs:dayTimeDuration. If no parameter is present,
    the method will use the value of $maxAge. If $maxAge is empty as well, the login will only
    be valid for the current browser session.
    
    @param $domain arbitrary string to be used for the name of the cookie
    @param $maxAge max duration for which the user credentials remain valid
:)
declare function login:set-user($domain as xs:string, $maxAge as xs:dayTimeDuration?) as element()* {
    session:create(),
    let $user := request:get-parameter("user", ())
    let $password := request:get-parameter("password", ())
    let $logout := request:get-parameter("logout", ())
    let $durationParam := request:get-parameter("duration", ())
    let $duration :=
        if ($durationParam) then
            xs:dayTimeDuration($durationParam)
        else
            $maxAge
    let $cookie := request:get-cookie-value($domain)
    return
        if ($logout eq "logout") then
            login:clear-credentials($cookie)
        else if ($user) then
            login:create-login-session($domain, $user, $password, $duration)
        else if (exists($cookie)) then
            login:get-credentials($domain, $cookie)
        else
            ()
};

declare %private function login:store-credentials($user as xs:string, $password as xs:string,
    $maxAge as xs:dayTimeDuration?) as xs:string {
    let $token := util:uuid($password)
    let $expires := if (empty($maxAge)) then () else util:system-dateTime() + $maxAge
    let $newEntry := map {
        "token" := $token, 
        "user" := $user, 
        "password" := $password, 
        "expires" := $expires
    }
    return (
        $token,
        cache:put("xquery.login.users", $token, $newEntry)
    )[1]
};

declare %private function login:is-valid($entry as map(*)) {
    empty($entry("expires")) or util:system-dateTime() < $entry("expires")
};

declare %private function login:with-login($user as xs:string, $password as xs:string, $func as function() as item()*) {
    let $loggedIn := xmldb:login("/db", $user, $password)
    return
        if ($loggedIn) then
            $func()
        else
            ()
};

declare %private function login:get-credentials($domain as xs:string, $token as xs:string) as element()* {
    let $entry := cache:get("xquery.login.users", $token)
    return
        if (exists($entry) and login:is-valid($entry)) then
            login:with-login($entry("user"), $entry("password"), function() {
                <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="xquery.user" value="{$entry('user')}"/>,
                <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="xquery.password" value="{$entry('password')}"/>,
                <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="{$domain}.user" value="{$entry('user')}"/>
            })
        else
            util:log("INFO", ("No login entry found for user hash: ", $token))
};

declare %private function login:create-login-session($domain as xs:string, $user as xs:string, $password as xs:string,
    $maxAge as xs:dayTimeDuration?) {
    login:with-login($user, $password, function() {
        let $token := login:store-credentials($user, $password, $maxAge)
        return (
            <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="{$domain}.user" value="{$user}"/>,
            <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="xquery.user" value="{$user}"/>,
            <set-attribute xmlns="http://exist.sourceforge.net/NS/exist" name="xquery.password" value="{$password}"/>,
            if (empty($maxAge)) then
                response:set-cookie($domain, $token)
            else
                response:set-cookie($domain, $token, $maxAge, false())
        )
    })
};

declare %private function login:clear-credentials($token as xs:string) {
    let $removed := cache:remove("xquery.login.users", $token)
    return
        ()
};