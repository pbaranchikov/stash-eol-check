package com.pbaranchikov.stash.checks;

/**
 * Class, holding constants.
 * @author Pavel Baranchikov
 */
public class Constants {
    /**
     * SHA1 of the NULL commit.
     */
    public static final String NON_ID = "0000000000000000000000000000000000000000";
    /**
     * Carriage return symbol.
     */
    public static final int CR = 0x0D;
    /**
     * Line feed symbol.
     */
    public static final int LF = 0x0A;
    /**
     * Setting name for excluded files.
     */
    public static final String SETTING_EXCLUDED_FILES = "excludeFiles";
    /**
     * Separator, used in settings to distinguish excluded patterns from one
     * another.
     */
    public static final String PATTERNS_SEPARATOR = ",";

    private Constants() {
    }

}
