package org.exist.xquery.parser;

import antlr.collections.AST;

/**
 * AST for XQuery function declarations. Preserves XQDoc comments.
 */
public class XQueryFunctionAST extends XQueryAST {

    private String doc = null;

    public XQueryFunctionAST() {
        super();
    }

    public XQueryFunctionAST(int type, String text) {
        super(type, text);
    }

    public XQueryFunctionAST(AST ast) {
        super(ast);
    }

    @Override
    public void setDoc(String xqdoc) {
        this.doc = xqdoc;
    }

    @Override
    public String getDoc() {
        return doc;
    }
}
