xquery version "1.0";

declare namespace xdb = "http://exist-db.org/xquery/xmldb";

declare option exist:serialize "method=xhtml media-type=text/html indent=yes omit-xml-declaration=no";

let $a := ""
return
<html>
    <head>
    	<title>Authenticated</title>
	</head>
    <body>
    	<div>ID: {xdb:get-current-user()}</div>
    	<div/>
    	<div>Fullname: {xdb:get-current-user-attribute('http://axschema.org/namePerson')}</div>
    	<div>Firstname: {xdb:get-current-user-attribute('http://axschema.org/namePerson/first')}</div>
    	<div>Lastname: {xdb:get-current-user-attribute('http://axschema.org/namePerson/last')}</div>
    	<div/>
    	<div>ID: {xdb:get-current-user-attribute('id')}</div>
    	<div>Email: {xdb:get-current-user-attribute('http://axschema.org/contact/email')}</div>
    	<div>Country: {xdb:get-current-user-attribute('http://axschema.org/contact/country/home')}</div>
    	<div>Language: {xdb:get-current-user-attribute('http://axschema.org/pref/language')}</div>
    	<div>
    		<form action="" method="get" name="logoutform">
            	<button type="submit" name="action" value="logout">logout</button>
            </form>
        </div>
    </body>
</html>
