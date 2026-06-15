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
package org.exist.xquery.modules.httpclient;

import com.github.mizosoft.methanol.MediaType;
import com.github.mizosoft.methanol.MultipartBodyPublisher;
import org.exist.dom.QName;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Builds a {@link java.net.http.HttpRequest} from an {@code <http:request>} element.
 *
 * <p>Parses attributes (method, href, timeout, follow-redirect, auth, etc.)
 * and child elements (http:header, http:body, http:multipart) from the request
 * element.</p>
 */
public class RequestBuilder {

    private static final String HTTP_NS = HttpClientModule.NAMESPACE_URI;

    private String method;
    private String href;
    private int timeout = 0;
    private boolean followRedirect = true;
    private boolean statusOnly = false;
    private String overrideMediaType;
    private String username;
    private String password;
    private String authMethod;
    private boolean sendAuthorization = false;

    private final List<String[]> headers = new ArrayList<>();
    private String bodyMediaType;
    private String bodyContent;
    private String multipartMediaType;
    private final List<BodyPart> multipartBodies = new ArrayList<>();

    /**
     * Parses the {@code <http:request>} element and optional function parameters.
     *
     * @param requestNode the http:request element (may be null for 2/3-arg form)
     * @param hrefParam   optional href parameter (overrides attribute)
     * @param bodiesParam optional bodies parameter (overrides body children)
     * @return this builder
     * @throws XPathException if the request element is invalid
     */
    public RequestBuilder parse(final NodeValue requestNode, final String hrefParam,
                                 final Sequence bodiesParam) throws XPathException {
        if (requestNode == null) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "http:request element is required");
        }

        final Element reqElem = (Element) requestNode.getNode();
        parseAttributes(reqElem);
        parseChildren(reqElem);
        applyOverrides(hrefParam, bodiesParam);
        validate();
        return this;
    }

    private void parseAttributes(final Element reqElem) throws XPathException {
        method = getAttr(reqElem, "method");
        href = getAttr(reqElem, "href");
        timeout = parseTimeout(getAttr(reqElem, "timeout"));
        followRedirect = parseBooleanAttr(reqElem, "follow-redirect", followRedirect);
        statusOnly = parseBooleanAttr(reqElem, "status-only", statusOnly);
        overrideMediaType = getAttr(reqElem, "override-media-type");
        username = getAttr(reqElem, "username");
        password = getAttr(reqElem, "password");
        authMethod = getAttr(reqElem, "auth-method");
        sendAuthorization = parseBooleanAttr(reqElem, "send-authorization", sendAuthorization);
    }

    private int parseTimeout(final String timeoutStr) throws XPathException {
        if (timeoutStr == null || timeoutStr.isEmpty()) {
            return timeout;
        }
        try {
            return Integer.parseInt(timeoutStr);
        } catch (final NumberFormatException e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "Invalid timeout value: " + timeoutStr);
        }
    }

    private void parseChildren(final Element reqElem) {
        final NodeList children = reqElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !HTTP_NS.equals(child.getNamespaceURI())) {
                continue;
            }
            final String localName = child.getLocalName();
            if ("header".equals(localName)) {
                addHeader((Element) child);
            } else if ("body".equals(localName)) {
                final Element bodyElem = (Element) child;
                bodyMediaType = bodyElem.getAttribute("media-type");
                // Body content is the text/XML content of the body element
                bodyContent = getBodyContent(bodyElem);
            } else if ("multipart".equals(localName)) {
                final Element multipartElem = (Element) child;
                multipartMediaType = getAttr(multipartElem, "media-type");
                parseMultipart(multipartElem);
            }
        }
    }

    private void addHeader(final Element headerElem) {
        final String name = headerElem.getAttribute("name");
        final String value = headerElem.getAttribute("value");
        if (name != null && !name.isEmpty()) {
            headers.add(new String[]{name, value != null ? value : ""});
        }
    }

    private void applyOverrides(final String hrefParam, final Sequence bodiesParam) throws XPathException {
        if (hrefParam != null && !hrefParam.isEmpty()) {
            href = hrefParam;
        }

        // External bodies override inline body
        if (bodiesParam != null && !bodiesParam.isEmpty()) {
            if (bodyMediaType == null) {
                bodyMediaType = "text/plain";
            }
            bodyContent = bodiesParam.itemAt(0).getStringValue();
        }
    }

    private void validate() throws XPathException {
        if (method == null || method.isEmpty()) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "method attribute is required on http:request");
        }
        if (href == null || href.isEmpty()) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "No URI specified: provide href attribute or $href parameter");
        }
    }

    private static boolean parseBooleanAttr(final Element elem, final String name, final boolean defaultValue) {
        final String value = getAttr(elem, name);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    /**
     * Builds the {@link java.net.http.HttpRequest} from the parsed parameters.
     */
    public HttpRequest build() throws XPathException {
        final URI uri;
        try {
            uri = URI.create(href);
        } catch (IllegalArgumentException e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "Invalid URI: " + href + ". " + e.getMessage());
        }

        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);

        // Timeout
        if (timeout > 0) {
            builder.timeout(Duration.ofSeconds(timeout));
        }

        // Headers (a Content-Type set here is ignored for multipart, where the publisher supplies
        // a Content-Type carrying the generated boundary)
        final boolean isMultipart = !multipartBodies.isEmpty();
        for (final String[] header : headers) {
            if (isMultipart && "Content-Type".equalsIgnoreCase(header[0])) {
                continue;
            }
            builder.header(header[0], header[1]);
        }

        // Authentication
        if (username != null && sendAuthorization && "basic".equalsIgnoreCase(authMethod)) {
            final String encoded = Base64.getEncoder().encodeToString(
                    (username + ":" + (password != null ? password : ""))
                            .getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }

        final String upperMethod = method.toUpperCase();

        // Multipart request body, built with Methanol's MultipartBodyPublisher (the JDK client has
        // no multipart support). Each http:body part becomes a part with its own Content-Type.
        if (isMultipart) {
            final MultipartBodyPublisher.Builder multipart = MultipartBodyPublisher.newBuilder();
            if (multipartMediaType != null && !multipartMediaType.isEmpty()) {
                multipart.mediaType(MediaType.parse(multipartMediaType));
            }
            for (final BodyPart part : multipartBodies) {
                final HttpRequest.BodyPublisher partBody =
                        HttpRequest.BodyPublishers.ofString(part.content(), StandardCharsets.UTF_8);
                final Map<String, List<String>> partHeaders = part.mediaType() != null && !part.mediaType().isEmpty()
                        ? Map.of("Content-Type", List.of(part.mediaType()))
                        : Map.of();
                multipart.part(MultipartBodyPublisher.Part.create(
                        HttpHeaders.of(partHeaders, (k, v) -> true), partBody));
            }
            final MultipartBodyPublisher publisher = multipart.build();
            builder.header("Content-Type", publisher.mediaType().toString());
            builder.method(upperMethod, publisher);
            return builder.build();
        }

        // Single body
        if (bodyContent != null && !bodyContent.isEmpty()) {
            final Charset charset = bodyMediaType != null
                    ? Charset.forName(ContentTypeHelper.extractCharset(bodyMediaType))
                    : StandardCharsets.UTF_8;
            final HttpRequest.BodyPublisher bodyPublisher =
                    HttpRequest.BodyPublishers.ofString(bodyContent, charset);

            // Set Content-Type if not already set via headers
            if (bodyMediaType != null && headers.stream().noneMatch(
                    h -> "Content-Type".equalsIgnoreCase(h[0]))) {
                builder.header("Content-Type", bodyMediaType);
            }

            builder.method(upperMethod, bodyPublisher);
        } else {
            builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    public boolean isFollowRedirect() {
        return followRedirect;
    }

    public boolean isStatusOnly() {
        return statusOnly;
    }

    public String getOverrideMediaType() {
        return overrideMediaType;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public int getTimeout() {
        return timeout;
    }

    private void parseMultipart(final Element multipartElem) {
        final NodeList children = multipartElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && HTTP_NS.equals(child.getNamespaceURI())
                    && "body".equals(child.getLocalName())) {
                final Element bodyElem = (Element) child;
                final String mediaType = bodyElem.getAttribute("media-type");
                final String content = getBodyContent(bodyElem);
                multipartBodies.add(new BodyPart(mediaType, content));
            }
        }
    }

    private String getBodyContent(final Element bodyElem) {
        // Check for child elements (XML content)
        final NodeList children = bodyElem.getChildNodes();
        boolean hasElements = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElements = true;
                break;
            }
        }

        if (hasElements) {
            // Serialize XML child content
            try {
                final StringBuilder sb = new StringBuilder();
                final TransformerFactory tf = TransformerFactory.newInstance();
                final Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                for (int i = 0; i < children.getLength(); i++) {
                    final Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        final StringWriter sw = new StringWriter();
                        transformer.transform(new DOMSource(child), new StreamResult(sw));
                        sb.append(sw);
                    }
                }
                return sb.toString();
            } catch (Exception e) {
                // Fall through to text content
            }
        }

        return bodyElem.getTextContent();
    }

    private static String getAttr(final Element elem, final String name) {
        final String val = elem.getAttribute(name);
        return val != null && !val.isEmpty() ? val : null;
    }

    /**
     * Represents a body part in a multipart request.
     */
    record BodyPart(String mediaType, String content) {}
}
