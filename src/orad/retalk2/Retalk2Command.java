/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 *
 * @author org.ovrn
 */
abstract class Retalk2Command {
    private int commandId;
    private Status responceStatus;
    private final Date creationTimestamp;
    private Date sendTimestamp;
    
    protected Status dataResponceStatus;
    protected boolean hasDataResponce;
    protected Retalk2ConnectionController connectionModel;
    
    final ArrayList<String> responceList;
    
    enum Status {
        None,
        WaitingResponce,
        ResponceOk,
        ResponceError
    }

    Retalk2Command(Retalk2ConnectionController connectionModel) {
        responceList = new ArrayList<>();
        responceStatus = Status.None;
        dataResponceStatus = Status.None;
        hasDataResponce = false;
        creationTimestamp = new Date();
        this.connectionModel = connectionModel;
    }

    void addResponce(byte[] responceData) {
            String responceString = new String(responceData, Retalk2Settings.ENCODINGNAME);
            responceList.add(responceString);
            processResponce(responceData);            
    }
    
    void setSended()
    {
        if(getSendTimestamp() == null){
            responceStatus = Status.WaitingResponce;
            if(hasDataResponce)
                dataResponceStatus = Status.WaitingResponce;
            sendTimestamp = new Date();
        }
    }
    
    private void processResponce(byte[] responceData){
        if (Arrays.equals(responceData, Retalk2Settings.REPLYOKDATA)) {
            if (responceStatus == Status.WaitingResponce){
                onRecivedOk();
                responceStatus = Status.ResponceOk;
            }
            return;
        }
        
        if (responceData.length > 4 && Arrays.equals(Arrays.copyOfRange(responceData, 0, 5), Arrays.copyOfRange(Retalk2Settings.REPLYOKDATA, 0, 5))){
            if(responceStatus == Status.WaitingResponce)
                onRecivedError();
            responceStatus = Status.ResponceError;
            return;
        }
        
        try{
            processData(responceData);
        } catch (UnsupportedOperationException ex){
            dataResponceStatus = Status.ResponceError;
//            retalkInterface.getLogger().log(Level.SEVERE, ex.toString());
        }
    }
    
    Status getResponceStatus(){
        return responceStatus;
    }

    public Date getCreationTimestamp() {
        return creationTimestamp;
    }

    public Date getSendTimestamp() {
        return sendTimestamp;
    }

    public int getCommandId() {
        return commandId;
    }
    
    Status getDataResponceStatus() {
        return dataResponceStatus;
    }
    
    protected abstract void processData(byte[] responceData) throws UnsupportedOperationException;
    abstract byte[] getData() throws UnsupportedOperationException;
    protected void onRecivedOk() {}
    protected void onRecivedError() {
    }
}
