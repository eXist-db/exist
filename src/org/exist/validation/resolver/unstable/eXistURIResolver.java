
package org.exist.validation.resolver.unstable;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

/**
 *
 * @author wessels
 */
public class eXistURIResolver implements URIResolver {

    public Source resolve(String href, String base) throws TransformerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
