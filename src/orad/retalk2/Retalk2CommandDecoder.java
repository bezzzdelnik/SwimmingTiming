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
class RetalkCantDecodeException extends Exception {
    private static final long serialVersionUID = 1L;
}

class Retalk2CommandDecoder {
    private final ByteBuffer buffer;

    public Retalk2CommandDecoder(byte[] data) {
        buffer = ByteBuffer.allocate(data.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(data);
        buffer.position(0);
    }
    
    boolean skipData(byte[] skippedData){
        if(skippedData.length > buffer.remaining())
            return false;
        
        byte[] toSkipData = new byte[skippedData.length];
        buffer.get(toSkipData);
        
        if(Arrays.equals(skippedData, toSkipData))
            return true;
        
        positionGoBack(skippedData.length);
        return false;
                
    }
    
    int getRetalkUInt() throws ArrayIndexOutOfBoundsException, RetalkCantDecodeException{
        if(buffer.remaining() < 5)
            throw new ArrayIndexOutOfBoundsException();
        
        if(buffer.get() != Retalk2Settings.INTPFX){
            positionGoBack(1);
            throw new RetalkCantDecodeException();
        }
        
        int result = buffer.getInt();
        
        return result;
    }
    
    String getRetalkUnicodeString() throws ArrayIndexOutOfBoundsException, RetalkCantDecodeException{
        if(buffer.remaining() < 5)
            throw new ArrayIndexOutOfBoundsException();
        
        if(buffer.get() != Retalk2Settings.UNICODESTRINGPFX){
            positionGoBack(1);
            throw new RetalkCantDecodeException();
        }
        int resultSize = buffer.getInt()*2;//beacuse of utf16 coding multiply by 2
        
        if(buffer.remaining() < resultSize)
            throw new ArrayIndexOutOfBoundsException();
        
        byte[] stringBytes = new byte[resultSize];
        buffer.get(stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_16LE);
    }
    
    String getRetalkAsciiString() throws ArrayIndexOutOfBoundsException, RetalkCantDecodeException{
        if(buffer.remaining() < 5)
            throw new ArrayIndexOutOfBoundsException();
        
        if(buffer.get() != Retalk2Settings.ASCIISTRINGPFX){
            positionGoBack(1);
            throw new RetalkCantDecodeException();
        }
        int resultSize = buffer.getInt();
        
        if(buffer.remaining() < resultSize)
            throw new ArrayIndexOutOfBoundsException();
        
        byte[] stringBytes = new byte[resultSize];
        buffer.get(stringBytes);
        return new String(stringBytes, StandardCharsets.US_ASCII);
    }
    
    private void positionGoBack(int offset) {
        buffer.position(buffer.position() - offset);
    }
}
