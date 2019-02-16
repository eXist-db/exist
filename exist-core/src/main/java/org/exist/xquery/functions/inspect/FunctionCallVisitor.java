package org.exist.xquery.functions.inspect;

import org.exist.xquery.DefaultExpressionVisitor;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;

import java.util.*;

public class FunctionCallVisitor extends DefaultExpressionVisitor {

    private Set<FunctionSignature> functionCalls = new HashSet<FunctionSignature>();

    public Set<FunctionSignature> getFunctionCalls() {
        return functionCalls;
    }

    @Override
    public void visitFunctionCall(FunctionCall call) {
        functionCalls.add(call.getSignature());
        // continue with the function arguments, but skip the body:
        // we're not interested in following function calls recursively
        for(int i = 0; i < call.getArgumentCount(); i++) {
            call.getArgument(i).accept(this);
        }
    }
}
