package com.huntero.injector.compiler;

import java.security.MessageDigest;

/**
 * Created by huntero on 17-3-30.
 */

public final class Encrypt {
    public static String Md5(String plainText ) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte b[] = md.digest();
            int i;

            StringBuffer buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if(i<0) i+= 256;
                if(i<16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
            //32位
            return buf.toString();
            //16位
//          return buf.toString().substring(8, 24);
        } catch (Exception e) {
            e.printStackTrace();
            return plainText;
        }
    }
}
