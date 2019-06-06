package org.exist.xquery.functions.fn;

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.util.Collations;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;

import static org.exist.xquery.FunctionDSL.*;

public class FunContainsToken extends BasicFunction {
    private static final QName FS_CONTAINS_TOKEN_NAME = new QName("contains-token", Function.BUILTIN_FUNCTION_NS);

    private final static FunctionParameterSequenceType FS_INPUT = optManyParam("input", Type.STRING, "The input string");
    private final static FunctionParameterSequenceType FS_TOKEN = param("token", Type.STRING, "The token to be searched for");
    private final static FunctionParameterSequenceType FS_COLLATION = optParam("pattern", Type.STRING, "Collation to use");

    public final static FunctionSignature FS_CONTAINS_TOKEN[] = functionSignatures(
            FS_CONTAINS_TOKEN_NAME,
            "Determines whether or not any of the supplied strings, when tokenized at whitespace boundaries, " +
                    "contains the supplied token, under the rules of the supplied collation.",
            returns(Type.BOOLEAN, "The function returns true if and only if there is string in $input which, " +
                    "after tokenizing at whitespace boundaries, contains a token that is equal to the trimmed value of $token " +
                    "under the rules of the selected collation."),
            arities(
                    arity(FS_INPUT, FS_TOKEN),
                    arity(FS_INPUT, FS_TOKEN, FS_COLLATION)
            )
    );


    public FunContainsToken(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        if (args[0].isEmpty()) {
            return BooleanValue.FALSE;
        }

        /* for all further processing trimmed value of the token is used */
        String token = StringValue.trimWhitespace(args[1].toString());

        if (token.isEmpty()) {
            return BooleanValue.FALSE;
        }

        /* if any of the input chunks (tokenized at whitespace) is equal to trimmed token then return true */

        ArrayList<String> fragments = new ArrayList<>();

        for (int i = 0; i < args[0].getItemCount(); i++) {
            String[] chunks = Option.tokenize(args[0].itemAt(i).getStringValue());
            for (String chunk : chunks) {
                fragments.add(chunk);
            }
        }

        Collator collator = context.getDefaultCollator();;

        if (args.length > 2 && !args[2].isEmpty()) {
            collator = context.getCollator(args[2].getStringValue());
        }

        //if (comparison == Constants.EQUAL)

        for (String fragment : fragments) {
            /* if fragment is equivalent to the token under chosen collation */
            //if (fragment.contains(token))
            int comparison = Collations.compare(collator, fragment, token);
            if (comparison == Constants.EQUAL) {
                return BooleanValue.TRUE;
            }
        }
        return BooleanValue.FALSE;
    }
}
