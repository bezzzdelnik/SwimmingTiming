/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 *
 * @author org.ovrn
 */
final class Retalk2Settings {
    static final byte UNICODESTRINGPFX = 0x0b;
    static final byte ASCIISTRINGPFX = 0x0a;
    static final byte INTPFX = 0x08;
    static final byte[] BOOLTRUE = {0x00, 0x01};
    static final byte[] BOOLFALSE = {0x00, 0x00};
    static final Charset ENCODINGNAME = StandardCharsets.ISO_8859_1;
    static final Pattern HEADERPATTERN = Pattern.compile("RE_FRAME\\((-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+)\\)");
    public static final byte[] REPLYOKDATA = {0x08,0x01,0x00,0x00,0x00,0x08,0x00,0x00,0x00,0x00,0x08,0x00,0x00,0x00,0x00};
}
