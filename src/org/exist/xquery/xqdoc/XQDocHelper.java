package org.exist.xquery.xqdoc;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.xqdoc.parser.XQDocLexer;
import org.exist.xquery.xqdoc.parser.XQDocParser;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper for parsing XQDoc comments on function declarations. XQDoc comments
 * are stored in the function signature but not parsed until one of the
 * inspection functions accesses them.
 */
public class XQDocHelper {

    public static void parse(FunctionSignature signature) {
        final String desc = signature.getDescription();
        if (desc == null || !desc.startsWith("(:")) {
            return;
        }
        final XQDocHelper helper = parseComment(desc);
        if (helper == null)
            {return;}
        helper.enhance(signature);
    }

    public static void parse(ExternalModule module) {
        final String desc = module.getDescription();
        if (desc == null || !desc.startsWith("(:")) {
            return;
        }
        final XQDocHelper helper = parseComment(desc);
        if (helper == null)
            {return;}
        helper.enhance(module);
    }

    private static XQDocHelper parseComment(String desc) {
        final XQDocLexer lexer = new XQDocLexer(new StringReader(desc));
        final XQDocParser parser = new XQDocParser(lexer);
        try {
            final XQDocHelper helper = new XQDocHelper();
            parser.xqdocComment(helper);
            return helper;
        } catch (final RecognitionException e) {
            // ignore: comment will be shown unparsed
        } catch (final TokenStreamException e) {
            // ignore: comment will be shown unparsed
        }
        return null;
    }

    private StringBuilder description = new StringBuilder();
    private Map<String, String> parameters = new HashMap<String, String>();
    private String returnValue = null;
    private Map<String, String> meta = new HashMap<String, String>();

    public XQDocHelper() {
    }

    public void addDescription(CharSequence part) {
        description.append(part);
    }

    public void setParameter(String comment) {
        final String components[] = comment.trim().split("\\s+", 2);
        if(components != null && components.length == 2) {
            String var = components[0];
            if (var.length() > 0 && var.charAt(0) == '$')
                {var = var.substring(1);}
            parameters.put(var, components[1]);
        }
    }

    public void setTag(String tag, String content) {
        if ("@param".equals(tag)) {
            setParameter(content);
        } else if ("@return".equals(tag)) {
            returnValue = content;
        } else {
            meta.put(tag, content);
        }
    }

    protected void enhance(FunctionSignature signature) {
        signature.setDescription(description.toString());
        if (returnValue != null) {
            final SequenceType returnType = signature.getReturnType();
            final FunctionReturnSequenceType newType =
                    new FunctionReturnSequenceType(returnType.getPrimaryType(), returnType.getCardinality(), returnValue);
            signature.setReturnType(newType);
        }
        final SequenceType[] args = signature.getArgumentTypes();
        for (final SequenceType type : args) {
            if (type instanceof FunctionParameterSequenceType) {
                final FunctionParameterSequenceType argType = (FunctionParameterSequenceType)type;
                final String desc = parameters.get(argType.getAttributeName());
                if (desc != null)
                    {argType.setDescription(desc);}
            }
        }
        for (final Map.Entry<String, String> entry: meta.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 1 && key.charAt(0) == '@')
                {key = key.substring(1);}
            signature.addMetadata(key, entry.getValue());
        }
    }

    protected void enhance(ExternalModule module) {
        module.setDescription(description.toString());
        for (final Map.Entry<String, String> entry: meta.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 1 && key.charAt(0) == '@')
                {key = key.substring(1);}
            module.addMetadata(key, entry.getValue());
        }
    }

    public String toString() {
        final StringBuilder out = new StringBuilder();
        out.append(description).append("\n\n");
        for (final Map.Entry<String, String> entry : meta.entrySet()) {
            out.append(String.format("%20s\t%s\n", entry.getKey(), entry.getValue()));
        }
        for (final Map.Entry<String, String> entry : parameters.entrySet()) {
            out.append(String.format("%20s\t%s\n", entry.getKey(), entry.getValue()));
        }
        return out.toString();
    }
}
