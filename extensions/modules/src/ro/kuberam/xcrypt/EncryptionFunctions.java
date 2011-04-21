/*
 *  eXist Java Cryptographic Extension
 *  Copyright (C) 2010 Claudius Teodorescu at http://kuberam.ro
 *
 *  Released under LGPL License - http://gnu.org/licenses/lgpl.html.
 *
 */

package ro.kuberam.xcrypt;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.StringValue;

/**
 * @author Claudius Teodorescu (claud108@yahoo.com)
 */

public class EncryptionFunctions extends BasicFunction {

    @SuppressWarnings("unused")
    private final static Logger logger = Logger.getLogger( EncryptionFunctions.class );

    public final static FunctionSignature signatures[] = {
                    new FunctionSignature(
                            new QName("encrypt-string", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
                            "Encrypts the input string.",
                            new SequenceType[] {
                                new FunctionParameterSequenceType("input-string", Type.STRING, Cardinality.EXACTLY_ONE, "The string to be encrypted."),
                                new FunctionParameterSequenceType("encryption-type", Type.STRING, Cardinality.EXACTLY_ONE, "The type of encryption. Legal values: 'symmetric', and 'asymmetric'."),
                                new FunctionParameterSequenceType("secret-key", Type.STRING, Cardinality.EXACTLY_ONE, "The secret key used for encryption, as string."),
                                new FunctionParameterSequenceType("cryptographic-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The cryptographic algorithm used for encryption.")
                            },
                            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the encrypted string.")
                    ),
                    new FunctionSignature(
                            new QName("decrypt-string", XcryptModule.NAMESPACE_URI, XcryptModule.PREFIX),
                            "Decrypts the input string.",
                            new SequenceType[] {
                                new FunctionParameterSequenceType("input-string", Type.STRING, Cardinality.EXACTLY_ONE, "The string to be decrypted."),
                                new FunctionParameterSequenceType("decryption-type", Type.STRING, Cardinality.EXACTLY_ONE, "The type of decryption. Legal values: 'symmetric', and 'asymmetric'."),
                                new FunctionParameterSequenceType("secret-key", Type.STRING, Cardinality.EXACTLY_ONE, "The secret key used for decryption, as string."),
                                new FunctionParameterSequenceType("cryptographic-algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The cryptographic algorithm used for decryption.")
                            },
                            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the decrypted string.")
                    )
            };

	public EncryptionFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
            String result = null;
            String functionName = getSignature().getName().getLocalName();

            if ("encrypt-string".equals(functionName)) {
                if ("symmetric".equals(args[1].getStringValue())) {
                    result = SymmetricEncryption.symmetricEncryption(args[0].getStringValue(), args[2].getStringValue(), args[3].getStringValue());
                } else if ("asymmetric".equals(args[1].getStringValue())) {
                    
                } else {
                    throw new XPathException( "Unknown encryption type!" );
                }
            } else if("decrypt-string".equals(functionName)) {
                if ("symmetric".equals(args[1].getStringValue())) {
                    result = SymmetricEncryption.symmetricDecryption(args[0].getStringValue(), args[2].getStringValue(), args[3].getStringValue());
                } else if ("asymmetric".equals(args[1].getStringValue())) {

                } else {
                    throw new XPathException( "Unknown decryption type!" );
                }
            }

            return new StringValue(result);
     }

}
