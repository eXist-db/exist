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
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.httpclient.config.RequestOptions;
import org.exist.xquery.modules.httpclient.config.HttpClientOptions;
import org.exist.xquery.modules.httpclient.config.ResponseOptions;
import org.exist.xquery.modules.httpclient.config.UserCredentials;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Builds a {@link java.net.http.HttpRequest} from an {@code <http:request>} element.
 *
 * <p>Parses child elements (http:header, http:body, http:multipart) from the request element,
 * and accepts an {@link RequestOptions} (produced by {@link RequestOptionsParser}) that carries
 * all attribute-level options. The caller invokes {@link #parse} then {@link #build}.</p>
 */
public class RequestBuilder {


    private static final String HTTP_NS = HttpClientModule.NAMESPACE_URI;

    private String method;
    private String href;
    private RequestOptions allOptions = new RequestOptions(HttpClientOptions.DEFAULTS, ResponseOptions.DEFAULTS, UserCredentials.DEFAULTS);

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
        method = getAttr(reqElem, "method");
        href = getAttr(reqElem, "href");
        allOptions = RequestOptionsParser.parse(reqElem);
        parseChildren(reqElem);
        applyOverrides(hrefParam, bodiesParam);
        validate();
        return this;
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

    /**
     * Builds the {@link java.net.http.HttpRequest} from the parsed parameters, sending credentials
     * preemptively only when {@code @send-authorization} is true.
     *
     * @return the built HttpRequest
     * @throws XPathException if the URI is invalid
     */
    public HttpRequest build() throws XPathException {
        return build(null);
    }

    /**
     * Builds the {@link java.net.http.HttpRequest} from the parsed parameters.
     *
     * @param authorizationHeader when non-null, attach this exact {@code Authorization} header
     *     value — used to answer a 401 challenge with a Basic or Digest response computed by
     *     {@link #challengeResponse(String)} (see {@link #shouldAttemptChallenge()}).
     * @return the built HttpRequest
     * @throws XPathException if the URI is invalid
     */
    public HttpRequest build(final String authorizationHeader) throws XPathException {
        final URI uri;
        try {
            uri = URI.create(href);
        } catch (final IllegalArgumentException e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "Invalid URI: " + href + ". " + e.getMessage());
        }

        final HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
        if (allOptions.requestOptions().timeout() > 0) {
            builder.timeout(Duration.ofSeconds(allOptions.requestOptions().timeout()));
        }

        final boolean isMultipart = !multipartBodies.isEmpty();
        applyHeaders(builder, isMultipart);
        applyAuthorization(builder, authorizationHeader);
        applyBody(builder, method.toUpperCase(), isMultipart);
        return builder.build();
    }

    private void applyHeaders(final HttpRequest.Builder builder, final boolean isMultipart) {
        // a Content-Type set here is ignored for multipart, where the publisher supplies a
        // Content-Type carrying the generated boundary
        for (final String[] header : headers) {
            if (isMultipart && "Content-Type".equalsIgnoreCase(header[0])) {
                continue;
            }
            builder.header(header[0], header[1]);
        }
    }

    /**
     * Per the EXPath spec, credentials are sent preemptively only when {@code @send-authorization}
     * is true; otherwise they are withheld until the server issues a 401 challenge, at which point
     * the request is re-sent with the {@code authorizationHeader} that {@link #challengeResponse}
     * computed for the offered scheme.
     */
    private void applyAuthorization(final HttpRequest.Builder builder, final String authorizationHeader) {
        final String authorization;
        if (authorizationHeader != null) {
            authorization = authorizationHeader;
        } else if (allOptions.userCredentials().sendAuthorization() && allOptions.userCredentials().username() != null && "basic".equalsIgnoreCase(allOptions.userCredentials().authMethod())) {
            authorization = "Basic " + base64Credentials();
        } else {
            authorization = null;
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
    }

    private void applyBody(final HttpRequest.Builder builder, final String upperMethod, final boolean isMultipart) {
        if (isMultipart) {
            applyMultipartBody(builder, upperMethod);
        } else if (bodyContent != null && !bodyContent.isEmpty()) {
            applySingleBody(builder, upperMethod);
        } else {
            builder.method(upperMethod, HttpRequest.BodyPublishers.noBody());
        }
    }

    /**
     * Multipart request body, built with Methanol's MultipartBodyPublisher (the JDK client has no
     * multipart support). Each http:body part becomes a part with its own Content-Type.
     */
    private void applyMultipartBody(final HttpRequest.Builder builder, final String upperMethod) {
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
    }

    private void applySingleBody(final HttpRequest.Builder builder, final String upperMethod) {
        final Charset charset = bodyMediaType != null
                ? Charset.forName(ContentTypeHelper.extractCharset(bodyMediaType))
                : StandardCharsets.UTF_8;
        final HttpRequest.BodyPublisher bodyPublisher =
                HttpRequest.BodyPublishers.ofString(bodyContent, charset);
        // Set Content-Type if not already set via headers
        if (bodyMediaType != null && headers.stream().noneMatch(h -> "Content-Type".equalsIgnoreCase(h[0]))) {
            builder.header("Content-Type", bodyMediaType);
        }
        builder.method(upperMethod, bodyPublisher);
    }

    /**
     * Whether a 401 response should be answered with credentials (EXPath challenge-response):
     * credentials and an auth method are present but were not sent preemptively (because
     * {@code @send-authorization} is not true). If they had been sent preemptively, a 401 means the
     * credentials are wrong and retrying would not help.
     *
     * @return true if the request should be re-sent with an {@code Authorization} header on a 401.
     */
    public boolean shouldAttemptChallenge() {
        final UserCredentials userCredentials = allOptions.userCredentials();
        return userCredentials.username() != null && !userCredentials.sendAuthorization() && userCredentials.authMethod() != null
                && ("basic".equalsIgnoreCase(userCredentials.authMethod()) || "digest".equalsIgnoreCase(userCredentials.authMethod()));
    }

    /**
     * Computes the {@code Authorization} header value answering a server's {@code WWW-Authenticate}
     * challenge, for the request's declared {@code @auth-method} (Basic or Digest). Mirrors the
     * EXPath reference behavior.
     *
     * @param wwwAuthenticate the server's {@code WWW-Authenticate} header value (may be null).
     * @return the {@code Authorization} header value, or null if the challenge cannot be answered
     *     (unsupported scheme, missing data, or a scheme mismatch with {@code @auth-method}).
     */
    public String challengeResponse(final String wwwAuthenticate) {
        final UserCredentials userCredentials = allOptions.userCredentials();
        if (userCredentials.username() == null || userCredentials.authMethod() == null) {
            return null;
        }
        if ("basic".equalsIgnoreCase(userCredentials.authMethod())) {
            return "Basic " + base64Credentials();
        }
        if ("digest".equalsIgnoreCase(userCredentials.authMethod())) {
            return digestResponse(wwwAuthenticate);
        }
        return null;
    }

    private String base64Credentials() {
        final UserCredentials userCredentials = allOptions.userCredentials();
        return Base64.getEncoder().encodeToString(
                (userCredentials.username() + ":" + (userCredentials.password() != null ? userCredentials.password() : "")).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds an RFC 2617 Digest {@code Authorization} header value from the server's challenge.
     */
    private String digestResponse(final String wwwAuthenticate) {
        if (wwwAuthenticate == null) {
            return null;
        }
        final String[] schemeAndParams = wwwAuthenticate.trim().split("\\s+", 2);
        if (schemeAndParams.length < 2 || !"digest".equalsIgnoreCase(schemeAndParams[0])) {
            return null;
        }
        final Map<String, String> p = parseAuthParams(schemeAndParams[1]);
        final String realm = p.get("realm");
        final String nonce = p.get("nonce");
        final String opaque = p.get("opaque");
        if (realm == null || nonce == null) {
            return null;
        }
        final String qop = resolveQop(p.get("qop"));
        final String pwd = allOptions.userCredentials().password() != null ? allOptions.userCredentials().password() : "";
        final String digestUri = href;
        final String ha1 = md5(allOptions.userCredentials().username() + ":" + realm + ":" + pwd);
        final String ha2 = md5(method.toUpperCase() + ":" + digestUri);

        final StringBuilder value = new StringBuilder()
                .append("username=\"").append(allOptions.userCredentials().username()).append("\", ")
                .append("realm=\"").append(realm).append("\", ")
                .append("nonce=\"").append(nonce).append("\", ")
                .append("uri=\"").append(digestUri).append('"');
        final String response;
        if (qop != null) {
            final String nc = "00000001";
            final String cnonce = md5(Long.toString(System.nanoTime()));
            response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
            value.append(", qop=").append(qop)
                    .append(", nc=").append(nc)
                    .append(", cnonce=\"").append(cnonce).append('"');
        } else {
            response = md5(ha1 + ":" + nonce + ":" + ha2);
        }
        value.append(", response=\"").append(response).append("\", algorithm=MD5");
        if (opaque != null) {
            value.append(", opaque=\"").append(opaque).append('"');
        }
        return "Digest " + value;
    }

    /**
     * Selects the quality-of-protection to use from a server's offered {@code qop} (which may be a
     * comma-separated list such as {@code "auth,auth-int"}); we implement {@code auth}. Returns null
     * for the legacy (no-qop) digest variant.
     */
    private static String resolveQop(final String offeredQop) {
        if (offeredQop != null) {
            for (final String q : offeredQop.split(",")) {
                if ("auth".equals(q.trim())) {
                    return "auth";
                }
            }
        }
        return null;
    }

    /**
     * Parses comma-separated {@code key=value} (optionally quoted) auth parameters into a map keyed
     * by lower-case name. Adequate for the realm/nonce/qop/opaque tokens used here.
     */
    private static Map<String, String> parseAuthParams(final String params) {
        final Map<String, String> map = new HashMap<>();
        for (final String part : params.split(",")) {
            final int eq = part.indexOf('=');
            if (eq > 0) {
                final String key = part.substring(0, eq).trim().toLowerCase();
                String val = part.substring(eq + 1).trim();
                if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                if (!key.isEmpty()) {
                    map.put(key, val);
                }
            }
        }
        return map;
    }

    // Digest authentication is defined over MD5 (RFC 2617); this is protocol-mandated, not a
    // security choice on our part.
    @SuppressWarnings("java:S4790")
    private static String md5(final String s) {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (final byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            // MD5 is required to be present on every Java platform
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    /**
     * Returns the parsed request options.
     * @return the request options
     */
    public RequestOptions getOptions() {
        return allOptions;
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
        final NodeList children = bodyElem.getChildNodes();
        boolean hasElements = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasElements = true;
                break;
            }
        }

        if (hasElements) {
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
