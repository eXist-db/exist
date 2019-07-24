package org.exist.dom.persistent;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.io.VariableByteInput;
import org.easymock.Capture;
import org.exist.storage.io.VariableByteOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.util.Configuration;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class SymbolTableTest {

    private final SymbolTable createSymbolTable(final Path dir) throws BrokerPoolServiceException {
        final SymbolTable symbolTable = new SymbolTable();
        final Configuration configuration = createMock(Configuration.class);
        expect(configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR)).andReturn(dir);

        replay(configuration);

        symbolTable.configure(configuration);
        symbolTable.prepare(null);
        return symbolTable;
    }

    @Test
    public void getName_returns_empty_string_when_id_is_zero() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        assertEquals("", symbolTable.getName((short)0));
        symbolTable.close();
    }

    @Test
    public void getNameSpace_returns_empty_string_when_id_is_zero() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        assertEquals("", symbolTable.getNamespace((short)0));
    }

    @Test
    public void geMimeType_returns_empty_string_when_id_is_zero() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        assertEquals("", symbolTable.getMimeType((short)0));
        symbolTable.close();
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void getSymbol_for_localName_throws_exception_when_name_is_empty_string() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        symbolTable.getSymbol("");
        symbolTable.close();
    }

    @Test
    public void getNSSymbol_returns_zero_when_namespace_is_null() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        assertEquals(0, symbolTable.getNSSymbol(null));
        symbolTable.close();
    }

    @Test
    public void getNSSymbol_returns_zero_when_namespace_is_empty_string() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        assertEquals(0, symbolTable.getNSSymbol(""));
        symbolTable.close();
    }

    @Test
    public void localName_ids_are_stable() throws IOException, BrokerPoolServiceException {
        final Path tmpDir = createTempDir();
        SymbolTable symbolTable = createSymbolTable(tmpDir);
        final String localName = "some-name";
        final short localNameId = symbolTable.getSymbol(localName);
        symbolTable.close();

        symbolTable = createSymbolTable(tmpDir);
        final String roundTrippedLocalName = symbolTable.getName(localNameId);
        symbolTable.close();

        assertEquals(localName, roundTrippedLocalName);
    }

    @Test
    public void namespace_ids_are_stable() throws IOException, BrokerPoolServiceException {
        final Path tmpDir = createTempDir();
        SymbolTable symbolTable = createSymbolTable(tmpDir);
        final String namespace = "http://something/or/other";
        final short namespaceId = symbolTable.getNSSymbol(namespace);
        symbolTable.close();

        symbolTable = createSymbolTable(tmpDir);
        final String roundTrippedNamespace = symbolTable.getNamespace(namespaceId);
        symbolTable.close();

        assertEquals(namespace, roundTrippedNamespace);
    }

    @Test
    public void mimetype_ids_are_stable() throws IOException, BrokerPoolServiceException {
        final Path tmpDir = createTempDir();
        SymbolTable symbolTable = createSymbolTable(tmpDir);
        final String mimetype = "something/other";
        final int mimetypeId = symbolTable.getMimeTypeId(mimetype);
        symbolTable.close();

        symbolTable = createSymbolTable(tmpDir);
        final String roundTrippedMimetype = symbolTable.getMimeType(mimetypeId);
        symbolTable.close();

        assertEquals(mimetype, roundTrippedMimetype);
    }

    @Test
    public void write_and_read_are_balanced() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        symbolTable.getSymbol("some-name");

        VariableByteOutputStream mockOs = createMock(VariableByteOutputStream.class);
        VariableByteInput mockIs = createMock(VariableByteInput.class);

        final Capture<Byte> byteCapture = newCapture();
        final Capture<Integer> intCapture = newCapture();
        final Capture<String> strCapture = newCapture();

        //write expectations
        mockOs.writeByte(captureByte(byteCapture));
        mockOs.writeInt(captureInt(intCapture));
        mockOs.writeUTF(capture(strCapture));

        replay(mockOs);

        symbolTable.localNameSymbols.write(mockOs);

        verify(mockOs);

        //read expectations
        expect(mockIs.available()).andReturn(1);
        expect(mockIs.readByte()).andReturn(byteCapture.getValue());
        expect(mockIs.readInt()).andReturn(intCapture.getValue());
        expect(mockIs.readUTF()).andReturn(strCapture.getValue());
        expect(mockIs.available()).andReturn(0);

        replay(mockIs);

        symbolTable.read(mockIs);

        verify(mockIs);
    }

    @Test
    public void readLegacyFormat() throws IOException, BrokerPoolServiceException {
        final SymbolTable symbolTable = createSymbolTable(createTempDir());
        VariableByteInput mockIs = createMock(VariableByteInput.class);

        /* readLegacy expectations */

        //max and nsMax
        expect(mockIs.readShort()).andReturn((short)1);
        expect(mockIs.readShort()).andReturn((short)1);

        //localnames
        expect(mockIs.readInt()).andReturn(1);
        expect(mockIs.readUTF()).andReturn("local-name");
        expect(mockIs.readShort()).andReturn((short)67);

        //namespaces
        expect(mockIs.readInt()).andReturn(1);
        expect(mockIs.readUTF()).andReturn("http://some/or/other");
        expect(mockIs.readShort()).andReturn((short)77);

        //default mappings
        expect(mockIs.readInt()).andReturn(1);
        expect(mockIs.readUTF()).andReturn("mapping");
        expect(mockIs.readShort()).andReturn((short)87);

        //mimetypes
        expect(mockIs.readInt()).andReturn(1);
        expect(mockIs.readUTF()).andReturn("some/other");
        expect(mockIs.readInt()).andReturn(97);

        //replay
        replay(mockIs);

        //action
        symbolTable.readLegacy(mockIs);

        //verify
        verify(mockIs);

        symbolTable.close();
    }

    private static Path createTempDir() throws IOException {
        return Files.createTempDirectory("exist-symbolTableTest");
    }
}