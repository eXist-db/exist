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

import java.io.InputStream;
import java.io.InputStreamReader;

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

    
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        return getKey().equals(((Source)obj).getKey());
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
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
    
    /**
     * Check if the XQuery file declares a content encoding in the
     * XQuery declaration.
     * 
     * @param is
     * @return The guessed encoding.
     */
    protected final static String guessXQueryEncoding(InputStream is) {
        XQueryLexer lexer = new XQueryLexer(null, new InputStreamReader(is));
        DeclScanner scanner = new DeclScanner(lexer);
        try {
            scanner.versionDecl();
        } catch (RecognitionException e) {
        } catch (TokenStreamException e) {
        } catch (XPathException e) {
        }
        return scanner.getEncoding();
    }
}
