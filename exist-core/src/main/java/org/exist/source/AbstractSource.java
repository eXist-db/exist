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
package org.exist.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.xquery.XPathException;
import org.exist.xquery.parser.DeclScanner;
import org.exist.xquery.parser.XQueryLexer;

import antlr.RecognitionException;
import antlr.TokenStreamException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author wolf
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractSource implements Source {

    private final long key;

    protected AbstractSource(final long key) {
        this.key = key;
    }

    @Override
    public long getKey() {
        return key;
    }

    @Override
    public Charset getEncoding() throws IOException {
        return null;
    }

    @Deprecated
    public void validate(final Subject subject, final int perm) throws PermissionDeniedException {
        // no-op
    }

    @Override
    public boolean equals(final Object obj) {
    	if (obj != null && obj instanceof Source) {
            return key == (((Source)obj).getKey());
		}
    	return false;
    }

    public abstract int hashCode();

    @Override
    public QName isModule() throws IOException {
        return null;
    }

    /**
     * Check if the XQuery file declares a content encoding in the
     * XQuery declaration.
     *
     * @param is the input stream
     * @return The guessed encoding.
     */
    protected static String guessXQueryEncoding(final InputStream is) {
        final XQueryLexer lexer = new XQueryLexer(null, new InputStreamReader(is));
        final DeclScanner scanner = new DeclScanner(lexer);
        try {
            scanner.versionDecl();
        } catch (final RecognitionException | XPathException | TokenStreamException e) {
            //Nothing to do
        }
        return scanner.getEncoding();
    }

    /**
     * Check if the source is an XQuery module. If yes, return a QName containing
     * the module prefix as local name and the module namespace as namespace URI.
     *
     * @param is the input stream
     * @return QName describing the module namespace or null if the source is not
     * a module.
     */
    protected static QName getModuleDecl(final InputStream is) {
        final XQueryLexer lexer = new XQueryLexer(null, new InputStreamReader(is));
        final DeclScanner scanner = new DeclScanner(lexer);
        try {
            scanner.versionDecl();
        } catch (final RecognitionException | XPathException | TokenStreamException e) {
            //Nothing to do
        }
        if (scanner.getNamespace() != null) {
            return new QName(scanner.getPrefix(), scanner.getNamespace());
        }
        return null;
    }

    @Override
    public String shortIdentifier() {
        return type() + "/" + key;
    }

    @Override
    public String pathOrShortIdentifier() {
        String str = path();
        if (str == null) {
            str = shortIdentifier();
        }
        return str;
    }

    @Override
    public String pathOrContentOrShortIdentifier() {
        String str = path();
        if (str == null) {
            try {
                str = getContent();
            } catch (final IOException e) {
                str = shortIdentifier();
            }
        }
        return str;
    }

    protected static final long XXHASH64_SEED = 0x79742bc8;
    protected static long hashKey(final String key) {
        return hashKey(key.getBytes(UTF_8));
    }
    protected static long hashKey(final byte[] key) {
        final XXHash64 xxHash64 = XXHashFactory.fastestInstance().hash64();
        return xxHash64.hash(key, 0, key.length, XXHASH64_SEED);
    }
}
