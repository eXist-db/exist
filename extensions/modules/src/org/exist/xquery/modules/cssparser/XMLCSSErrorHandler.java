package org.exist.xquery.modules.cssparser;

import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.ErrorHandler;

/**
 *
 * @author aretter
 */


public class XMLCSSErrorHandler implements ErrorHandler {

    @Override
    public void warning(CSSParseException exception) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void error(CSSParseException exception) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void fatalError(CSSParseException exception) throws CSSException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
