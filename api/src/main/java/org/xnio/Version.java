
package org.xnio;

/**
 * The version class.
 *
 * @apiviz.exclude
 */
public final class Version {
    private Version() {}

    /**
     * The current XNIO version.
     */
    public static final String VERSION = getVersionString();

    /**
     * Get the version string.
     *
     * @return the version string
     */
    public static String getVersionString() {
        return "TRUNK SNAPSHOT";
    }

    /**
     * Print out the current XNIO version on {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.print(VERSION);
    }
}
