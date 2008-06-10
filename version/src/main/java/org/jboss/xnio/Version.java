package org.jboss.xnio;

/**
 * The version class.
 */
public final class Version {
    private Version() {}

    /**
     * The current XNIO version.
     */
    public static final String VERSION = "1.0.0.Beta1";

    /**
     * Print out the current XNIO version on {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.print(VERSION);
    }
}
