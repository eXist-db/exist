xquery version "1.0";


(: Demonstrates sending an email through Sendmail from eXist :)


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

if(mail:send-email($message, (), ()))then
(
        <h1>Sent Message OK :-)</h1>
)
else
(
        <h1>Could not Send Message :-(</h1>
)
