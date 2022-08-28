/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

/**
 *
 * @author org.ovrn
 */
class Retalk2CommandAcivateScene  extends Retalk2Command{
    private final static byte[] ACTIVATEPREFIX = ("\u0008\u0051\u0000\u0000\u0000"
            +"\u000b\u0000\u0000\u0000\u0000"
            +"\u0008\u00ff\u00ff\u00ff\u00ff"
            +"\n\u0009\u0000\u0000\u0000^Viewport"
            +"\u000b\u0004\u0000\u0000\u0000F\u0000u\u0000l\u0000l\u0000"
            +"\u0008\u00ff\u00ff\u00ff\u00ff"
            +"\u0008\u0000\u0000\u0000\u0000"
            +"\u0008\u0001\u0000\u0000\u0000"
            +"\n\u0027\u0000\u0000\u0000^Method2Modification<int,UnicodeString>"
            +"\n\u0007\u0000\u0000\u0000/Scenes"
            +"\n\u0008\u0000\u0000\u0000Activate").getBytes(Retalk2Settings.ENCODINGNAME);
    private final String sceneName;
    private final int slot;
    
    
    Retalk2CommandAcivateScene(Retalk2ConnectionController connection, String name, int slot){ 
        super(connection);
        sceneName = name;
        this.slot = slot;
    }
    
    @Override
    protected void processData(byte[] responceData) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    byte[] getData() throws UnsupportedOperationException {
        Retalk2CommandEncoder encoder = new Retalk2CommandEncoder();
        encoder.add(ACTIVATEPREFIX);
        encoder.addOradInt(slot);
        encoder.addUnicodeString(sceneName);
        
        return encoder.getArray();
    }
    
    @Override
    protected void onRecivedOk() throws UnsupportedOperationException {
        connectionModel.onSceneActivationDone(sceneName, slot);
    }

    @Override
    protected void onRecivedError() {
        connectionModel.onSceneActivationFailed(sceneName, slot);
    }
    
}
