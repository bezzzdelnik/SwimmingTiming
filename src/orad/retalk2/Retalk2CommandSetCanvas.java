/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.nio.charset.StandardCharsets;

/**
 *
 * @author org.ovrn
 */
class Retalk2CommandSetCanvas extends Retalk2Command{
    private final static byte[] FRAMEPREFIX = "\u0008\u0023\u0000\u0000\u0000".getBytes(StandardCharsets.UTF_8);
    private final int index;

    Retalk2CommandSetCanvas(Retalk2ConnectionController connection, int index) {
        super(connection);
        hasDataResponce = false; 
        this.index = index;
    }
    
    @Override
    protected void processData(byte[] responceData) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    byte[] getData() throws UnsupportedOperationException {
        Retalk2CommandEncoder encoder = new Retalk2CommandEncoder();
        encoder.add(FRAMEPREFIX);
        encoder.addOradInt(index);
        
        return encoder.getArray();
    }
    
    @Override
    protected void onRecivedOk() throws UnsupportedOperationException {
        connectionModel.onReciveCanvasSet();
    }

    @Override
    protected void onRecivedError() throws UnsupportedOperationException {
//        Object[] params = {index, responceList.get(responceList.size()-1)};
//        connectionModel.getLogger().log(Level.SEVERE, "Canvas index \"{0}\" didn't set with message: {1}", params);
        connectionModel.oReciveCanvasNotSet();
    }
}
