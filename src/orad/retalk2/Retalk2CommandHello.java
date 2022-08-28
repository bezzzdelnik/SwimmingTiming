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
public class Retalk2CommandHello extends Retalk2Command{
    private final static byte[] FRAMEDATA = {0x08,0x64,0x00,0x00,0x00};
    private final static byte[] ANSWERFRAMEPRFIX = {0x08,0x65,0x00,0x00,0x00};
    
    Retalk2CommandHello(Retalk2ConnectionController connection) {
        super(connection);
        hasDataResponce = true;
    }
    
    @Override
    protected void processData(byte[] responceData) {
        if(responceData.length != 10){
           dataResponceStatus = Status.ResponceError;
           return;
        }
        
        Retalk2CommandDecoder dataParser = new Retalk2CommandDecoder(responceData);
        if(!dataParser.skipData(ANSWERFRAMEPRFIX)){
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        int clientId; 
        try {
            clientId = dataParser.getRetalkUInt();
        } catch (ArrayIndexOutOfBoundsException | RetalkCantDecodeException e) {
            dataResponceStatus = Status.ResponceError;
            return;
        }
        
        connectionModel.onRecivedClientID(clientId);
        if(dataResponceStatus == Status.WaitingResponce)
            dataResponceStatus = Status.ResponceOk;
    }

    @Override
    byte[] getData() {
        return FRAMEDATA;
    }
}
