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
class Retalk2CommandGetActiveScenes extends Retalk2Command{
    private static final byte[] FRAMEDATA = ("\u0008\u0019\u0000\u0000\u0000"
            + "\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\n\u0009\u0000\u0000\u0000^Viewport"
            + "\u000b\u0004\u0000\u0000\u0000F\u0000u\u0000l\u0000l\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0008\u0001\u0000\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERFRAMEPREFIX = ("\u0008\u001a\u0000\u0000\u0000"
            + "\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\n\u0009\u0000\u0000\u0000^Viewport"
            + "\u000b\u0004\u0000\u0000\u0000F\u0000u\u0000l\u0000l\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERFRAMEPREFIX2 = ("\u0008\u0000\u0000\u0000\u0000"
            + "\u0008\u0001\u0000\u0000\u0000"
            + "\u0002").getBytes(Retalk2Settings.ENCODINGNAME);

    Retalk2CommandGetActiveScenes(Retalk2ConnectionController connection) {
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
        
        try {
            decoder.getRetalkUInt();
        } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        if(!decoder.skipData(ANSWERFRAMEPREFIX2)){
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        int slotCount; 
        try {
            slotCount = decoder.getRetalkUInt();
        } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        ArrayList<String> scenes = new ArrayList<>();
        if(slotCount == 0){
            connectionModel.onActivatedScenesRecived(scenes);
            return;
        }
        
        for (int i = 0; i < slotCount; i++) {  
            try {
                String scenePrefix = decoder.getRetalkAsciiString();
                if("".equals(scenePrefix)){
                    decoder.getRetalkUnicodeString();
                    decoder.getRetalkUInt();
                    scenes.add("");
                    continue;
                }                    
            } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
                dataResponceStatus = Status.ResponceError;
                return;
            }
            
            String sceneName;
            try {
                sceneName = decoder.getRetalkUnicodeString();
            } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
                dataResponceStatus = Status.ResponceError;
                return;
            }
            
            try {
                int canvasIndex = decoder.getRetalkUInt();
                if(canvasIndex == connectionModel.getCanvasIndex())
                    scenes.add(sceneName);
                else
                    scenes.add("");
            } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
                dataResponceStatus = Status.ResponceError;
                return;
            }

        }
        
        connectionModel.onActivatedScenesRecived(scenes);
    }

    @Override
    byte[] getData() throws UnsupportedOperationException {
        return FRAMEDATA;
    }

    @Override
    protected void onRecivedError() {
        connectionModel.onGetActiveScenesError();
    }
    
}
