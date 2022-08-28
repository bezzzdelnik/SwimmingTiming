/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.util.ArrayList;

/**
 *
 * @author org.ovrn
 */
public class Retalk2CommandGetCanvases extends Retalk2Command{
    private static final byte[] FRAMEDATA = ("\u0008\u0014\u0000\u0000\u0000"
            + "\u000B\u0000\u0000\u0000\u0000"
            + "\u0008\u00FF\u00FF\u00FF\u00FF"
            + "\n\u0007\u0000\u0000\u0000^Output"
            + "\u000B\u0007\u0000\u0000\u0000C\u0000a\u0000n\u0000"
            + "v\u0000a\u0000s\u00001\u0000"
            + "\u0008\u00FF\u00FF\u00FF\u00FF"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERFRAMEPREFIX = ("\u0008\u0015\u0000\u0000\u0000"
            + "\u000B\u0000\u0000\u0000\u0000"
            + "\u0008\u00FF\u00FF\u00FF\u00FF"
            + "\n\u0007\u0000\u0000\u0000^Output"
            + "\u000B\u0007\u0000\u0000\u0000C\u0000a\u0000n\u0000"
            + "v\u0000a\u0000s\u00001\u0000"
            + "\u0008\u00FF\u00FF\u00FF\u00FF"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0002").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERCANVASPREFIX = ("\n\u0007\u0000\u0000\u0000^Output").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERCANVASSUFFIX = ("\u0008\u00ff\u00ff\u00ff\u00ff").getBytes(Retalk2Settings.ENCODINGNAME);
    
    Retalk2CommandGetCanvases(Retalk2ConnectionController connection) {
        super(connection);
        hasDataResponce = true;
    }
    
    @Override
    protected void processData(byte[] responceData) throws UnsupportedOperationException {
        Retalk2CommandDecoder decoder = new Retalk2CommandDecoder(responceData);
        
        if(!decoder.skipData(ANSWERFRAMEPREFIX)){
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        int canvasesCount; 
        try {
            canvasesCount = decoder.getRetalkUInt();
        } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        if(canvasesCount == 0)
            return;
        
        ArrayList<String> canvases = new ArrayList<>();
        for (int i = 0; i < canvasesCount; i++) {
            if(!decoder.skipData(ANSWERCANVASPREFIX)){
                dataResponceStatus = Status.ResponceError;
                return;
            }
            
            String canvas;
            try {
                canvas = decoder.getRetalkUnicodeString();
            } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
                dataResponceStatus = Status.ResponceError;
                return;
            }
            
            canvases.add(canvas);
         
            if(!decoder.skipData(ANSWERCANVASSUFFIX)){
                dataResponceStatus = Status.ResponceError;
                return;
            }
        }
        
        connectionModel.onReciveCanvases(canvases);
    }

    @Override
    byte[] getData() throws UnsupportedOperationException {
        return FRAMEDATA;
    }
    
}
