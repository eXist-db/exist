package org.exist.dom;

import org.exist.storage.io.VariableByteInput;
import org.easymock.Capture;
import org.exist.storage.io.VariableByteOutputStream;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.captureByte;
import static org.easymock.EasyMock.captureInt;
import static org.easymock.EasyMock.capture;
import java.io.File;
import java.io.IOException;
import org.exist.EXistException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class SymbolTableTest {

    private final File tmpDir = new File(System.getProperty("java.io.tmpdir"));

    @Test
    public void getName_returns_empty_string_when_id_is_zero() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        assertEquals("", symbolTable.getName((short)0));
        symbolTable.close();
    }

    @Test
    public void getNameSpace_returns_empty_string_when_id_is_zero() throws EXistException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        assertEquals("", symbolTable.getNamespace((short)0));
    }

    @Test
    public void geMimeType_returns_empty_string_when_id_is_zero() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        assertEquals("", symbolTable.getMimeType((short)0));
        symbolTable.close();
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void getSymbol_for_localName_throws_exception_when_name_is_empty_string() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        symbolTable.getSymbol("");
        symbolTable.close();
    }

    @Test
    public void getNSSymbol_returns_zero_when_namespace_is_null() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        assertEquals(0, symbolTable.getNSSymbol(null));
        symbolTable.close();
    }

    @Test
    public void getNSSymbol_returns_zero_when_namespace_is_empty_string() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        assertEquals(0, symbolTable.getNSSymbol(""));
        symbolTable.close();
    }

    @Test
    public void localName_ids_are_stable() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        final String localName = "some-name";
        final short localNameId = symbolTable.getSymbol(localName);
        symbolTable.close();

        symbolTable = new SymbolTable(null, tmpDir);
        final String roundTrippedLocalName = symbolTable.getName(localNameId);
        symbolTable.close();

        assertEquals(localName, roundTrippedLocalName);
    }

    @Test
    public void namespace_ids_are_stable() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        final String namespace = "http://something/or/other";
        final short namespaceId = symbolTable.getNSSymbol(namespace);
        symbolTable.close();

        symbolTable = new SymbolTable(null, tmpDir);
        final String roundTrippedNamespace = symbolTable.getNamespace(namespaceId);
        symbolTable.close();

        assertEquals(namespace, roundTrippedNamespace);
    }

    @Test
    public void mimetype_ids_are_stable() throws EXistException, IOException {
        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        final String mimetype = "something/other";
        final int mimetypeId = symbolTable.getMimeTypeId(mimetype);
        symbolTable.close();

        symbolTable = new SymbolTable(null, tmpDir);
        final String roundTrippedMimetype = symbolTable.getMimeType(mimetypeId);
        symbolTable.close();

        assertEquals(mimetype, roundTrippedMimetype);
    }

    @Test
    public void write_and_read_are_balanced() throws EXistException, IOException {

        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        symbolTable.getSymbol("some-name");

        VariableByteOutputStream mockOs = EasyMock.createMock(VariableByteOutputStream.class);
        VariableByteInput mockIs = EasyMock.createMock(VariableByteInput.class);

        final Capture<Byte> byteCapture = new Capture<Byte>();
        final Capture<Integer> intCapture = new Capture<Integer>();
        final Capture<String> strCapture = new Capture<String>();

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
    public void readLegacyFormat() throws EXistException, IOException {

        SymbolTable symbolTable = new SymbolTable(null, tmpDir);
        VariableByteInput mockIs = EasyMock.createMock(VariableByteInput.class);

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
}