/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.webstart;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.evolvedbinary.j8fu.OptionalUtil.or;

/**
 * Dedicated servlet for Webstart.
 */
public class JnlpServlet extends HttpServlet {

    private static final long serialVersionUID = 1238966115449192258L;

    private static final Logger LOGGER = LogManager.getLogger(JnlpServlet.class);

    private JnlpJarFiles jf = null;

    /**
     * Initialize servlet.
     */
    @Override
    public void init() throws ServletException {
        LOGGER.info("Initializing JNLP servlet");

        final Optional<Path> libDir = or(
            Optional.ofNullable(System.getProperty("app.repo")).map(Paths::get).filter(Files::exists),
            () -> or (
                    Optional.ofNullable(System.getProperty("app.home")).map(Paths::get).map(p -> p.resolve("lib")).filter(Files::exists),
                    () -> or (
                            Optional.ofNullable(System.getProperty("exist.home")).map(Paths::get).map(p -> p.resolve("lib")).filter(Files::exists),
                            () -> Optional.ofNullable(getServletContext().getRealPath("/")).map(Paths::get).map(p -> p.resolve("lib")).filter(Files::exists)
                    )
            )
        );

        if (!libDir.isPresent()) {
            final String txt = "Could not locate lib directory. Webstart is not available.";
            LOGGER.error(txt);
            throw new ServletException(txt);
        } else {
            LOGGER.debug(String.format("jars location=%s", libDir.get().normalize().toAbsolutePath().toString()));
            jf = new JnlpJarFiles(libDir.get().normalize());
        }
    }

    private String stripFilename(String URI) {
        final int lastPos = URI.lastIndexOf('/');
        return URI.substring(lastPos + 1);
    }

    /**
     * Handle webstart request for JNLP file, jar file or image.
     *
     * @param request  Object representing http request.
     * @param response Object representing http response.
     * @throws ServletException Standard servlet exception
     * @throws IOException      Standard IO exception
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            final JnlpWriter jw = new JnlpWriter();

            final String requestURI = request.getRequestURI();
            final String filename = stripFilename(request.getPathInfo());
            LOGGER.debug("Requested URI=" + requestURI);

            if (requestURI.endsWith(".jnlp")) {
                jw.writeJnlpXML(jf, request, response);

            } else if (requestURI.endsWith(".jar") || requestURI.endsWith(".jar.pack.gz")) {
                jw.sendJar(jf, filename, request, response);

            } else if (requestURI.endsWith(".gif") || requestURI.endsWith(".jpg")) {
                jw.sendImage(jf, filename, response);

            } else {
                LOGGER.error("Invalid filename extension.");
                response.sendError(HttpServletResponse.SC_NOT_FOUND, filename + " not found.");
            }

        } catch (final EOFException | SocketException ex) {
            LOGGER.error(ex.getMessage());

        } catch (final Throwable e) {
            LOGGER.error(e);
            throw new ServletException("An error occurred: " + e.getMessage());
        }

    }

    @Override
    protected long getLastModified(final HttpServletRequest req) {
        try {
            return jf.getLastModified();
        } catch (final IOException e) {
            LOGGER.error(e);
            return -1l;
        }
    }
}