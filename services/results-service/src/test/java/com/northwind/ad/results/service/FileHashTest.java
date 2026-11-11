package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class FileHashTest {

    @Test
    public void knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                FileHash.sha256Hex(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                FileHash.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void sameBytesSameHashDifferentBytesDifferentHash() {
        byte[] a = "candidateNumber,sessionId,score,scoredAt\nNW00000003,1,780,2026-09-10T12:31:00\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "candidateNumber,sessionId,score,scoredAt\nNW00000003,1,781,2026-09-10T12:31:00\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(FileHash.sha256Hex(a), FileHash.sha256Hex(a.clone()));
        assertNotEquals(FileHash.sha256Hex(a), FileHash.sha256Hex(b));
        assertEquals(64, FileHash.sha256Hex(a).length());
    }
}
