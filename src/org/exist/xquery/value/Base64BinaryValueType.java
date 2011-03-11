package org.exist.xquery.value;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.exist.util.io.Base64OutputStream;
import org.exist.xquery.XPathException;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class Base64BinaryValueType extends BinaryValueType<Base64OutputStream> {

    //private final static Pattern base64Pattern = Pattern.compile("^(?:[A-Za-z0-9+/\\s]{4})*(?:[A-Za-z0-9+/\\s]{2}==|[A-Za-z0-9+/\\s]{3}=)?$");
    //private final static Pattern base64Pattern = Pattern.compile("^((?:(?:\\s*[A-Za-z0-9+/]){4})*(?:(?:\\s*[A-Za-z0-9+/]){2}\\s*=\\s*=|(?:\\s*[A-Za-z0-9+/]){3}\\s*=)?)$");

    //private final static Pattern base64Pattern = Pattern.compile("^((?:(?:\\s*[A-Za-z0-9+/]){4})*(?:(?:\\s*[A-Za-z0-9+/]){1}(?:\\s*[AQgw]){1}\\s*=\\s*=|(?:\\s*[A-Za-z0-9+/]){3}\\s*=)?)$");
    private final static Pattern base64Pattern = Pattern.compile("^((?>(?>\\s*[A-Za-z0-9+/]){4})*(?>(?>\\s*[A-Za-z0-9+/]){1}(?>\\s*[AQgw]){1}\\s*=\\s*=|(?>\\s*[A-Za-z0-9+/]){3}\\s*=)?)$");

    private Matcher matcher;

    public Base64BinaryValueType() {
        super(Type.BASE64_BINARY, Base64OutputStream.class);
    }

    private Matcher getMatcher(String toMatch) {
        if(matcher == null) {
            matcher = base64Pattern.matcher(toMatch);
        } else {
            matcher = matcher.reset(toMatch);
        }
        return matcher;
    }

    @Override
    public void verifyString(String str) throws XPathException {
        if(!getMatcher(str).matches()) {
            throw new XPathException("FORG0001: Invalid base64 data");
        }
    }

    @Override
    protected String formatString(String str) {
        return str;
    }
}