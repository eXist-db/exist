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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for the reflected-XSS issue in {@link XQueryServlet}'s
 * HTML error page. Both the message and the description are written into an
 * <code>html</code> response; the description in particular carries
 * user-derived values (a requested source path), so both must be HTML-escaped
 * before they appear in the page.
 */
public class XQueryServletErrorPageTest {

    @Test
    public void scriptInDescriptionIsEscaped() {
        final String page = XQueryServlet.renderErrorPage("Error",
                "</pre><script>alert(1)</script>", false);

        assertFalse(page.contains("</pre><script>"),
                "raw </pre> tag must not survive");
        assertFalse(page.contains("<script>"),
                "a raw <script> tag must not survive");
        assertTrue(page.contains("&lt;script&gt;"),
                "the escaped form of <script> must be present");
     }

    @Test
    public void scriptInMessageIsEscaped() {
        final String page = XQueryServlet.renderErrorPage("<img src=x onerror=alert(1)>",
                "benign", false);

        assertFalse(page.contains("<img src=x onerror=alert(1)>"),
                "the injected markup must not survive");
        assertTrue(page.contains("&lt;img src=x onerror=alert(1)&gt;"));
     }

    @Test
    public void descriptionIsHiddenWhenConfigured() {
        final String page = XQueryServlet.renderErrorPage("Error",
                "SECRET-DESCRIPTION", true);

        assertFalse(page.contains("SECRET-DESCRIPTION"),
                "a hidden description must not be emitted");
     }

    @Test
    public void benignMessagePassesThroughUnchanged() {
        final String page = XQueryServlet.renderErrorPage("Source not found",
                "benign", false);

        assertTrue(page.contains("Source not found"));
        assertTrue(page.contains("benign"));
     }

    @Test
    public void nullValuesDoNotThrow() {
        final String page = XQueryServlet.renderErrorPage(null, null, false);

        assertFalse(page.contains("<b>Message: </b>null"),
                "a null message must not render the literal text 'null' unescaped");
        assertTrue(page.contains("</body></html>"));
     }
}
