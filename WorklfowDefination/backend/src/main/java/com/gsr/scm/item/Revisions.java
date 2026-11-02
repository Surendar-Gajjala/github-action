package com.gsr.scm.item;

/** Alphabetic revision sequence: A, B, ..., Z, AA, AB, ... */
public final class Revisions {

    public static final String INITIAL = "A";

    private Revisions() {
    }

    public static String next(String revision) {
        if (revision == null || revision.isBlank()) {
            return INITIAL;
        }
        char[] chars = revision.toUpperCase().toCharArray();
        int i = chars.length - 1;
        while (i >= 0) {
            if (chars[i] < 'A' || chars[i] > 'Z') {
                throw new IllegalArgumentException("Unsupported revision format: " + revision);
            }
            if (chars[i] != 'Z') {
                chars[i]++;
                return new String(chars);
            }
            chars[i] = 'A';
            i--;
        }
        return "A" + new String(chars);
    }
}
