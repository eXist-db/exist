/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *  
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *  
 *
 * $Id$
 */
package ro.kuberam.xcrypt;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import java.util.List;
import java.util.Map;
import org.exist.xquery.XPathException;
/**
 * Cryptographic module for eXist.
 *
 * @author Claudius Teodorescu (claud108@yahoo.com)
 */
public class XcryptModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://kuberam.ro/ns/x-crypt";
	public final static String PREFIX = "x-crypt";
    public final static String INCLUSION_DATE = "2010-12-17";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";

	public final static FunctionDef[] functions = {
		new FunctionDef(GenerateXMLSignatureFunction.signatures[0], GenerateXMLSignatureFunction.class),
                new FunctionDef(GenerateXMLSignatureFunction.signatures[1], GenerateXMLSignatureFunction.class),
                new FunctionDef(GenerateXMLSignatureFunction.signatures[2], GenerateXMLSignatureFunction.class),
                new FunctionDef(GenerateXMLSignatureFunction.signatures[3], GenerateXMLSignatureFunction.class),
                new FunctionDef(ValidateSignatureFunction.signature, ValidateSignatureFunction.class),
                new FunctionDef(EncryptionFunctions.signatures[0], EncryptionFunctions.class),
                new FunctionDef(EncryptionFunctions.signatures[1], EncryptionFunctions.class),
                new FunctionDef(HmacFunctions.signatures[0], HmacFunctions.class),
                new FunctionDef(HmacFunctions.signatures[1], HmacFunctions.class)
	};
	
	public XcryptModule(Map<String, List<? extends Object>> parameters) throws XPathException {
		super(functions, parameters);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module with cryptographic functions.";
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
