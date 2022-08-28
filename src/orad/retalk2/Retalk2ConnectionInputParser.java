/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;

/**
 *
 * @author Oleg Voronov
 */
public class Retalk2ConnectionInputParser extends Thread{
    ByteBuffer buffer;
    Retalk2ConnectionController connectionModel;

    Retalk2ConnectionInputParser(Retalk2ConnectionController connectionModel) {
        super("Retalk2ConnectionInputParser");
        this.connectionModel = connectionModel;
        buffer = ByteBuffer.allocate(10*1024);
    }

    @Override
    public void run() {
        try {
            do {     
                Thread.sleep(10);
                
                if(buffer.remaining() == 0) {
                    connectionModel.onInputParserBufferEnded();
                    break;
                }
                
                if(connectionModel.getSocketChannel().read(buffer) != 0)
                    parseBuffer();          
            } while (true);
        } catch (InterruptedException | IOException e) {
        }
    }    
    
    private void parseBuffer()
    {
        try {
            String bufferString = new String(Arrays.copyOfRange(buffer.array(), 0, buffer.position()), Retalk2Settings.ENCODINGNAME);
            int prevStartMatch = 0;
            int startMatch = 0;
            int endMatch = bufferString.length();
            do {
                if(startMatch >= endMatch)
                    break;
                Matcher matcher = Retalk2Settings.HEADERPATTERN.matcher(bufferString).region(startMatch, endMatch);
                
                if(!matcher.find())
                    break;
                
                int dataLength = Integer.parseInt(matcher.group(1));
//                int frameId = Integer.parseInt(matcher.group(2));
                int requestId = Integer.parseInt(matcher.group(3));
//                int reasonOfCo = Integer.parseInt(matcher.group(4));
//                int clientId = Integer.parseInt(matcher.group(5));
                
                prevStartMatch = startMatch;
                startMatch = matcher.end() + dataLength;
                
                if(startMatch > endMatch)
                    break;
                
                byte[] data = bufferString.substring(matcher.end(), startMatch).getBytes(Retalk2Settings.ENCODINGNAME); 
                
                connectionModel.onReciveFrame(requestId, data);                             
                
            } while (true);
            
            buffer.clear();
            
            if(startMatch > endMatch)
                buffer.put(bufferString.substring(prevStartMatch).getBytes(Retalk2Settings.ENCODINGNAME));
            else
                buffer.put(bufferString.substring(startMatch).getBytes(Retalk2Settings.ENCODINGNAME));
             
        } catch (Exception e) {
        }
    }

}
