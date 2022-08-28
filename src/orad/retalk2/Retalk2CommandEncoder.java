/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 *
 * @author org.ovrn
 */
class Retalk2CommandEncoder {
    private ByteBuffer buffer;

    Retalk2CommandEncoder() {
        buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
    }
    
    void add(byte[] data) throws ArrayIndexOutOfBoundsException{
        while(buffer.remaining() < data.length)
            realocate();
        
        buffer.put(data);
    }
    
    void add(byte data) throws ArrayIndexOutOfBoundsException {
        if(buffer.remaining() == 0)
            realocate();
        buffer.put(data);
    }
    
    void addUnicodeString(String data) throws ArrayIndexOutOfBoundsException {
        add(Retalk2Settings.UNICODESTRINGPFX);
        addInt(data.length());
        add(data.getBytes(StandardCharsets.UTF_16LE));
    }
    
    void addAsciiString(String data) throws ArrayIndexOutOfBoundsException {
        add(Retalk2Settings.ASCIISTRINGPFX);
        addInt(data.length());
        add(data.getBytes(StandardCharsets.US_ASCII));
    }
    
    private void addInt(int data) throws ArrayIndexOutOfBoundsException {
        if(buffer.remaining() < 4)
            realocate();
        buffer.putInt(data);
    }
    
    void addOradInt(int data) throws ArrayIndexOutOfBoundsException {
        add(Retalk2Settings.INTPFX);
        addInt(data);
    }
    
    void addBool(boolean data) throws ArrayIndexOutOfBoundsException {
        if(data)
            add(Retalk2Settings.BOOLTRUE);
        else
            add(Retalk2Settings.BOOLFALSE);
    }
    
    byte[] getArray() {
        return Arrays.copyOfRange(buffer.array(), 0, buffer.position());
    }
    
    ByteBuffer getDataBuffer() {
        return (ByteBuffer)ByteBuffer.allocate(buffer.position()).put(getArray()).flip();
    }
    
    private void realocate() throws ArrayIndexOutOfBoundsException{
        if(buffer.limit() > 100*1024)
            throw new ArrayIndexOutOfBoundsException();
        
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.limit()*2);
        newBuffer.put(buffer);
        buffer = newBuffer;
    }
    
}
