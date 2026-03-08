package com.shashank.url_shortener.util;

import java.util.concurrent.ThreadLocalRandom;

public class ShortCodeGenerator {

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 7;

    private ShortCodeGenerator() {
    }

    public static String generateShortCode() {
        int length = ThreadLocalRandom.current().nextInt(MIN_LENGTH, MAX_LENGTH + 1);
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = ThreadLocalRandom.current().nextInt(CHARSET.length());
            code.append(CHARSET.charAt(randomIndex));
        }
        return code.toString();
    }
    
}
