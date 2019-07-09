/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import org.exist.dom.QName;
import org.exist.xquery.XPathException;
import org.exist.xquery.parser.DeclScanner;
import org.exist.xquery.parser.XQueryLexer;

import antlr.RecognitionException;
import antlr.TokenStreamException;


/**
 * @author wolf
 */
public abstract class AbstractSource implements Source {

    private long cacheTime = 0;

    @Override
    public Charset getEncoding() throws IOException {
        return null;
    }

    /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
    @Override
    public boolean equals(Object obj) {
    	if (obj != null && obj instanceof Source) {
            return getKey().equals(((Source)obj).getKey());
		}
    	return false;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getKey().hashCode();
    }
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#getCacheTimestamp()
     */
    public long getCacheTimestamp() {
        return cacheTime;
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.source.Source#setCacheTimestamp(long)
     */
    public void setCacheTimestamp(long timestamp) {
        cacheTime = timestamp;
    }

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
    protected final static String guessXQueryEncoding(InputStream is) {
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
    protected final static QName getModuleDecl(InputStream is) {
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
}
