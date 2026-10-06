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

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.commons.codec.binary.Base64;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.util.UUIDGenerator;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.xquery.modules.mail.Util.executeQuery;
import static org.exist.xquery.modules.mail.Util.withCompiledQuery;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Send Email Integration Tests.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SendEmailIT {

    enum SmtpImplementation {
        SMTP_DIRECT_CONNECTION,
        JAKARTA_MAIL
    }

    enum AuthenticationOption {
        NOT_AUTHENTICATED,
        AUTHENTICATED
    }

    public SmtpImplementation smtpImplementation;
    public AuthenticationOption authenticationOption;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @RegisterExtension
    public final GreenMailExtension greenMail = new GreenMailExtension(new ServerSetup(0, "127.0.0.1", "smtp"));

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/mail-module-test");
    private static final XmldbURI XML_DOC1_NAME = XmldbURI.create("doc1.xml");
    private static final String XML_DOC1_CONTENT = "<uuid>" + UUIDGenerator.getUUIDversion4() + "</uuid>";
    private static final XmldbURI BIN_DOC1_NAME = XmldbURI.create("doc 1.bin");     // NOTE(AR) intentionally contains a space character to test correct encoding/decoding
    private static final byte[] BIN_DOC1_CONTENT = UUIDGenerator.getUUIDversion4().getBytes(UTF_8);

    private static final String EMAIL_UID = "emailuid";
    private static final String EMAIL_PWD = "emailpwd";

    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { SmtpImplementation.SMTP_DIRECT_CONNECTION, AuthenticationOption.NOT_AUTHENTICATED },
                { SmtpImplementation.JAKARTA_MAIL, AuthenticationOption.NOT_AUTHENTICATED },
                { SmtpImplementation.JAKARTA_MAIL, AuthenticationOption.AUTHENTICATED },
        });
    }

    @BeforeAll
    static void setup() throws PermissionDeniedException, IOException, SAXException, EXistException, LockException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.get(Optional.of(brokerPool.getSecurityManager().getSystemSubject()));
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

             try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                broker.storeDocument(transaction, XML_DOC1_NAME, new StringInputSource(XML_DOC1_CONTENT), MimeType.XML_TYPE, collection);
                broker.storeDocument(transaction, BIN_DOC1_NAME, new StringInputSource(BIN_DOC1_CONTENT), null, collection);
             }

            transaction.commit();
        }
    }

    private void setSmtpAuth() {
        if (authenticationOption == AuthenticationOption.AUTHENTICATED) {
            greenMail.setUser(EMAIL_UID, EMAIL_PWD);
        }
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextEmail(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final MimeMessage receivedMessage = sendEmail(message, null);

        assertEquals("text/plain; charset=UTF-8", receivedMessage.getContentType());
        assertEquals(messageText, GreenMailUtil.getBody(receivedMessage));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextEmailWithXmlAttachment(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final MimeMessage receivedMessage = sendEmail(message, new String[] {
                TEST_COLLECTION.append(XML_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertEquals(messageText, firstPartBody);

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/xml; name=" + XML_DOC1_NAME.lastSegment().getCollectionPath(), secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertEquals(XML_DOC1_CONTENT, new String(Base64.decodeBase64(secondPartBody), UTF_8));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextEmailWithBinaryAttachment(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final MimeMessage receivedMessage = sendEmail(message, new String[] {
                TEST_COLLECTION.append(BIN_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertEquals(messageText, firstPartBody);

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/octet-stream; name=\"" + BIN_DOC1_NAME.lastSegment().getCollectionPath() + "\"", secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertArrayEquals(BIN_DOC1_CONTENT, Base64.decodeBase64(secondPartBody));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextEmailWithXmlAndBinaryAttachments(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final MimeMessage receivedMessage = sendEmail(message, new String[] {
                TEST_COLLECTION.append(XML_DOC1_NAME).getCollectionPath(),
                TEST_COLLECTION.append(BIN_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(3, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertEquals(messageText, firstPartBody);

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/xml; name=" + XML_DOC1_NAME.lastSegment().getCollectionPath(), secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertEquals(XML_DOC1_CONTENT, new String(Base64.decodeBase64(secondPartBody), UTF_8));

        final BodyPart thirdPart = multipartContent.getBodyPart(2);
        assertEquals("application/octet-stream; name=\"" + BIN_DOC1_NAME.lastSegment().getCollectionPath() + "\"", thirdPart.getContentType());
        final String thirdPartBody = GreenMailUtil.getBody(thirdPart);
        assertArrayEquals(BIN_DOC1_CONTENT, Base64.decodeBase64(thirdPartBody));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendHtmlEmail(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(htmlMessage, null);

        assertEquals("text/html; charset=UTF-8", receivedMessage.getContentType());
        final String body = GreenMailUtil.getBody(receivedMessage);
        assertTrue(body.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(body.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(body.contains("<p>" + htmlMessageText + "</p>"));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendHtmlEmailWithXmlAttachment(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(htmlMessage, new String[] {
                TEST_COLLECTION.append(XML_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/html; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertTrue(firstPartBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(firstPartBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(firstPartBody.contains("<p>" + htmlMessageText + "</p>"));

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/xml; name=" + XML_DOC1_NAME.lastSegment().getCollectionPath(), secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertEquals(XML_DOC1_CONTENT, new String(Base64.decodeBase64(secondPartBody), UTF_8));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendHtmlEmailWithBinaryAttachment(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(htmlMessage, new String[] {
                TEST_COLLECTION.append(BIN_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/html; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertTrue(firstPartBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(firstPartBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(firstPartBody.contains("<p>" + htmlMessageText + "</p>"));

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/octet-stream; name=\"" + BIN_DOC1_NAME.lastSegment().getCollectionPath() + "\"", secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertArrayEquals(BIN_DOC1_CONTENT, Base64.decodeBase64(secondPartBody));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendHtmlEmailWithXmlAndBinaryAttachments(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(htmlMessage, new String[] {
                TEST_COLLECTION.append(XML_DOC1_NAME).getCollectionPath(),
                TEST_COLLECTION.append(BIN_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(3, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/html; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertTrue(firstPartBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(firstPartBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(firstPartBody.contains("<p>" + htmlMessageText + "</p>"));

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/xml; name=" + XML_DOC1_NAME.lastSegment().getCollectionPath(), secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertEquals(XML_DOC1_CONTENT, new String(Base64.decodeBase64(secondPartBody), UTF_8));

        final BodyPart thirdPart = multipartContent.getBodyPart(2);
        assertEquals("application/octet-stream; name=\"" + BIN_DOC1_NAME.lastSegment().getCollectionPath() + "\"", thirdPart.getContentType());
        final String thirdPartBody = GreenMailUtil.getBody(thirdPart);
        assertArrayEquals(BIN_DOC1_CONTENT, Base64.decodeBase64(thirdPartBody));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextAndHtmlEmail(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(message + htmlMessage, null);

        assertTrue(receivedMessage.getContentType().startsWith("multipart/alternative"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPart.getContentType());
        final String firstPartBody = GreenMailUtil.getBody(firstPart);
        assertEquals(messageText, firstPartBody);

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("text/html; charset=UTF-8", secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertTrue(secondPartBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(secondPartBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(secondPartBody.contains("<p>" + htmlMessageText + "</p>"));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextAndHtmlEmailWithXmlAttachment(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(message + htmlMessage, new String[] {
                TEST_COLLECTION.append(XML_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertTrue(firstPart.getContentType().startsWith("multipart/alternative"));
        final Object firstPartContent = firstPart.getContent();
        assertInstanceOf(MimeMultipart.class, firstPartContent);
        final MimeMultipart multipartFirstPartContent = (MimeMultipart) firstPartContent;
        assertEquals(2, multipartFirstPartContent.getCount());

        final BodyPart firstPartFirstBodyPart = multipartFirstPartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPartFirstBodyPart.getContentType());
        final String firstPartFirstBody = GreenMailUtil.getBody(firstPartFirstBodyPart);
        assertEquals(messageText, firstPartFirstBody);

        final BodyPart firstPartSecondBodyPart = multipartFirstPartContent.getBodyPart(1);
        assertEquals("text/html; charset=UTF-8", firstPartSecondBodyPart.getContentType());
        final String firstPartSecondBody = GreenMailUtil.getBody(firstPartSecondBodyPart);
        assertTrue(firstPartSecondBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(firstPartSecondBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(firstPartSecondBody.contains("<p>" + htmlMessageText + "</p>"));

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/xml; name=" + XML_DOC1_NAME.lastSegment().getCollectionPath(), secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertEquals(XML_DOC1_CONTENT, new String(Base64.decodeBase64(secondPartBody), UTF_8));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextAndHtmlEmailWithBinaryAttachment(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(message + htmlMessage, new String[] {
                TEST_COLLECTION.append(BIN_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(2, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertTrue(firstPart.getContentType().startsWith("multipart/alternative"));
        final Object firstPartContent = firstPart.getContent();
        assertInstanceOf(MimeMultipart.class, firstPartContent);
        final MimeMultipart multipartFirstPartContent = (MimeMultipart) firstPartContent;
        assertEquals(2, multipartFirstPartContent.getCount());

        final BodyPart firstPartFirstBodyPart = multipartFirstPartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPartFirstBodyPart.getContentType());
        final String firstPartFirstBody = GreenMailUtil.getBody(firstPartFirstBodyPart);
        assertEquals(messageText, firstPartFirstBody);

        final BodyPart firstPartSecondBodyPart = multipartFirstPartContent.getBodyPart(1);
        assertEquals("text/html; charset=UTF-8", firstPartSecondBodyPart.getContentType());
        final String firstPartSecondBody = GreenMailUtil.getBody(firstPartSecondBodyPart);
        assertTrue(firstPartSecondBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(firstPartSecondBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(firstPartSecondBody.contains("<p>" + htmlMessageText + "</p>"));

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/octet-stream; name=\"" + BIN_DOC1_NAME.lastSegment().getCollectionPath() + "\"", secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertArrayEquals(BIN_DOC1_CONTENT, Base64.decodeBase64(secondPartBody));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0} {1}")
    void sendTextAndHtmlEmailWithXmlAndBinaryAttachments(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        initSendEmailIT(smtpImplementation, authenticationOption);
        setSmtpAuth();
        final String messageText = UUIDGenerator.getUUIDversion4();
        final String message = "<text>" + messageText + "</text>";

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<xhtml><html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html></xhtml>";

        final MimeMessage receivedMessage = sendEmail(message + htmlMessage, new String[] {
                TEST_COLLECTION.append(XML_DOC1_NAME).getCollectionPath(),
                TEST_COLLECTION.append(BIN_DOC1_NAME).getCollectionPath()
        });

        assertTrue(receivedMessage.getContentType().startsWith("multipart/mixed"));
        final Object content = receivedMessage.getContent();
        assertInstanceOf(MimeMultipart.class, content);
        final MimeMultipart multipartContent = (MimeMultipart) content;
        assertEquals(3, multipartContent.getCount());

        final BodyPart firstPart = multipartContent.getBodyPart(0);
        assertTrue(firstPart.getContentType().startsWith("multipart/alternative"));
        final Object firstPartContent = firstPart.getContent();
        assertInstanceOf(MimeMultipart.class, firstPartContent);
        final MimeMultipart multipartFirstPartContent = (MimeMultipart) firstPartContent;
        assertEquals(2, multipartFirstPartContent.getCount());

        final BodyPart firstPartFirstBodyPart = multipartFirstPartContent.getBodyPart(0);
        assertEquals("text/plain; charset=UTF-8", firstPartFirstBodyPart.getContentType());
        final String firstPartFirstBody = GreenMailUtil.getBody(firstPartFirstBodyPart);
        assertEquals(messageText, firstPartFirstBody);

        final BodyPart firstPartSecondBodyPart = multipartFirstPartContent.getBodyPart(1);
        assertEquals("text/html; charset=UTF-8", firstPartSecondBodyPart.getContentType());
        final String firstPartSecondBody = GreenMailUtil.getBody(firstPartSecondBodyPart);
        assertTrue(firstPartSecondBody.contains("<title>" + htmlTitle + "</title>"));
        assertTrue(firstPartSecondBody.contains("<h1>" + htmlHeading + "</h1>"));
        assertTrue(firstPartSecondBody.contains("<p>" + htmlMessageText + "</p>"));

        final BodyPart secondPart = multipartContent.getBodyPart(1);
        assertEquals("application/xml; name=" + XML_DOC1_NAME.lastSegment().getCollectionPath(), secondPart.getContentType());
        final String secondPartBody = GreenMailUtil.getBody(secondPart);
        assertEquals(XML_DOC1_CONTENT, new String(Base64.decodeBase64(secondPartBody), UTF_8));

        final BodyPart thirdPart = multipartContent.getBodyPart(2);
        assertEquals("application/octet-stream; name=\"" + BIN_DOC1_NAME.lastSegment().getCollectionPath() + "\"", thirdPart.getContentType());
        final String thirdPartBody = GreenMailUtil.getBody(thirdPart);
        assertArrayEquals(BIN_DOC1_CONTENT, Base64.decodeBase64(thirdPartBody));
    }

    private MimeMessage sendEmail(final String message, @Nullable final String[] attachmentPaths) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        return switch (smtpImplementation) {
            case SMTP_DIRECT_CONNECTION -> sendEmailBySmtpDirectConnection(message, attachmentPaths);
            case JAKARTA_MAIL -> sendEmailByJakartaMail(message, attachmentPaths);
            default -> throw new IllegalArgumentException("Unknown SMTP implementation: " + smtpImplementation);
        };
    }

    private MimeMessage sendEmailBySmtpDirectConnection(final String message, @Nullable final String[] attachmentPaths) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        if (authenticationOption == AuthenticationOption.AUTHENTICATED) {
            throw new UnsupportedOperationException("Authentication is not yet implemented by SMTP direct connection");
        }

        final String from = "sender@place1.com";
        final String to = "recipient@place2.com";
        final String subject = "some email subject";

        String tmpAttachmentPaths = "";
        if (attachmentPaths != null && attachmentPaths.length > 0) {
            tmpAttachmentPaths = String.join("','", attachmentPaths);
            tmpAttachmentPaths = "'" + tmpAttachmentPaths + "'";
        }

        final String query =
                "import module namespace mail = \"http://exist-db.org/xquery/mail\";\n" +
                        "let $attachments := \n" +
                        "    for $attachment-path in (" + tmpAttachmentPaths + ")\n" +
                        "    let $is-binary := util:binary-doc-available($attachment-path)\n" +
                        "    let $attachment-file-name := fn:replace($attachment-path, '.+/(.+)', '$1')\n" +
                        "    let $attachment-media-type := xmldb:get-mime-type($attachment-path)\n" +
                        "    return\n" +
                        "      if ($is-binary)\n" +
                        "      then\n" +
                        "        <attachment filename='{$attachment-file-name}' mimetype='{$attachment-media-type}'>{util:binary-doc($attachment-path)}</attachment>\n" +
                        "      else\n" +
                        "        <attachment filename='{$attachment-file-name}' mimetype='{$attachment-media-type}'>{util:base64-encode(fn:serialize(fn:doc($attachment-path), map { 'method': 'XML' }))}</attachment>\n" +
                        "return\n" +
                        "  mail:send-email(\n" +
                        "      <mail><from>" + from + "</from><to>" + to + "</to><subject>" + subject + "</subject><message>" + message + "</message>{$attachments}</mail>,\n" +
                        "      '127.0.0.1:" + greenMail.getSmtp().getPort() + "',\n" +
                        "      ()\n" +
                        "  )";

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
        final Address[] froms = receivedMessage.getFrom();
        assertEquals(1, froms.length);
        assertEquals(from, froms[0].toString());
        final Address[] recipients = receivedMessage.getRecipients(Message.RecipientType.TO);
        assertEquals(1, recipients.length);
        assertEquals(to, recipients[0].toString());
        assertEquals(subject, receivedMessage.getSubject());

        return receivedMessage;
    }

    private MimeMessage sendEmailByJakartaMail(final String message, final String[] attachmentPaths) throws EXistException, XPathException, PermissionDeniedException, IOException, MessagingException {
        final String from = "sender@place1.com";
        final String to = "recipient@place2.com";
        final String subject = "some email subject";

        String tmpAttachmentPaths = "";
        if (attachmentPaths != null && attachmentPaths.length > 0) {
            tmpAttachmentPaths = String.join("','", attachmentPaths);
            tmpAttachmentPaths = "'" + tmpAttachmentPaths + "'";
        }

        String query =
                "import module namespace mail = \"http://exist-db.org/xquery/mail\";\n" +
                        "let $attachments := \n" +
                        "    for $attachment-path in (" + tmpAttachmentPaths + ")\n" +
                        "    let $is-binary := util:binary-doc-available($attachment-path)\n" +
                        "    let $attachment-file-name := fn:replace($attachment-path, '.+/(.+)', '$1')\n" +
                        "    let $attachment-media-type := xmldb:get-mime-type($attachment-path)\n" +
                        "    return\n" +
                        "      if ($is-binary)\n" +
                        "      then\n" +
                        "        <attachment filename='{$attachment-file-name}' mimetype='{$attachment-media-type}'>{util:binary-doc($attachment-path)}</attachment>\n" +
                        "      else\n" +
                        "        <attachment filename='{$attachment-file-name}' mimetype='{$attachment-media-type}'>{util:base64-encode(fn:serialize(fn:doc($attachment-path), map { 'method': 'XML' }))}</attachment>\n" +
                        "return\n" +
                        "  let $session := mail:get-mail-session(\n" +
                        "      <properties>\n" +
                        "          <property name=\"mail.transport.protocol\" value=\"smtp\"/>\n" +
                        "          <property name=\"mail.smtp.port\" value=\"" + greenMail.getSmtp().getPort() + "\"/>\n" +
                        "          <property name=\"mail.smtp.host\" value=\"127.0.0.1\"/>\n";

        if (authenticationOption == AuthenticationOption.AUTHENTICATED) {
            query +=
                    "          <property name=\"mail.smtp.auth\" value=\"true\"/>" +
                        "      </properties>\n" +
                    ", \n" +
                    "<authentication username='" + EMAIL_UID + "' password='" + EMAIL_PWD + "'/>";
        } else {
            query +=
                    "      </properties>\n";
        }

        query +=
                        ")\n" +
                        "  return\n" +
                        "    mail:send-email(\n" +
                        "        $session,\n" +
                        "        <mail><from>" + from + "</from><to>" + to + "</to><subject>" + subject + "</subject><message>" + message + "</message>{$attachments}</mail>\n" +
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
        final Address[] froms = receivedMessage.getFrom();
        assertEquals(1, froms.length);
        assertEquals(from, froms[0].toString());
        final Address[] recipients = receivedMessage.getRecipients(Message.RecipientType.TO);
        assertEquals(1, recipients.length);
        assertEquals(to, recipients[0].toString());
        assertEquals(subject, receivedMessage.getSubject());

        return receivedMessage;
    }

    public void initSendEmailIT(SmtpImplementation smtpImplementation, AuthenticationOption authenticationOption) {
        this.smtpImplementation = smtpImplementation;
        this.authenticationOption = authenticationOption;
    }

}
