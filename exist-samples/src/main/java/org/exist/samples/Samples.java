package org.exist.samples;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.URL;

public class Samples {

    public static final Samples SAMPLES = new Samples();

    private Samples() {}

    /**
     * Gets the path of the Address Book sample.
     *
     * @return The path to the Address Book sample
     */
    public @Nullable InputStream getAddressBookSample() {
        return getSample("validation/addressbook/addressbook.xsd");
    }

    /**
     * Gets the path of the Shakespeare Hamlet sample.
     *
     * @return The path to the Shakespeare Hamlet sample
     */
    public @Nullable InputStream getHamletSample() {
        return getShakespeareSample("hamlet.xml");
    }

    /**
     * Gets the path of the Shakespeare Romeo and Juliet sample.
     *
     * @return The path to the Shakespeare Romeo and Juliet sample
     */
    public @Nullable InputStream getRomeoAndJulietSample() {
        return getShakespeareSample("r_and_j.xml");
    }

    /**
     * Gets the path of the Macbeth sample.
     *
     * @return The path to the Macbeth sample
     */
    public@Nullable  InputStream getMacbethSample() {
        return getShakespeareSample("macbeth.xml");
    }

    /**
     * Get the names of just the Shakespeare XML data sample files.
     *
     * @return the names of the Shakespeare XML data files.
     */
    public String[] getShakespeareXmlSampleNames() {
        return new String[] { "hamlet.xml", "macbeth.xml", "r_and_j.xml"};
    }

    /**
     * Get the names of all the Shakespeare sample files.
     *
     * @return the names of all the Shakespeare sample files.
     */
    public String[] getShakespeareSampleNames() {
        return new String[] { "collection.xconf", "hamlet.xml", "macbeth.xml", "play.dtd", "r_and_j.xml", "shakes.css", "shakes.xsl"};
    }

    /**
     * Gets the path of the shakespeare sample.
     *
     * @param sampleFileName the name of the shakespeare sample.
     *
     * @return The path to the shakespeare sample
     */
    public @Nullable InputStream getShakespeareSample(final String sampleFileName) {
        return getSample("shakespeare/" + sampleFileName);
    }

    /**
     * Gets the path of the Bibliographic sample.
     *
     * @return The path to the Bibliographic sample
     */
    public @Nullable InputStream getBiblioSample() {
        return getSample("biblio.rdf");
    }

    /**
     * Gets the sample.
     *
     * @param sample relative path to the sample
     *
     * @return The stream to the sample
     */
    public @Nullable InputStream getSample(final String sample) {
        return getClass().getResourceAsStream(sample);
    }


    /**
     * Gets the URL of the sample.
     *
     * @param sample relative path to the sample
     *
     * @return The url of the sample
     */
    public @Nullable URL getSampleUrl(final String sample) {
        return getClass().getResource(sample);
    }
}
