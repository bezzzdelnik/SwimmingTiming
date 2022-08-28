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
class Retalk2CommandUnloadScene extends Retalk2Command{
    private final static byte[] FRAMEPREFIX = ("\u0008\u0051\u0000\u0000\u0000"
        +"\u000b\u0000\u0000\u0000\u0000"
        +"\u0008\u00ff\u00ff\u00ff\u00ff"
        +"\n\u000c\u0000\u0000\u0000^Application"
        +"\u000b\u0000\u0000\u0000\u0000"
        +"\u0008\u00ff\u00ff\u00ff\u00ff"
        +"\u0008\u0000\u0000\u0000\u0000"
        +"\u0008\u0001\u0000\u0000\u0000"
        +"\n\u0023\u0000\u0000\u0000^Method1Modification<UnicodeString>"
        +"\n\u0000\u0000\u0000\u0000"
        +"\n\u000c\u0000\u0000\u0000Unload scene").getBytes(Retalk2Settings.ENCODINGNAME);
    private final String sceneName;
    
    
    Retalk2CommandUnloadScene(Retalk2ConnectionController connection, String name) {
        super(connection);
        sceneName = name;
    }
    
    @Override
    protected void processData(byte[] responceData) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    byte[] getData() throws UnsupportedOperationException {
        Retalk2CommandEncoder encoder = new Retalk2CommandEncoder();
        encoder.add(FRAMEPREFIX);
        encoder.addUnicodeString(sceneName);
        
        return encoder.getArray();
    }
    
    @Override
    protected void onRecivedOk() throws UnsupportedOperationException {
        connectionModel.onSceneUnloadDone(sceneName);
    }

    @Override
    protected void onRecivedError() throws UnsupportedOperationException {
        connectionModel.onSceneUnloadFailed(sceneName);
    }
    
}
