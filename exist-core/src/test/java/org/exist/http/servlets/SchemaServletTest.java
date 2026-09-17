/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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
package org.exist.http.servlets;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Mocked-request unit test: {@link SchemaServlet} does plain filesystem I/O with no
 * eXist-specific request context, so a real Jetty pipeline isn't needed to exercise it
 * (see CLAUDE.md's XQSuite-vs-Java testing guidance — lightest vehicle for the behavior).
 */
class SchemaServletTest {

    private String previousExistHome;

    @BeforeEach
    void saveExistHomeProperty() {
        previousExistHome = System.getProperty("exist.home");
    }

    @AfterEach
    void restoreExistHomeProperty() {
        if (previousExistHome == null) {
            System.clearProperty("exist.home");
        } else {
            System.setProperty("exist.home", previousExistHome);
        }
    }

    private static class CapturingOutputStream extends ServletOutputStream {
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        @Override
        public void write(final int b) {
            captured.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(final WriteListener writeListener) {
            // synchronous test double; nothing to register
        }
    }

    @Test
    void servesAnExistingSchemaFile(@TempDir final Path existHomeDir) throws Exception {
        final Path schemaDir = Files.createDirectory(existHomeDir.resolve("schema"));
        Files.writeString(schemaDir.resolve("conf.xsd"), "<xs:schema/>", StandardCharsets.UTF_8);
        System.setProperty("exist.home", existHomeDir.toString());

        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathInfo()).andReturn("/conf.xsd");
        replay(request);

        final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
        final CapturingOutputStream out = new CapturingOutputStream();
        expect(response.getOutputStream()).andReturn(out);
        replay(response);

        new SchemaServlet().doGet(request, response);

        assertThat(out.captured.toString(StandardCharsets.UTF_8)).isEqualTo("<xs:schema/>");
    }

    @Test
    void unknownFileReturns404(@TempDir final Path existHomeDir) throws Exception {
        Files.createDirectory(existHomeDir.resolve("schema"));
        System.setProperty("exist.home", existHomeDir.toString());

        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathInfo()).andReturn("/does-not-exist.xsd");
        replay(request);

        final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        replay(response);

        new SchemaServlet().doGet(request, response);

        assertThatCode(() -> verify(response)).doesNotThrowAnyException();
    }

    @Test
    void pathTraversalAttemptReturns404(@TempDir final Path existHomeDir) throws Exception {
        final Path schemaDir = Files.createDirectory(existHomeDir.resolve("schema"));
        Files.writeString(schemaDir.resolve("conf.xsd"), "<xs:schema/>", StandardCharsets.UTF_8);
        Files.writeString(existHomeDir.resolve("secret.xsd"), "<xs:schema/>", StandardCharsets.UTF_8);
        System.setProperty("exist.home", existHomeDir.toString());

        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathInfo()).andReturn("/../secret.xsd");
        replay(request);

        final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        replay(response);

        new SchemaServlet().doGet(request, response);

        assertThatCode(() -> verify(response)).doesNotThrowAnyException();
    }

    @Test
    void nonXsdExtensionReturns404(@TempDir final Path existHomeDir) throws Exception {
        Files.createDirectory(existHomeDir.resolve("schema"));
        System.setProperty("exist.home", existHomeDir.toString());

        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathInfo()).andReturn("/conf.xml");
        replay(request);

        final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        replay(response);

        new SchemaServlet().doGet(request, response);

        assertThatCode(() -> verify(response)).doesNotThrowAnyException();
    }
}
