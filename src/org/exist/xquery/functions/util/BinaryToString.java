package org.exist.xquery.functions.util;

import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class BinaryToString extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(BinaryToString.class);

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("binary-to-string", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or the default of UTF-8.",
            new SequenceType[] {
                new FunctionParameterSequenceType("binary-resource", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary resource")
            },
            new FunctionParameterSequenceType("encoded-string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string containing the encoded binary resource")
        ),
        new FunctionSignature(
            new QName("binary-to-string", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or the default of UTF-8.",
            new SequenceType[] {
                new FunctionParameterSequenceType("binary-resource", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary resource"),
                new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "the encoding type.  i.e. 'UTF-8'")
            },
            new FunctionParameterSequenceType("encoded-string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string containing the encoded binary resource")
        ),
        new FunctionSignature(
            new QName("string-to-binary", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or the default of UTF-8.",
            new SequenceType[] {
                new FunctionParameterSequenceType("encoded-string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string containing the encoded binary resource")
            },
            new FunctionParameterSequenceType("binary-resource", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary resource")
        ),
        new FunctionSignature(
            new QName("string-to-binary", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or the default of UTF-8.",
            new SequenceType[] {
                new FunctionParameterSequenceType("encoded-string", Type.STRING, Cardinality.ZERO_OR_ONE, "The string containing the encoded binary resource"),
                new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.EXACTLY_ONE, "the encoding type.  i.e. 'UTF-8'")
            },
            new FunctionParameterSequenceType("binary-resource", Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary resource")
        )
    };
	
	public BinaryToString(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		String encoding = "UTF-8";
		if (args.length == 2)
			encoding = args[1].getStringValue();
        if (isCalledAs("binary-to-string")) {
            Base64Binary binary = (Base64Binary) args[0].itemAt(0);
            byte[] data = binary.getBinaryData();
            try {
                return new StringValue(new String(data, encoding));
            } catch (UnsupportedEncodingException e) {
                throw new XPathException(this, "Unsupported encoding: " + encoding);
            }
        } else {
            String str = args[0].getStringValue();
            try {
                byte[] data = str.getBytes(encoding);
                return new Base64Binary(data);
            } catch (UnsupportedEncodingException e) {
                throw new XPathException(this, "Unsupported encoding: " + encoding);
            }
        }
    }

}
