package org.exist.numbering;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class DLNBaseTest {

    @Test
    public void DLNByteArrayConstructor_roundTrip() {
        final DLNBase dlnBase = new DLNBase();

        for(int i = 0; i < 100; i++) {
            dlnBase.incrementLevelId();

            if(i % 10 == 0) {
                for(final int levelId : dlnBase.getLevelIds()) {
                    dlnBase.addLevelId(levelId, true);
                }
            }

            final byte[] data = new byte[dlnBase.size()];
            dlnBase.serialize(data, 0);

            final DLN reconstructedDln = new DLN(dlnBase.units(), data, 0);

            assertTrue(dlnBase.equals(reconstructedDln));
        }
    }
}
