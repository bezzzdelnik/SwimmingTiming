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
class Retalk2CommandSetExport extends Retalk2Command{
    private final String sceneName;
    private final String exportName;
    private final String value;
    private final static byte[] FRAMEPREFIX = ("\u0008Q\u0000\u0000"
            + "\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMEINFIX = ("\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\n\u000b\u0000\u0000\u0000"
            + "^BaseExport").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMESUFFIX = ("\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0008\u0001\u0000\u0000\u0000"
            + "\n\u0004\u0000\u0000\u0000@Mod"
            + "\n\u0006\u0000\u0000\u0000/Value"
            + "\n\u0000\u0000\u0000\u0000"
            + "\u0008\r\u0000\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    
    Retalk2CommandSetExport(Retalk2ConnectionController connection, String nameScene, String nameExport, String value) {
        super(connection);
        sceneName = nameScene;
        exportName = nameExport;
        this.value = value;
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
        encoder.addUnicodeString(exportName);
        encoder.add(FRAMESUFFIX);
        encoder.addUnicodeString(value);       
        
        return encoder.getArray();
    }

}
