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


(: Demonstrates sending an email with SMTP from eXist (NB - Using Sendmail instead of SMTP seems to perform much faster) :)


declare namespace mail="http://exist-db.org/xquery/mail";

declare variable $message
{
        <mail>
                <from>Person 1 &lt;sender@domain.com&gt;</from>
                <to>recipient@otherdomain.com</to>
                <cc>cc@otherdomain.com</cc>
                <bcc>bcc@otherdomain.com</bcc>
                <subject>Testing send-email()</subject>
                <message>
                        <text>Test message, Testing 1, 2, 3</text>
                        <xhtml>
                                <html>
                                        <head>
                                                <title>Testing</title>
                                        </head>
                                        <body>
                                                <h1>Testing</h1>
                                                <p>Test Message 1, 2, 3</p>
                                        </body>
                                </html>
                        </xhtml>
                </message>
        </mail>
};

if(mail:send-email($message, "smtp-server.domain.com", ()))then
(
        <h1>Sent Message OK :-)</h1>
)
else
(
        <h1>Could not Send Message :-(</h1>
)
