package org.exist.storage.md.xquery;

import java.util.List;
import java.util.Map;

import org.exist.storage.md.Plugin;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

public class MetadataModule extends AbstractInternalModule 
{
    public final static String INCLUSION_DATE = "2012-04-01";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";

	public static final FunctionDef[] functions = {
		new FunctionDef( Create.signature, Create.class )
	};
	
	public MetadataModule(Map<String, List<? extends Object>> parameters) throws XPathException {
		super( functions, parameters );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return ""; 
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return Plugin.NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return Plugin.PREFIX;
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}