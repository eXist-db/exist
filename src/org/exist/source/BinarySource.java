package org.exist.source;

import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;

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

    public Object getKey() {
        return data;
    }

    public int isValid(DBBroker broker) {
        return Source.VALID;
    }

    public int isValid(Source other) {
        return Source.VALID;
    }

    public Reader getReader() throws IOException {
        checkEncoding();
        return new InputStreamReader(getInputStream(), encoding);
    }

    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(data);
    }

    public String getContent() throws IOException {
        checkEncoding();
        return new String(data, encoding);
    }

    private void checkEncoding() throws IOException {
        if (checkEncoding) {
            final InputStream is = getInputStream();
            try {
                String checkedEnc = guessXQueryEncoding(is);
                if (checkedEnc != null)
                    {encoding = checkedEnc;}
            } finally {
                is.close();
            }
        }
    }

	@Override
	public void validate(Subject subject, int perm) throws PermissionDeniedException {
		// TODO protected?
	}
}