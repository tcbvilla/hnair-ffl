package com.ffl.util;

import java.security.SecureRandom;

public final class InviteCodeGenerator {

    private static final String CHARSET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}
