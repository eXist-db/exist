package org.exist.xquery;

import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Item;
import org.easymock.EasyMock;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.Test;
import static org.junit.Assert.assertNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.anyObject;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class DeferredFunctionCallTest {

    /**
     * resetState() make be called on the UserDefinedFunction of a DeferredFunctionCall
     * before the function is eval'd, this is because the evaluation is deferred!
     * resetState() clears the currentArguments to the function, however for a deferred
     * function call we must ensure that we still have these when eval() is called
     * otherwise we will get an NPE!
     * 
     * This test tries to prove that eval can be called after resetState without
     * causing problems for a DeferredFunctionCall
     *
     * The test implementation, due to the nature of the code under test, is rather horrible
     * and mostly consists of tightly coupled mocking code making it very brittle.
     * The interesting aspect of this test case is at the bottom of the function itself.
     */
    
    @Test
    public void ensure_argumentsToDeferredFunctionCall_AreNotLost_AfterReset_And_BeforeEval() throws XPathException {
        
        //mocks for FunctionCall constructor
        XQueryContext mockContext = EasyMock.createNiceMock(XQueryContext.class);
        
        //mocks for evalFunction()
        Sequence mockContextSequence = EasyMock.createMock(Sequence.class);
        Item mockContextItem = EasyMock.createMock(Item.class);
        Sequence[] mockSeq = { Sequence.EMPTY_SEQUENCE };
        int nextExpressionId = 1234;
        SequenceType[] mockArgumentTypes = { new SequenceType(Type.NODE, Cardinality.ZERO) };
        
        //mock for functionDef
        FunctionSignature mockFunctionSignature = EasyMock.createMock(FunctionSignature.class);
        SequenceType mockReturnType = EasyMock.createMock(SequenceType.class); 
        LocalVariable mockMark = EasyMock.createMock(LocalVariable.class);
        Expression mockExpression = EasyMock.createMock(Expression.class); 
        
        //expectations for UserDefinedFunction constructor
        expect(mockContext.nextExpressionId()).andReturn(nextExpressionId++);
        expect(mockExpression.simplify()).andReturn(mockExpression);
        
        //expectations for FunctionCall constructor
        expect(mockContext.nextExpressionId()).andReturn(nextExpressionId++);
        
        //expectations for FunctionCall.setFunction
        expect(mockFunctionSignature.getReturnType()).andReturn(mockReturnType);
        expect(mockReturnType.getCardinality()).andReturn(Cardinality.ZERO_OR_MORE);
        expect(mockReturnType.getPrimaryType()).andReturn(Type.NODE).times(4);
        expect(mockContext.nextExpressionId()).andReturn(nextExpressionId++);
        
        //expectations for functionCall.evalFunction
        expect(mockContext.isProfilingEnabled()).andReturn(false);

        //expectations for DeferredFunctionCallImpl.setup
        expect(mockFunctionSignature.getReturnType()).andReturn(mockReturnType);
        expect(mockReturnType.getCardinality()).andReturn(Cardinality.ZERO_OR_MORE);
        expect(mockReturnType.getPrimaryType()).andReturn(Type.NODE).times(4);
        expect(mockContext.nextExpressionId()).andReturn(nextExpressionId++);

        //expectations for DeferredFunctionCall.execute
        mockContext.pushDocumentContext();
        mockContext.functionStart(mockFunctionSignature);
        mockContext.stackEnter((Expression)anyObject());
        
        expect(mockContext.declareVariableBinding((LocalVariable)anyObject())).andReturn(null); 
        expect(mockFunctionSignature.getArgumentTypes()).andReturn(mockArgumentTypes); 
        
        expect(mockExpression.eval(mockContextSequence, mockContextItem)).andReturn(Sequence.EMPTY_SEQUENCE);

        mockExpression.resetState(true);

        mockContext.stackLeave((Expression)anyObject());
        mockContext.functionEnd();
        mockContext.popDocumentContext();
        
        
        
        replay(mockContext, mockFunctionSignature, mockReturnType, mockExpression);
        
        UserDefinedFunction userDefinedFunction = new UserDefinedFunction(mockContext, mockFunctionSignature);
        userDefinedFunction.addVariable("testParam");
        userDefinedFunction.setFunctionBody(mockExpression);
        
        FunctionCall functionCall = new FunctionCall(mockContext, userDefinedFunction);
        functionCall.setRecursive(true); //ensure DeferredFunction
        
        
        /*** this is the interesting bit ***/
        
        // 1) Call reset, this should set current arguments to null
        functionCall.resetState(true);
        functionCall.setRecursive(true); //ensure DeferredFunction
        
        // 2) check UserDefinedFunction.currentArguments == null
        assertNull(userDefinedFunction.getCurrentArguments());
        
        //so the currentArguments have been set to null, but deferredFunction should have its own copy
        
        // 3) Call functionCall.eval, if we dont get an NPE on reading currentArguments, then success :-)
        DeferredFunctionCall dfc = (DeferredFunctionCall)functionCall.evalFunction(mockContextSequence, mockContextItem, mockSeq);
        dfc.execute();
        
        /** end interesting bit ***/
        
        
        verify(mockContext, mockFunctionSignature, mockReturnType, mockExpression);
    }
    
}
