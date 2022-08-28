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
class Retalk2CommandCanvasLoadScene extends Retalk2Command{
    private final String sceneName;
    private final static byte[] FRAMEPREFIX = ("\u0008\u0051\u0000\u0000\u0000"
            + "\u000B\u0000\u0000\u0000\u0000"
            + "\u0008\u00FF\u00FF\u00FF\u00FF"
            + "\n\u000C\u0000\u0000\u0000^Application"
            + "\u000B\u0000\u0000\u0000\u0000"
            + "\u0008\u00FF\u00FF\u00FF\u00FF"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0008\u0001\u0000\u0000\u0000"
            + "\n\u0014\u0000\u0000\u0000^Method3Modification"
            + "\n\u0000\u0000\u0000\u0000"
            + "\n\u0010\u0000\u0000\u0000Load scene index"
            + "\u0008\r\u0000\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMEINFIX = ("\u0008\u0007\u0000\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMESUFFIX = ("\u0008\u0007\u0000\u0000\u0000"
            + "\u0008\u0001\u0000\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    
    Retalk2CommandCanvasLoadScene(Retalk2ConnectionController connection, String name) {
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
        encoder.add(FRAMEINFIX);
        encoder.addOradInt(connectionModel.getCanvasIndex());
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
