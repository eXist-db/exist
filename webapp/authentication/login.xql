xquery version "1.0";
(: $Id: login.xql 13358 2010-12-07 03:45:03Z shabanovd $ :)
(:
    eXist & OpenId login forms.
:)

declare option exist:serialize "method=xhtml media-type=text/html indent=yes omit-xml-declaration=no";

<html>
	<head>
		<link rel="stylesheet" type="text/css" href="css/eXist.css" />
		
		<link rel="stylesheet" type="text/css" href="../scripts/yui/grids/grids-min.css" />
		
		<!-- Simple OpenID Selector -->
		<link rel="stylesheet" href="../scripts/openid-selector/css/openid.css" />
		<script type="text/javascript" src="../scripts/jquery/jquery-1.6.2.min.js"></script>
		<script type="text/javascript" src="../scripts/openid-selector/js/openid-jquery.js"></script>
		<script type="text/javascript">
			$(document).ready(function() {{
				openid.init('openid_identifier','../scripts/openid-selector/images/');
			}});
		</script>
		<!-- /Simple OpenID Selector -->
	</head>
	<body>
		<div class="yui-g" style="margin: 1em auto 2em; width: 60em">
			<!-- native login form -->
			<div class="yui-u first">
				<h3 class="titlebar">Log in with a eXist-db account</h3>
				<form action="j_security_check" method="post" id="login_native">
				
					<!-- return url -->
					<input type="hidden" name="eXist_return_to" value="{session:encode-url(request:get-uri())}" />
				
					<p>Enter your account info:</p>
				
					<fieldset style="padding: 0">
						<legend></legend>
							<!-- user name block -->
							<div style="padding: 0.2em 0pt 0.2em 4em; margin-top: 0.5em;">
								<div class="login_left">Username:</div>
								<div>
									<input type="text" name="j_username" maxlength="15" value="" style="padding: .2em; border: 1px solid #789"/>
								</div>
							</div>
							<!-- password block -->
							<div style="padding: 0.2em 0pt 0.2em 4em; margin-top: 0.5em;">
								<div class="login_left">Password:</div>
								<div>
									<input type="password" name="j_password" maxlength="32" style="padding: .2em; border: 1px solid #789"/>
								</div>
							</div>
							<!-- login button block -->
							<div style="padding: 15px 0.5em 0.5em 5em;">
								<div class="login_left">
									<input type="submit" name="login" value="Sign-In"/>
								</div>
							</div>
					</fieldset>
				</form>
			</div>
			
			<!-- OpenID login form -->
			<div class="yui-u">
				
				<h3 class="titlebar">Log in with a OpenID account</h3>
				<!-- Simple OpenID Selector -->
				<form action="{request:get-context-path()}/openid" method="get" id="openid_form">
					<input type="hidden" name="action" value="verify" />
					
					<!-- return url -->
					<input type="hidden" name="return_to" value="{session:encode-url(request:get-uri())}" />
					
					<fieldset>
						<legend></legend>
						
						<div id="openid_choice">
							<p>Please click your account provider:</p>
							<div id="openid_btns"></div>
						</div>
					
						<div id="openid_input_area">
							<input id="openid_identifier" name="openid_identifier" type="text" value="http://" />
							<input id="openid_submit" type="submit" value="Sign-In"/>
						</div>
						<noscript>
							<p>OpenID is service that allows you to log-on to many different websites using a single indentity.
							Find out <a href="http://openid.net/what/">more about OpenID</a> and <a href="http://openid.net/get/">how to get an OpenID enabled account</a>.</p>
						</noscript>
					</fieldset>
				</form>
				<!-- /Simple OpenID Selector -->
			</div>
			
			<!-- OAuth login form -->
			<div class="yui-u first">
				
				<h3 class="titlebar">Log in with a OAuth account</h3>

				<fieldset>
					<legend></legend>
					
					<a href="/exist/oauth/cook?auth={session:encode-url(request:get-uri())}">facebook</a>
				</fieldset>
			</div>
		</div>
		<p class="copyright">Copyright &#169; 2011 <a title="eXist-db Open Source Native XML Database." href="http://exist-db.org">eXist-DB</a> All rights reserved.</p>
	</body>
</html>
