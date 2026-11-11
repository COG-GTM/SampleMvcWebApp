package com.northwind.ad.results.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 of uploaded score files, used to recognise a file that was uploaded before. */
public final class FileHash {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private FileHash() {
    }

    /** Lower-case hex SHA-256 (64 chars) of the raw bytes. */
    public static String sha256Hex(byte[] content) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
        byte[] digest = md.digest(content);
        char[] out = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            out[i * 2] = HEX[(digest[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[digest[i] & 0xF];
        }
        return new String(out);
    }
}
