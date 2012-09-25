package org.exist.xquery.xqdoc;

import antlr.RecognitionException;
import antlr.TokenStreamException;
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
        String desc = signature.getDescription();
        if (desc == null || !desc.startsWith("(:")) {
            return;
        }
        XQDocLexer lexer = new XQDocLexer(new StringReader(desc));
        XQDocParser parser = new XQDocParser(lexer);
        XQDocHelper helper = new XQDocHelper();
        try {
            parser.xqdocComment(helper);
            helper.enhance(signature);
        } catch (RecognitionException e) {
            // ignore: comment will be shown unparsed
        } catch (TokenStreamException e) {
            // ignore: comment will be shown unparsed
        }
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
        String components[] = comment.trim().split("\\s+", 2);
        if(components != null && components.length == 2) {
            String var = components[0];
            if (var.length() > 0 && var.charAt(0) == '$')
                var = var.substring(1);
            parameters.put(var, components[1]);
        }
    }

    public void setTag(String tag, String content) {
        if (tag.equals("@param")) {
            setParameter(content);
        } else if (tag.equals("@return")) {
            returnValue = content;
        } else {
            meta.put(tag, content);
        }
    }

    protected void enhance(FunctionSignature signature) {
        signature.setDescription(description.toString());
        if (returnValue != null) {
            SequenceType returnType = signature.getReturnType();
            FunctionReturnSequenceType newType =
                    new FunctionReturnSequenceType(returnType.getPrimaryType(), returnType.getCardinality(), returnValue);
            signature.setReturnType(newType);
        }
        SequenceType[] args = signature.getArgumentTypes();
        for (SequenceType type : args) {
            if (type instanceof FunctionParameterSequenceType) {
                FunctionParameterSequenceType argType = (FunctionParameterSequenceType)type;
                String desc = parameters.get(argType.getAttributeName());
                if (desc != null)
                    argType.setDescription(desc);
            }
        }
        for (Map.Entry<String, String> entry: meta.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 1 && key.charAt(0) == '@')
                key = key.substring(1);
            signature.addMetadata(key, entry.getValue());
        }
    }

    public String toString() {
        StringBuilder out = new StringBuilder();
        out.append(description).append("\n\n");
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            out.append(String.format("%20s\t%s\n", entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            out.append(String.format("%20s\t%s\n", entry.getKey(), entry.getValue()));
        }
        return out.toString();
    }
}
