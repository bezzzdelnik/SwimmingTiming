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
class Retalk2CommandLoadScene extends Retalk2Command{
    private final String sceneName;
    private final static byte[] FRAMEPREFIX = ("\u0008Q\u0000\u0000\u0000\u000b\u0000\u0000\u0000\u0000"
            + "\u0008\u00ff\u00ff\u00ff\u00ff\n\u000c\u0000\u0000\u0000^Application\u000b\u0000\u0000\u0000"
            + "\u0000\u0008\u00ff\u00ff\u00ff\u00ff\u0008\u0000\u0000\u0000\u0000\u0008\u0001\u0000\u0000"
            + "\u0000\n(\u0000\u0000\u0000^Method2Modification<UnicodeString,bool>\n\u0000\u0000\u0000\u0000"
            + "\n\n\u0000\u0000\u0000Load scene").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMESUFFIX = { 0x00, 0x01};
    
    Retalk2CommandLoadScene(Retalk2ConnectionController connection, String name) {
        super(connection);
        sceneName = name;
    }

    @Override
    protected void processData(byte[] responceData) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    byte[] getData() {
        Retalk2CommandEncoder encoder = new Retalk2CommandEncoder();
        encoder.add(FRAMEPREFIX);
        encoder.addUnicodeString(sceneName);
        encoder.add(FRAMESUFFIX);
        
        return encoder.getArray();
    }

    @Override
    protected void onRecivedOk() throws UnsupportedOperationException {
        connectionModel.onSceneLoadDone(sceneName);
    }

    @Override
    protected void onRecivedError() throws UnsupportedOperationException {
        connectionModel.onSceneLoadFailed(sceneName);
    }
}
