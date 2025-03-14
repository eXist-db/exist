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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.commons.codec.binary.Base64;
import org.exist.util.UUIDGenerator;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Write Message Tests.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class WriteMessageTest {

    private static final String CHARSET = "UTF-8";
    private static final String XML_DOC1_NAME = "doc1.xml";
    private static final String XML_DOC1_CONTENT = "<uuid>" + UUIDGenerator.getUUIDversion4() + "</uuid>";
    private static final SendEmailFunction.MailAttachment XML_DOC1_ATTACHMENT = new SendEmailFunction.MailAttachment(XML_DOC1_NAME, "application/xml", Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)));
    private static final String BIN_DOC1_NAME = "doc 1.bin";     // NOTE(AR) intentionally contains a space character to test correct encoding/decoding
    private static final byte[] BIN_DOC1_CONTENT = UUIDGenerator.getUUIDversion4().getBytes(UTF_8);
    private static final SendEmailFunction.MailAttachment BIN_DOC1_ATTACHMENT = new SendEmailFunction.MailAttachment(BIN_DOC1_NAME, "application/octet-stream", Base64.encodeBase64String(BIN_DOC1_CONTENT));
    private static final String XHTML_11_DOCTYPE = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">";
    private static final String DEFAULT_TRANSFER_ENCODING = "8bit";
    private static final String BASE64_TRANSFER_ENCODING = "base64";
    private static final String TEXT_HTML_CONTENT_TYPE = contentType("text/html", Tuple("charset", CHARSET));
    private static final String TEXT_PLAIN_CONTENT_TYPE = contentType("text/plain", Tuple("charset", CHARSET));
    private static final String MULTIPART_BOUNDARY_PREFIX_1 = SendEmailFunction.multipartBoundaryPrefix(1);
    private static final String MULTIPART_BOUNDARY_PREFIX_2 = SendEmailFunction.multipartBoundaryPrefix(2);
    private static final String MULTIPART_MIXED_CONTENT_TYPE = "multipart/mixed";
    private static final String MULTIPART_ALTERNATIVE_CONTENT_TYPE = "multipart/alternative";
    private static final String XML_DOC1_CONTENT_TYPE = contentType("application/xml", Tuple("name", XML_DOC1_NAME));
    private static final String BIN_DOC1_CONTENT_TYPE = contentType("application/octet-stream", Tuple("name", BIN_DOC1_NAME));

    private static final String FROM = "sender@place1.com";
    private static final String TO = "recipient@place2.com";
    private static final String SUBJECT = "some email with subject: " + UUIDGenerator.getUUIDversion4();

    @Test
    public void writeTextMessage() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();

        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, messageLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, messageLines[i++]);

        final String[] messageBodyLines = extractMessageBody(messageLines, i);

        assertEquals(1, messageBodyLines.length);
        assertEquals(messageText, messageBodyLines[0]);
    }

    @Test
    public void writeTextMessageWithXmlAttachment() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();
        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.addAttachment(XML_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(messageText, firstPart.bodyLines[0]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(XML_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)), secondPart.bodyLines[0]);
    }

    @Test
    public void writeTextMessageWithBinaryAttachment() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();
        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.addAttachment(BIN_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(messageText, firstPart.bodyLines[0]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(BIN_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(BIN_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(BIN_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(BIN_DOC1_CONTENT), secondPart.bodyLines[0]);
    }

    @Test
    public void writeTextMessageWithXmlAndBinaryAttachments() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();
        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.addAttachment(XML_DOC1_ATTACHMENT);
        mail.addAttachment(BIN_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(3, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(messageText, firstPart.bodyLines[0]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(XML_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)), secondPart.bodyLines[0]);

        i = 0;
        final Part thirdPart = parts[2];
        assertContentType(BIN_DOC1_CONTENT_TYPE, thirdPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, thirdPart.headerLines[i++]);
        assertContentDescription(BIN_DOC1_NAME, thirdPart.headerLines[i++]);
        assertContentDispositionAttachment(BIN_DOC1_NAME, thirdPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(BIN_DOC1_CONTENT), thirdPart.bodyLines[0]);
    }

    @Test
    public void writeHtmlMessage() throws IOException {
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setXHTML(htmlMessage);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertContentType(TEXT_HTML_CONTENT_TYPE, messageLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, messageLines[i++]);

        final String[] messageBodyLines = extractMessageBody(messageLines, i);

        assertEquals(2, messageBodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, messageBodyLines[0]);
        assertEquals(htmlMessage, messageBodyLines[1]);
    }

    @Test
    public void writeHtmlMessageWithXmlAttachment() throws IOException {
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setXHTML(htmlMessage);
        mail.addAttachment(XML_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_HTML_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(2, firstPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, firstPart.bodyLines[0]);
        assertEquals(htmlMessage, firstPart.bodyLines[1]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(XML_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)), secondPart.bodyLines[0]);
    }

    @Test
    public void writeHtmlMessageWithBinaryAttachment() throws IOException {
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setXHTML(htmlMessage);
        mail.addAttachment(BIN_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_HTML_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(2, firstPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, firstPart.bodyLines[0]);
        assertEquals(htmlMessage, firstPart.bodyLines[1]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(BIN_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(BIN_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(BIN_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(BIN_DOC1_CONTENT), secondPart.bodyLines[0]);
    }

    @Test
    public void writeHtmlMessageWithXmlAndBinaryAttachments() throws IOException {
        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setXHTML(htmlMessage);
        mail.addAttachment(XML_DOC1_ATTACHMENT);
        mail.addAttachment(BIN_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(3, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_HTML_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(2, firstPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, firstPart.bodyLines[0]);
        assertEquals(htmlMessage, firstPart.bodyLines[1]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(XML_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)), secondPart.bodyLines[0]);

        i = 0;
        final Part thirdPart = parts[2];
        assertContentType(BIN_DOC1_CONTENT_TYPE, thirdPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, thirdPart.headerLines[i++]);
        assertContentDescription(BIN_DOC1_NAME, thirdPart.headerLines[i++]);
        assertContentDispositionAttachment(BIN_DOC1_NAME, thirdPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(BIN_DOC1_CONTENT), thirdPart.bodyLines[0]);
    }

    @Test
    public void writeHtmlAndTextMessage() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.setXHTML(htmlMessage);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_ALTERNATIVE_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPart.headerLines[i++]);
        assertEquals(messageText, firstPart.bodyLines[0]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(TEXT_HTML_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertEquals(2, secondPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, secondPart.bodyLines[0]);
        assertEquals(htmlMessage, secondPart.bodyLines[1]);
    }

    @Test
    public void writeHtmlAndTextMessageWithXmlAttachment() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.setXHTML(htmlMessage);
        mail.addAttachment(XML_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertMultipartContentTypeWithBoundary(MULTIPART_ALTERNATIVE_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_2, firstPart.headerLines[i++]);
        i = 0;
        final Part[] firstPartParts = extractPartsFromPart(firstPart.bodyLines, i++, MULTIPART_BOUNDARY_PREFIX_2);
        assertEquals(2, firstPartParts.length);

        i = 0;
        final Part firstPartFirstPart = firstPartParts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPartFirstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPartFirstPart.headerLines[i++]);
        assertEquals(messageText, firstPartFirstPart.bodyLines[0]);

        i = 0;
        final Part firstPartSecondPart = firstPartParts[1];
        assertContentType(TEXT_HTML_CONTENT_TYPE, firstPartSecondPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPartSecondPart.headerLines[i++]);
        assertEquals(2, firstPartSecondPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, firstPartSecondPart.bodyLines[0]);
        assertEquals(htmlMessage, firstPartSecondPart.bodyLines[1]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(XML_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)), secondPart.bodyLines[0]);
    }

    @Test
    public void writeHtmlAndTextMessageWithBinaryAttachment() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.setXHTML(htmlMessage);
        mail.addAttachment(BIN_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(2, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertMultipartContentTypeWithBoundary(MULTIPART_ALTERNATIVE_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_2, firstPart.headerLines[i++]);
        i = 0;
        final Part[] firstPartParts = extractPartsFromPart(firstPart.bodyLines, i++, MULTIPART_BOUNDARY_PREFIX_2);
        assertEquals(2, firstPartParts.length);

        i = 0;
        final Part firstPartFirstPart = firstPartParts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPartFirstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPartFirstPart.headerLines[i++]);
        assertEquals(messageText, firstPartFirstPart.bodyLines[0]);

        i = 0;
        final Part firstPartSecondPart = firstPartParts[1];
        assertContentType(TEXT_HTML_CONTENT_TYPE, firstPartSecondPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPartSecondPart.headerLines[i++]);
        assertEquals(2, firstPartSecondPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, firstPartSecondPart.bodyLines[0]);
        assertEquals(htmlMessage, firstPartSecondPart.bodyLines[1]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(BIN_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(BIN_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(BIN_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(BIN_DOC1_CONTENT), secondPart.bodyLines[0]);
    }


    @Test
    public void writeHtmlAndTextMessageWithXmlAndBinaryAttachments() throws IOException {
        final String messageText = UUIDGenerator.getUUIDversion4();

        final String htmlTitle = UUIDGenerator.getUUIDversion4();
        final String htmlHeading = UUIDGenerator.getUUIDversion4();
        final String htmlMessageText = UUIDGenerator.getUUIDversion4();

        final String htmlMessage = "<html><head><title>" + htmlTitle + "</title></head><body><h1>" + htmlHeading + "</h1><p>" + htmlMessageText + "</p></body></html>";

        final SendEmailFunction.Mail mail = createMail();
        mail.setText(messageText);
        mail.setXHTML(htmlMessage);
        mail.addAttachment(XML_DOC1_ATTACHMENT);
        mail.addAttachment(BIN_DOC1_ATTACHMENT);

        final String[] messageLines = writeMessage(mail);

        int i = 0;
        assertMultipartContentTypeWithBoundary(MULTIPART_MIXED_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_1, messageLines[i++]);
        final String[] messageBodyLines = extractMessageBody(messageLines, i);
        i = 0;
        assertEquals(SendEmailFunction.ERROR_MSG_NON_MIME_CLIENT, messageBodyLines[i++]);

        final Part[] parts = extractParts(messageBodyLines, i, MULTIPART_BOUNDARY_PREFIX_1);
        assertEquals(3, parts.length);

        i = 0;
        final Part firstPart = parts[0];
        assertMultipartContentTypeWithBoundary(MULTIPART_ALTERNATIVE_CONTENT_TYPE, MULTIPART_BOUNDARY_PREFIX_2, firstPart.headerLines[i++]);
        i = 0;
        final Part[] firstPartParts = extractPartsFromPart(firstPart.bodyLines, i++, MULTIPART_BOUNDARY_PREFIX_2);
        assertEquals(2, firstPartParts.length);

        i = 0;
        final Part firstPartFirstPart = firstPartParts[0];
        assertContentType(TEXT_PLAIN_CONTENT_TYPE, firstPartFirstPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPartFirstPart.headerLines[i++]);
        assertEquals(messageText, firstPartFirstPart.bodyLines[0]);

        i = 0;
        final Part firstPartSecondPart = firstPartParts[1];
        assertContentType(TEXT_HTML_CONTENT_TYPE, firstPartSecondPart.headerLines[i++]);
        assertContentTransferEncoding(DEFAULT_TRANSFER_ENCODING, firstPartSecondPart.headerLines[i++]);
        assertEquals(2, firstPartSecondPart.bodyLines.length);
        assertEquals(XHTML_11_DOCTYPE, firstPartSecondPart.bodyLines[0]);
        assertEquals(htmlMessage, firstPartSecondPart.bodyLines[1]);

        i = 0;
        final Part secondPart = parts[1];
        assertContentType(XML_DOC1_CONTENT_TYPE, secondPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, secondPart.headerLines[i++]);
        assertContentDescription(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertContentDispositionAttachment(XML_DOC1_NAME, secondPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(XML_DOC1_CONTENT.getBytes(UTF_8)), secondPart.bodyLines[0]);

        i = 0;
        final Part thirdPart = parts[2];
        assertContentType(BIN_DOC1_CONTENT_TYPE, thirdPart.headerLines[i++]);
        assertContentTransferEncoding(BASE64_TRANSFER_ENCODING, thirdPart.headerLines[i++]);
        assertContentDescription(BIN_DOC1_NAME, thirdPart.headerLines[i++]);
        assertContentDispositionAttachment(BIN_DOC1_NAME, thirdPart.headerLines[i++]);
        assertEquals(Base64.encodeBase64String(BIN_DOC1_CONTENT), thirdPart.bodyLines[0]);
    }

    private static SendEmailFunction.Mail createMail() {
        final SendEmailFunction.Mail mail = new SendEmailFunction.Mail();
        mail.setFrom(FROM);
        mail.addTo(TO);
        mail.setSubject(SUBJECT);
        return mail;
    }

    private String[] writeMessage(final SendEmailFunction.Mail mail) throws IOException {
        final String[] lines;
        try (final StringWriter writer = new StringWriter();
             final PrintWriter printWriter = new PrintWriter(writer)) {
            SendEmailFunction.writeMessage(printWriter, mail, true, CHARSET);

            lines = writer.toString().split("\r\n");
        }

        assertTrue(lines != null);
        assertTrue(lines.length > 4);

        int i = 0;
        assertFrom(FROM, lines[i++]);
        assertTo(TO, lines[i++]);
        assertDate(lines[i++]);
        assertSubject(SUBJECT, lines[i++]);
        assertXMailer(lines[i++]);
        assertMimeVersion(lines[i++]);

        return Arrays.copyOfRange(lines, i, lines.length);
    }

    private static void assertFrom(final String expected, final String messageLine) {
        assertEquals("From: " + expected, messageLine);
    }

    private static void assertTo(final String expected, final String messageLine) {
        assertEquals("To: " + expected, messageLine);
    }

    private static void assertDate(final String messageLine) {
        assertTrue(messageLine.startsWith("Date: "));
    }

    private static void assertSubject(final String expected, final String messageLine) throws UnsupportedEncodingException {
        assertEquals("Subject: " + SendEmailFunction.encode64(expected, CHARSET), messageLine);
    }

    private static void assertXMailer(final String messageLine) {
        assertTrue(messageLine.startsWith("X-Mailer: "));
    }

    private static void assertMimeVersion(final String messageLine) {
        assertTrue(messageLine.startsWith("MIME-Version: "));
    }

    private static String contentType(final String mediaType, final Tuple2<String, String>... parameters) {
        final StringBuilder builder = new StringBuilder();
        builder.append(mediaType);
        if (parameters != null) {
            for (final Tuple2<String, String> parameter : parameters) {
                builder
                        .append("; ")
                        .append(parameter._1)
                        .append('=')
                        .append(SendEmailFunction.parameterValue(parameter._2));
            }
        }
        return builder.toString();
    }

    private static void assertContentType(final String expected, final String messageLine) {
        assertEquals("Content-Type: " + expected, messageLine);
    }

    private static void assertMultipartContentTypeWithBoundary(final String expectedContentType, final String expectedMultipartBoundaryPrefix, final String messageLine) {
        final String contentTypeHeaderPrefix = "Content-Type: " + expectedContentType + "; boundary=\"" + expectedMultipartBoundaryPrefix;
        assertTrue(messageLine.startsWith(contentTypeHeaderPrefix));
    }

    private static void assertContentTransferEncoding(final String expected, final String messageLine) {
        assertEquals("Content-Transfer-Encoding: " + expected, messageLine);
    }

    private static void assertContentDescription(final String expected, final String messageLine) {
        assertEquals("Content-Description: " + expected, messageLine);
    }

    private static void assertContentDispositionAttachment(final String expected, final String messageLine) {
        assertEquals("Content-Disposition: attachment; filename=" + SendEmailFunction.parameterValue(expected), messageLine);
    }

    private static void assertBoundaryStart(final String multipartBoundaryPrefix, final String messageLine) {
        assertTrue(isBoundaryStart(multipartBoundaryPrefix, messageLine));
    }

    private static boolean isBoundaryStart(final String multipartBoundaryPrefix, final String messageLine) {
        return messageLine.startsWith("--" + multipartBoundaryPrefix)
                && !messageLine.endsWith("--");
    }

    private static boolean isBoundaryEnd(final String multipartBoundaryPrefix, final String messageLine) {
        return messageLine.startsWith("--" + multipartBoundaryPrefix)
                && messageLine.endsWith("--");
    }

    private static Part[] extractParts(final String messageLines[], int messageBodyOffset, final String multipartBoundaryPrefix) {
        assertEquals("", messageLines[messageBodyOffset++]); // body should start with an empty line
        return extractPartsFromPart(messageLines, messageBodyOffset, multipartBoundaryPrefix);
    }

    private static Part[] extractPartsFromPart(final String messageLines[], int messageBodyOffset, final String multipartBoundaryPrefix) {
        // should start with a multipartBoundaryStart
        assertBoundaryStart(multipartBoundaryPrefix, messageLines[messageBodyOffset++]);

        final List<Part> parts = new ArrayList<>();
        final List<String> headerLines = new ArrayList<>();
        final List<String> bodyLines = new ArrayList<>();

        List<String> list = headerLines;

        while (true) {
            final String line = messageLines[messageBodyOffset++];

            if ("".equals(line) && list == headerLines) {
                // switch from headers to body
                list = bodyLines;

            } else if (isBoundaryStart(multipartBoundaryPrefix, line) || isBoundaryEnd(multipartBoundaryPrefix, line)) {
                // add the parsed part to the parts
                parts.add(new Part(headerLines.toArray(new String[0]), bodyLines.toArray(new String[0])));

                if (isBoundaryEnd(multipartBoundaryPrefix, line)) {
                    break;
                } else {
                    // reset for the next part
                    headerLines.clear();
                    bodyLines.clear();
                    list = headerLines;
                }
            } else {
                list.add(line);
            }
        }

        return parts.toArray(new Part[0]);
    }

    private static String[] extractMessageBody(final String messageLines[], int messageBodyOffset) {
        assertEquals("", messageLines[messageBodyOffset++]); // body should start with an empty line

        final List<String> messageBodyLines = new ArrayList<>();
        String prevLine = null;
        while (true) {
            final String line = messageLines[messageBodyOffset++];
            if (".".equals(line) && "".equals(prevLine)) {
                // drop previous line
                messageBodyLines.removeLast();
                // exit loop, we have read the message body
                break;
            }
            messageBodyLines.add(line);
            prevLine = line;
        }

        return messageBodyLines.toArray(new String[0]);
    }

    record Part(String[] headerLines, String[] bodyLines) {
    }
}
