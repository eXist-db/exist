(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "1.0";


(: Demonstrates sending emails through Javamail from eXist :)


declare namespace mail="http://exist-db.org/xquery/mail";

declare variable $message
{
        <mail>
                <from>Person 2 &lt;john@doe.org&gt;</from>
                <to>3jane@ashpool.not</to>
                <cc>john@doe.org</cc>
                <bcc>matrix@does.not.eXist</bcc>
                <subject>Testing send-email()</subject>
                <message>
                        <text>Test message, Testing 3, 2, 1</text>
                </message>
        </mail>
	,
        <mail>
                <from>Person 3 &lt;john@doe.org&gt;</from>
                <to>3jane@ashpool.not</to>
                <cc>john@doe.org</cc>
                <bcc>matrix@does.not.eXist</bcc>
                <subject>Testing send-email()</subject>
                <message>
                        <text>Test message, Testing 2, 1, 3</text>
                        <xhtml>
                                <html>
                                        <head>
                                                <title>Testing</title>
                                        </head>
                                        <body>
                                                <h1>Testing</h1>
                                                <p>Test Message 2, 1, 3</p>
                                        </body>
                                </html>
                        </xhtml>
                </message>
        </mail>
	,
        <mail>
                <from>Person 4 &lt;john@doe.org&gt;</from>
                <to>3jane@ashpool.not</to>
                <cc>john@doe.org</cc>
                <bcc>matrix@does.not.eXist</bcc>
                <subject>Testing send-email()</subject>
                <message>
                        <text>Test message, Testing Attach</text>
                </message>
				<attachment filename="hello.txt" mimetype="text/plain">caracolas en el mar</attachment>
        </mail>

};

let $props := <properties>
                <property name="mail.debug" value="false"/>
                <property name="mail.smtp.starttls.enable" value="true"/>
                <property name="mail.smtp.auth" value="true"/>
                <property name="mail.smtp.port" value="25"/>
                <property name="mail.smtp.host" value="some.smtp.host"/>
                <property name="mail.smtp.user" value="someuser"/>
                <property name="mail.smtp.password"  value="its password"/>
        </properties>

(: It throws an exception when there has been some problem composing or sending the e-mails :)
let $session    := mail:get-mail-session( $props )
return
mail:send-email($session,$message)
