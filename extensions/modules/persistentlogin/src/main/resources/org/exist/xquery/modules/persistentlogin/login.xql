xquery version "3.0";

module namespace login="http://exist-db.org/xquery/login";

import module namespace plogin="http://exist-db.org/xquery/persistentlogin"
    at "java:org.exist.xquery.modules.persistentlogin.PersistentLoginModule";

import module namespace request = "http://exist-db.org/xquery/request";
import module namespace response = "http://exist-db.org/xquery/response";
import module namespace session = "http://exist-db.org/xquery/session";

(:~
    Main entry point into the login module. Checks request parameters to determine the action
    to take. If a parameter "user" is given, try to authenticate this user with the password
    specified in parameter "password". If there's a parameter "logout", clear the current
    user credentials. Without a parameter: check if a user is registered with the persistent
    login module.

    The persistent login module implements a one-time login token approach as described in
    <a href="http://jaspan.com/improved_persistent_login_cookie_best_practice">Improved Persistent Login Cookie
    Best Practice</a> and implemented in <a href="https://github.com/SpringSource/spring-security">Spring Security</a>.
    This approach make it more difficult to attackers to steal a cookie, though users should be aware
    that persistent logins will never be completely secure.

    A cookie is generated with a unique token upon successfull login, which can be used for the next login without
    requiring credentials. The token is only valid for a single login though and is deleted afterwards.
    This means a new cookie is set by each request. For the next request, the browser has to send the
    cookie returned by the previous request - if not, we assume the cookie has been stolen and the session
    is invalidated. Request thus have to be send in sequence, which requires particular attention
    when using AJAX.

    An expiration date can be set when the user logs in through request parameter "duration". The
    specified value has to be a valid instance of xs:dayTimeDuration. If no parameter is present,
    the method will use the value of $maxAge.

    If no expiration date was set ($maxAge is empty), the function will fall back to the
    default session-based logins, which will time out depending on webserver settings.

    After evaluation of the function, the logged in user name will be available in request
    attribute $domain.user. If the user could not be logged in, this attribute will be empty.
    You can use this to check if the function was successful or not.

    @param $domain arbitrary string to be used for the name of the cookie
    @param $path the path for which the cookie will be valid (e.g. /exist by default)
    @param $maxAge default max duration for the session. User will need to re-login afterwards. Can be overwritten
    by request parameter "duration".
    @param $asDba  if true, require the user to be a member of the dba administrators group
:)
declare function login:set-user($domain as xs:string, $path as xs:string?, $maxAge as xs:dayTimeDuration?, $asDba as xs:boolean) as empty-sequence() {
    let $user := request:get-parameter("user", ())
    let $password := request:get-parameter("password", ())
    let $logout := request:get-parameter("logout", ())
    let $durationParam := request:get-parameter("duration", ())
    let $duration :=
        if ($durationParam) then
            xs:dayTimeDuration($durationParam)
        else if (exists($maxAge)) then
            $maxAge
        else
            ()
    let $cookie := request:get-cookie-value($domain)
    return
        if ($logout) then
            login:clear-credentials($cookie, $domain, $path)
        else if ($user) then
            login:create-login-session($domain, $path, $user, $password, $duration, $asDba)
        else if (exists($cookie) and $cookie != "deleted") then
            login:get-credentials($domain, $path, $cookie, $asDba)
        else
            login:get-credentials-from-session($domain)
};

(:~
    Same as login:set-user#4 but $path set to the default (use context path).
:)
declare function login:set-user($domain as xs:string, $maxAge as xs:dayTimeDuration?, $asDba as xs:boolean) as empty-sequence() {
    login:set-user($domain, (), $maxAge, $asDba)
};

declare %private function login:callback($newToken as xs:string?, $user as xs:string, $password as xs:string,
    $expiration as xs:duration, $domain as xs:string, $path as xs:string?, $asDba as xs:boolean) {
    if (not($asDba) or sm:is-dba($user)) then (
        request:set-attribute($domain || ".user", $user),
        request:set-attribute("xquery.user", $user),
        request:set-attribute("xquery.password", $password),
        if ($newToken) then
            response:set-cookie($domain, $newToken, $expiration, false(), (),
                if (exists($path)) then $path else request:get-context-path())
        else
            ()
    ) else
        ()
};

declare %private function login:get-credentials($domain as xs:string, $path as xs:string?, $token as xs:string, $asDba as xs:boolean) as empty-sequence() {
    plogin:login($token, login:callback(?, ?, ?, ?, $domain, $path, $asDba))
};

declare %private function login:create-login-session($domain as xs:string, $path as xs:string?, $user as xs:string, $password as xs:string,
    $maxAge as xs:dayTimeDuration?, $asDba as xs:boolean) as empty-sequence() {
    if (exists($maxAge)) then (
        plogin:register($user, $password, $maxAge, login:callback(?, ?, ?, ?, $domain, $path, $asDba)),
        session:invalidate()
    ) else
        login:fallback-to-session($domain, $user, $password, $asDba)
};

declare %private function login:clear-credentials($token as xs:string?, $domain as xs:string, $path as xs:string?) as empty-sequence() {
    response:set-cookie($domain, "deleted", xs:dayTimeDuration("-P1D"), false(), (),
        if (exists($path)) then $path else request:get-context-path()),
    if ($token and $token != "deleted") then
        plogin:invalidate($token)
    else
        (),
    session:invalidate()
};

(:~
 : If "remember me" is not enabled (no duration passed), fall back to the usual
 : session-based login mechanism.
 :)
declare %private function login:fallback-to-session($domain as xs:string, $user as xs:string, $password as xs:string?, $asDba as xs:boolean) {
    let $isLoggedIn := xmldb:login("/db", $user, $password, true())
    return
        if ($isLoggedIn and (not($asDba) or sm:is-dba($user))) then (
            session:set-attribute($domain || ".user", $user),
            request:set-attribute($domain || ".user", $user),
            request:set-attribute("xquery.user", $user),
            request:set-attribute("xquery.password", $password)
        ) else
            ()
};

declare %private function login:get-credentials-from-session($domain as xs:string) {
    let $userFromSession := session:get-attribute($domain || ".user")
    return (
        request:set-attribute($domain || ".user", $userFromSession)
    )
};
