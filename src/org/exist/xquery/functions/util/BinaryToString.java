package org.exist.xquery.functions.util;

import java.io.UnsupportedEncodingException;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class BinaryToString extends BasicFunction {

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("binary-to-string", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or UTF-8.",
            new SequenceType[] {
                new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("binary-to-string", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or UTF-8.",
            new SequenceType[] {
                new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("string-to-binary", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or UTF-8.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("string-to-binary", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Returns the contents of a binary resource as an xs:string value. The binary data " +
            "is transformed into a Java string using the encoding specified in the optional " +
            "second argument or UTF-8.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        )
    };
	
	public BinaryToString(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if (args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		String encoding = "UTF-8";
		if (args.length == 2)
			encoding = args[1].getStringValue();
        if (isCalledAs("binary-to-string")) {
            Base64Binary binary = (Base64Binary) args[0].itemAt(0);
            byte[] data = binary.getBinaryData();
            try {
                return new StringValue(new String(data, encoding));
            } catch (UnsupportedEncodingException e) {
                throw new XPathException(getASTNode(), "Unsupported encoding: " + encoding);
            }
        } else {
            String str = args[0].getStringValue();
            try {
                byte[] data = str.getBytes(encoding);
                return new Base64Binary(data);
            } catch (UnsupportedEncodingException e) {
                throw new XPathException(getASTNode(), "Unsupported encoding: " + encoding);
            }
        }
    }

}
