package com.huntero.injector.compiler;

/**
 * Created by huntero on 17-4-5.
 */

public class StringUtils {
    public static String upperFirstLetter(String string) {
        String value = string.trim();
        if (value == null || value.length() == 0) {
            return string;
        }
        return value.substring(0,1).toUpperCase() + value.substring(1, value.length());
    }

    public static boolean isEmpty(String[] arrays) {
        if (arrays == null || arrays.length == 0) {
            return true;
        }
        return false;
    }
}
