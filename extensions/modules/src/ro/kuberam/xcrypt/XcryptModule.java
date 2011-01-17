/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *  
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *  
 */
package ro.kuberam.xcrypt;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * Cryptographic module for eXist.
 *
 * @author Claudius Teodorescu (claud108@yahoo.com)
 */
public class XcryptModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://kuberam.ro/x-crypt";
	public final static String PREFIX = "x-crypt";
    	public final static String INCLUSION_DATE = "2010-12-17";
    	public final static String RELEASED_IN_VERSION = "eXist-1.5";

	public final static FunctionDef[] functions = {
		new FunctionDef(GenerateSignatureFunction.signatures[0], GenerateSignatureFunction.class),
                new FunctionDef(GenerateSignatureFunction.signatures[1], GenerateSignatureFunction.class),
                new FunctionDef(GenerateSignatureFunction.signatures[2], GenerateSignatureFunction.class),
                new FunctionDef(GenerateSignatureFunction.signatures[3], GenerateSignatureFunction.class),
                new FunctionDef(ValidateSignatureFunction.signature, ValidateSignatureFunction.class)
	};
	
	public XcryptModule() throws XPathException {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module comprising cryptographic functions.";
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
