package com.company.prototype.common.id;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 26-character Crockford Base32 ULID generator.
 * Standard ULID structure: 48-bit timestamp + 80-bit randomness.
 */
public class UlidPublicIdGenerator implements PublicIdGenerator {

    private static final char[] ENCODING_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIMESTAMP_LENGTH = 10;
    private static final int RANDOM_LENGTH = 16;
    private static final int TOTAL_LENGTH = TIMESTAMP_LENGTH + RANDOM_LENGTH;

    private final Random random;

    public UlidPublicIdGenerator() {
        this(new SecureRandom());
    }

    public UlidPublicIdGenerator(Random random) {
        this.random = random;
    }

    @Override
    public String nextId() {
        return generate(System.currentTimeMillis());
    }

    public String generate(long timestamp) {
        char[] chars = new char[TOTAL_LENGTH];

        // 48-bit timestamp to 10 Crockford Base32 chars
        long time = timestamp;
        for (int i = TIMESTAMP_LENGTH - 1; i >= 0; i--) {
            chars[i] = ENCODING_CHARS[(int) (time & 0x1F)];
            time >>>= 5;
        }

        // 80-bit randomness to 16 Crockford Base32 chars
        byte[] randomBytes = new byte[10];
        random.nextBytes(randomBytes);

        // Convert 10 bytes (80 bits) to 16 Base32 characters (each 5 bits)
        // 80 bits = 16 * 5 bits
        int charIndex = TIMESTAMP_LENGTH;
        long buffer = 0;
        int bitsInBuffer = 0;
        for (byte b : randomBytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                chars[charIndex++] = ENCODING_CHARS[(int) ((buffer >>> bitsInBuffer) & 0x1F)];
            }
        }
        if (bitsInBuffer > 0 && charIndex < TOTAL_LENGTH) {
            chars[charIndex] = ENCODING_CHARS[(int) ((buffer << (5 - bitsInBuffer)) & 0x1F)];
        }

        return new String(chars);
    }
}
