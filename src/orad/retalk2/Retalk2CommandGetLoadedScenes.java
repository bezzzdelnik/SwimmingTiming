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
class Retalk2CommandGetLoadedScenes extends Retalk2Command{
    private static final byte[] FRAMEDATA = ("\u0008\u0014\u0000\u0000\u0000"
            + "\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\n\u0006\u0000\u0000\u0000^Scene"
            + "\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERFRAMEPREFIX = ("\u0008\u0015\u0000\u0000\u0000"
            + "\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\n\u0006\u0000\u0000\u0000^Scene"
            + "\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0002").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] ANSWERSCENEPREFIX = ("\n\u0006\u0000\u0000\u0000"
            + "^Scene").getBytes(Retalk2Settings.ENCODINGNAME);

    public Retalk2CommandGetLoadedScenes(Retalk2ConnectionController connection) {
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
        
        int sceneCount; 
        try {
            sceneCount = decoder.getRetalkUInt();
        } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        ArrayList<String> scenes = new ArrayList<>();
        if(sceneCount == 0){
            connectionModel.onLodedScenesRecived(scenes);
            return;
        }
        
        for (int i = 0; i < sceneCount; i++) {
            if(!decoder.skipData(ANSWERSCENEPREFIX)){
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
         
            try{
                int canvasIndex = decoder.getRetalkUInt();
                if(canvasIndex == connectionModel.getCanvasIndex())
                    scenes.add(sceneName);
            } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
                dataResponceStatus = Status.ResponceError;
                return;
            }
        }
        
        connectionModel.onLodedScenesRecived(scenes);
    }

    @Override
    byte[] getData() throws UnsupportedOperationException {
        return FRAMEDATA;
    }

    @Override
    protected void onRecivedError() {
        connectionModel.onGetLoadedScenesError();
    }
    
}
