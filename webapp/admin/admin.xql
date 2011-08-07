xquery version "3.0";
(: 
    $Id$ 

    Main module of the database administration interface.
:)

declare namespace admin = "http://exist-db.org/xquery/admin-interface";

declare namespace request = "http://exist-db.org/xquery/request";
declare namespace session = "http://exist-db.org/xquery/session";
declare namespace util = "http://exist-db.org/xquery/util";
declare namespace xdb = "http://exist-db.org/xquery/xmldb";

import module namespace status = "http://exist-db.org/xquery/admin-interface/status" at "status.xqm";
import module namespace browse = "http://exist-db.org/xquery/admin-interface/browse" at "browse.xqm";
import module namespace users = "http://exist-db.org/xquery/admin-interface/users" at "users.xqm";
import module namespace xqueries = "http://exist-db.org/xquery/admin-interface/xqueries" at "xqueries.xqm";
import module namespace shut = "http://exist-db.org/xquery/admin-interface/shutdown" at "shutdown.xqm";
import module namespace setup = "http://exist-db.org/xquery/admin-interface/setup" at "setup.xqm";
import module namespace rev="http://exist-db.org/xquery/admin-interface/revisions" at "versions.xqm";
import module namespace backup="http://exist-db.org/xquery/admin-interface/backup" at "backup.xqm";
import module namespace prof="http://exist-db.org/xquery/profiling" at "trace.xqm";
import module namespace grammar="http://exist-db.org/xquery/admin-interface/grammar" at "grammar.xqm";
import module namespace install="http://exist-db.org/xquery/install-tools" at "install.xqm";
import module namespace fundocs="http://exist-db.org/xquery/admin/fundocs" at "fundocs.xqm";
import module namespace repomanager="http://exist-db.org/xquery/admin-interface/repo" at "repo.xqm";
import module namespace indexes="http://exist-db.org/xquery/admin-interface/indexes" at "indexes.xqm";

declare option exist:serialize "method=xhtml media-type=text/html";

(: 
    Display the version, SVN revision and user info in the top right corner 
:)
declare function admin:info-header() as element()
{
    <div class="info">
        <ul>
            <li>Version: { util:system-property( "product-version" ) }</li>
            <li>SVN Revision: { util:system-property( "svn-revision" ) }</li>
            <li>Build: {util:system-property("product-build")}</li>
            <li>User: { xdb:get-current-user() }</li>
        </ul>
    </div>
};

(: 
    Display the menu on left side 
:)
declare function admin:menu-panel() as element()
{
    let $link := session:encode-url(request:get-uri())
    return
    <div>
     <div class="guide-title">Select a Page</div>
     <div>
          <ul>
              <li>Browse
                  <ul>
                      <li><a href="{$link}?panel=browse">Collections</a></li>
                      <li><a href="{$link}?panel=indexes">Indexes</a></li>
                  </ul>
              </li>
              <li>Install
                  <ul>
                      <li><a href="{$link}?panel=fundocs">Documentation</a></li>
                      <li><a href="{$link}?panel=setup">Examples</a></li>
                      <!--li><a href="{$link}?panel=install">Tools</a></li-->
                  </ul>
              </li>
             
              <li>System
                 <ul>
                      <li><a href="{$link}?panel=shutdown">Shutdown</a></li>
                      <li><a href="{$link}?panel=status">Status</a></li>																		
                 </ul>
              </li>
              
              <li>Tooling
                  <ul>
                      <li><a href="{$link}?panel=backup">Backups</a></li>
                      <li><a href="{$link}?panel=repo">Package Repository</a></li>
                      <li><a href="{$link}?panel=trace">Query Profiling</a></li>
                      <li><a href="{$link}?panel=users">User Management</a></li>
                  </ul>
              </li>
              
              <li>View
                  <ul>
                      <li><a href="{$link}?panel=grammar">Grammar cache</a></li>
                      <li><a href="{$link}?panel=xqueries">Running Jobs</a></li>
                  </ul>
              </li>
          </ul>
          <ul>
              <li><a href="..">Home</a></li>
              <li><a href="{$link}?logout=yes">Logout</a></li>
          </ul>
     </div>
    </div>
};

(:
    Select the page to show. Every page is defined in its own module 
:)
declare function admin:panel() as element()
{
    let $panel := request:get-parameter("panel", "status")[1] return
        switch ($panel)
            case "browse"    return  browse:main()
            case "users"     return  users:main()
            case "xqueries"  return  xqueries:main()
            case "shutdown"  return  shut:main()
            case "setup"     return  setup:main()
            case "fundocs"   return  fundocs:main()
    		case "repo"      return  repomanager:main()
    		case "revisions" return  rev:main()
    		case "backup"    return  backup:main()
    	    case "trace"     return  prof:main()
            case "grammar"   return  grammar:main()    
            case "install"   return  install:main()
            case "indexes"   return  indexes:main()
            default          return  status:main()
};

declare function admin:panel-header() {
    let $panel := request:get-parameter("panel", "status")[1]
    return
        if ($panel[1] eq "install") then
            install:header()
        else()
};

(:~  
    Display the login form.
:)
declare function admin:display-login-form() as element()
{
    <div class="panel">
        <div class="panel-head">Login</div>
        <p>This is a protected resource. Only registered database users can log
        in. If you have not set up any users, login as "admin" and leave the
        password field empty. Note that the "guest" user is not permitted access.</p>

        <form action="{session:encode-url(request:get-uri())}" method="post">
            <table class="login" cellpadding="5">
                <tr>
                    <th colspan="2" align="left">Please Login</th>
                </tr>
                <tr>
                    <td align="left">Username:</td>
                    <td><input name="user" type="text" size="20"/></td>
                </tr>
                <tr>
                    <td align="left">Password:</td>
                    <td><input name="pass" type="password" size="20"/></td>
                </tr>
                <tr>
                    <td colspan="2" align="left"><input type="submit" value="Login"/></td>
                </tr>
            </table>
            {
                for $param in request:get-parameter-names()
                return
                    if ( $param = ("user","pass") ) then
                        ()
                    else 
                        <input type="hidden" name="{$param}" value="{request:get-parameter($param, ())}"/>
                        
            }
        </form>
    </div>
};

(: main entry point :)
let $userParam := request:get-parameter("user", ())
let $passwdParam := request:get-parameter("pass", ())
let $isLoggedIn :=  if(xdb:get-current-user() eq "guest") then
    (
        (: is this a login attempt? :)
        if($userParam and not(empty($passwdParam)))then
        (
            if($userParam = ("", "guest") )then
            (
                (: prevent the guest user from accessing the admin webapp :)
                false()
            )
            else
            (
                (: try and log the user in :)
                xdb:login( "/db", $userParam, $passwdParam, true() )
            )
        )
        else
        (
            (: prevent the guest user from accessing the admin webapp :)
            false()
        )
    )
    else
    (
        (: if we are already logged in, are we logging out - i.e. set permissions back to guest :)
        if(request:get-parameter("logout",()))then
        (
        	let $null  := xdb:login("/db", "guest", "guest") 
            let $inval := session:invalidate()
            
            return false()
        )
        else
        (
             (: we are already logged in and we are not the guest user :)
            true()
        )
    )
return (
    <?css-conversion no?>,
    <html>
        <head>
            <title>eXist Database Administration</title>
            <link type="text/css" href="admin.css" rel="stylesheet"/>
			<link type="text/css" href="styles/prettify.css" rel="stylesheet"/>
			<link type="text/css" href="libs/scripts/jquery/css/smoothness/jquery.ui.all.css" rel="stylesheet"/>
            <link rel="shortcut icon" href="../resources/exist_icon_16x16.ico"/>
			<link rel="icon" href="../resources/exist_icon_16x16.png" type="image/png"/>
			<script type="text/javascript" src="scripts/prettify.js"/>
			<script type="text/javascript" src="libs/scripts/jquery/jquery-1.4.2.min.js"></script>
			<script type="text/javascript" src="scripts/admin.js"></script>
			{ admin:panel-header() }
        </head>
        <body class="yui-skin-sam">
            <div class="header">
                { admin:info-header() }
                <img src="logo.jpg"/>
            </div>
            
            <div class="content">
                <div class="guide">
                    
                    {
                        if($isLoggedIn) then
                            admin:menu-panel()
                        else
                        ()
                    }
                    <div class="userinfo">
                        Logged in as: {xdb:get-current-user()}
                    </div>
                </div>
                {
                    if($isLoggedIn)then
                    (
                        admin:panel()
                    )
                    else
                    (
                        admin:display-login-form()
                    )
                }
            </div>
        </body>
    </html>
)
