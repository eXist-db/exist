/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.mail;

import com.icegreen.greenmail.junit4.GreenMailRule;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.UUIDGenerator;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.exist.util.IPUtil.nextFreePort;
import static org.exist.xquery.modules.mail.Util.executeQuery;
import static org.exist.xquery.modules.mail.Util.withCompiledQuery;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Send Email Integration Tests.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SendEmailIT {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private final int smtpPort = nextFreePort(2525, 2599, 10);

    @Rule
    public final GreenMailRule greenMail = new GreenMailRule(new ServerSetup(smtpPort, "127.0.0.1", "smtp"));

    @Test
    public void sendEmailSmtpDirect() throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        final String from = "sender@place1.com";
        final String to = "recipient@place2.com";
        final String subject = "some email subject";
        final String messageText = UUIDGenerator.getUUIDversion4();

        final String query =
                "import module namespace mail = \"http://exist-db.org/xquery/mail\";\n" +
                "mail:send-email(\n" +
                "    <mail><from>" + from + "</from><to>" + to + "</to><subject>" + subject + "</subject><message><text>" + messageText + "</text></message></mail>,\n" +
                "    '127.0.0.1:" + smtpPort + "',\n" +
                "    ()\n" +
                ")";

        // send the email from XQuery via SMTP
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            final Boolean sendResult = withCompiledQuery(broker, source, compiledXQuery -> {
                final Sequence result = executeQuery(broker, compiledXQuery);
                return result.itemAt(0).toJavaObject(boolean.class);
            });

            transaction.commit();

            assertTrue(sendResult.booleanValue());
        }

        // check the SMTP server received the email
        final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertEquals(1, receivedMessages.length);
        final MimeMessage receivedMessage = receivedMessages[0];
//        final Address sender = receivedMessage.getSender();
//        assertEquals(from, sender.toString());
        final Address[] recipients = receivedMessage.getRecipients(Message.RecipientType.TO);
        assertEquals(1, recipients.length);
        assertEquals(to, recipients[0].toString());
        assertEquals(subject, receivedMessage.getSubject());
        assertEquals(messageText, GreenMailUtil.getBody(receivedMessage));
    }

    @Test
    public void sendEmailJavaMail() throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        final String from = "sender@place1.com";
        final String to = "recipient@place2.com";
        final String subject = "some email subject";
        final String messageText = UUIDGenerator.getUUIDversion4();

        final String query =
                "import module namespace mail = \"http://exist-db.org/xquery/mail\";\n" +
                "let $session := mail:get-mail-session(\n" +
                "    <properties>\n" +
                "        <property name=\"mail.transport.protocol\" value=\"smtp\"/>\n" +
                "        <property name=\"mail.smtp.port\" value=\"" + smtpPort + "\"/>\n" +
                "        <property name=\"mail.smtp.host\" value=\"127.0.0.1\"/>\n" +
                "    </properties>\n" +
                ")\n" +
                "return\n" +
                "    mail:send-email(\n" +
                "        $session,\n" +
                "        <mail><from>" + from + "</from><to>" + to + "</to><subject>" + subject + "</subject><message><text>" + messageText + "</text></message></mail>\n" +
                "    )";

        // send the email from XQuery via SMTP
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Source source = new StringSource(query);
        try (final DBBroker broker = pool.getBroker();
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // execute query
            final Boolean sendResult = withCompiledQuery(broker, source, compiledXQuery -> {
                executeQuery(broker, compiledXQuery);
                return true;
            });

            transaction.commit();

            assertTrue(sendResult.booleanValue());
        }

        // check the SMTP server received the email
        final MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
        assertEquals(1, receivedMessages.length);
        final MimeMessage receivedMessage = receivedMessages[0];
//        final Address sender = receivedMessage.getSender();
//        assertEquals(from, sender.toString());
        final Address[] recipients = receivedMessage.getRecipients(Message.RecipientType.TO);
        assertEquals(1, recipients.length);
        assertEquals(to, recipients[0].toString());
        assertEquals(subject, receivedMessage.getSubject());
        assertEquals(messageText, GreenMailUtil.getBody(receivedMessage));
    }

}
