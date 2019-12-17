package org.exist.xquery;

import com.evolvedbinary.j8fu.Either;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import static com.evolvedbinary.j8fu.Either.Left;
import static com.evolvedbinary.j8fu.Either.Right;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContextItemTests {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    public void staticFunctionNoContextItem() throws EXistException, PermissionDeniedException {
        final String query =
                "declare function local:x() {" +
                "  //x" +
                "};" +
                "local:x()";

        final Either<XPathException, Sequence> result = executeQuery(query);
        assertXQueryError(ErrorCodes.XPDY0002, result);
    }

    @Test
    public void staticFunctionNoInheritContextItem() throws EXistException, PermissionDeniedException {
        final String query =
                "declare function local:x() {" +
                "  //x" +
                "};" +
                "<doc><x>1</x><x>2</x></doc>/local:x()";

        final Either<XPathException, Sequence> result = executeQuery(query);
        assertXQueryError(ErrorCodes.XPDY0002, result);
    }

    @Test
    public void dynamicFunctionNoContextItem() throws EXistException, PermissionDeniedException {
        final String query =
                "declare function local:x() {" +
                "  //x" +
                "};" +
                "let $ref := local:x#0 return" +
                "  $ref()";

        final Either<XPathException, Sequence> result = executeQuery(query);
        assertXQueryError(ErrorCodes.XPDY0002, result);
    }

    @Test
    public void dynamicFunctionNoInheritContextItem() throws EXistException, PermissionDeniedException {
        final String query =
                "declare function local:x() {" +
                "  //x" +
                "};" +
                "let $ref := local:x#0 return" +
                "  <doc><x>1</x><x>2</x></doc>/$ref()";

        final Either<XPathException, Sequence> result = executeQuery(query);
        assertXQueryError(ErrorCodes.XPDY0002, result);
    }

    private static void assertXQueryError(final ErrorCodes.ErrorCode expected, final Either<XPathException, Sequence> actual) {
        assertTrue("Expected: " + expected.getErrorQName() + ", but got result: " + actual.toString(), actual.isLeft());
        final XPathException xpe = actual.left().get();
        assertEquals(expected, xpe.getErrorCode());
    }

    private Either<XPathException, Sequence> executeQuery(final String string) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = server.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();
        try (final DBBroker broker = pool.getBroker()) {
            try {
                return Right(xqueryService.execute(broker, string, null));
            } catch (final XPathException e) {
                return Left(e);
            }
        }
    }
}
