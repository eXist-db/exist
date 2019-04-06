package samples;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Samples {

    public static final Samples SAMPLES = new Samples();

    /**
     * Gets the path of the Address Book sample
     *
     * @return The path to the Address Book sample
     */
    public Path getAddressBookSample() throws URISyntaxException {
        return getSample("validation/addressbook/addressbook.xsd");
    }

    /**
     * Gets the path of the Shakespeare Hamlet sample
     *
     * @return The path to the Shakespeare Hamlet sample
     */
    public Path getHamletSample() throws URISyntaxException {
        return getSample("shakespeare/hamlet.xml");
    }

    /**
     * Gets the path of the Shakespeare Romeo and Juliet sample
     *
     * @return The path to the Shakespeare Romeo and Juliet sample
     */
    public Path getRomeoAndJulietSample() throws URISyntaxException {
        return getSample("shakespeare/r_and_j.xml");
    }

    /**
     * Gets the path of the Macbeth sample
     *
     * @return The path to the Macbeth sample
     */
    public Path getMacbethSample() throws URISyntaxException {
        return getSample("shakespeare/macbeth.xml");
    }

    /**
     * Gets the path of the Shakespeare samples
     *
     * @return The path to the Shakespeare samples
     */
    public Path getShakespeareSamples() throws URISyntaxException {
        return getRomeoAndJulietSample().getParent();
    }

    /**
     * Gets the path of the Bibliographic sample
     *
     * @return The path to the Bibliographic sample
     */
    public Path getBiblioSample() throws URISyntaxException {
        return getSample("biblio.rdf");
    }

    /**
     * Gets the sample
     *
     * @return The path to the sample
     */
    public Path getSample(final String sample) throws URISyntaxException {
        return Paths.get(getClass().getResource(sample).toURI());
    }
}
