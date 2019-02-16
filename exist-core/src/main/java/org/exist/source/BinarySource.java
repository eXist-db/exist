package org.exist.source;

import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.util.io.FastByteArrayInputStream;

import java.io.*;

public class BinarySource extends AbstractSource {

    //TODO replace this with a streaming approach
    private byte[] data;
    private boolean checkEncoding = false;
    private String encoding = "UTF-8";

    public BinarySource(byte[] data, boolean checkXQEncoding) {
        this.data = data;
        this.checkEncoding = checkXQEncoding;
    }

    public String path() {
        return type();
    }

    @Override
    public String type() {
        return "Binary";
    }

    public Object getKey() {
        return data;
    }

    @Override
    public Validity isValid(final DBBroker broker) {
        return Source.Validity.VALID;
    }

    @Override
    public Validity isValid(final Source other) {
        return Source.Validity.VALID;
    }

    public Reader getReader() throws IOException {
        checkEncoding();
        return new InputStreamReader(getInputStream(), encoding);
    }

    public InputStream getInputStream() throws IOException {
        return new FastByteArrayInputStream(data);
    }

    public String getContent() throws IOException {
        checkEncoding();
        return new String(data, encoding);
    }

    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            try (final InputStream is = getInputStream()) {
                String checkedEnc = guessXQueryEncoding(is);
                if (checkedEnc != null) {
                    encoding = checkedEnc;
                }
            }
        }
    }

	@Override
	public void validate(Subject subject, int perm) throws PermissionDeniedException {
		// TODO protected?
	}
}