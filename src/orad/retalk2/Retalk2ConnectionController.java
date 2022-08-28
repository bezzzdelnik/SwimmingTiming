/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import orad.AbstractREConnectionController;
import orad.IREConnectionModel;
import orad.IREConnectionModelListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 *
 * @author Oleg Voronov
 */
public class Retalk2ConnectionController extends AbstractREConnectionController implements IREConnectionModel {
    private int port = 8735;
    private String hostName = "localhost";
    private Status status = Status.Disconnected;
    private String canvasName = "";    
    private SocketChannel socketChannel;
    private Retalk2ConnectionInputParser inputParser;
    private final HashMap<Integer, Retalk2Command> commandQue = new HashMap<>();
    private int canvasIndex = -1;
    private int clientId = -1;
    private int commandId = -1;
    private final int NOCANVASINDEX = 0xffffffff;
    private Thread connectionThread;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ArrayList<IREConnectionModelListener> listeners = new ArrayList<>();
    private final Object portLock = new Object();
    private final Object statusLock = new Object();
    private final Object hostnameLock = new Object();
    private final Object canvasNameLock = new Object();

    private void setStatus(Status status) {
        boolean isChanged = false;
        synchronized(statusLock){
            if(!this.status.equals(status)){
                this.status = status;
                isChanged = true;
            }
        }
        
        if(isChanged){
            synchronized(listeners){
                listeners.stream().forEach((listener) -> {
                    listener.statusChanged(this, status);
                });
            }
        }
    }

    private boolean sendFrame(ByteBuffer msg){
        boolean isSend = false;
        try {
            socketChannel.write(msg);
            isSend = true;
        } catch (IOException ex) {
//            logger.log(Level.SEVERE, "Can't send message to RE %s", getHostName());
        }
        
        return isSend;
    }
    
    private void sendInitCommand(Retalk2Command command){
        executorService.submit(() -> {
            if(!getStatus().equals(Status.PreConnected)){
                onSendCommandRejected(command);
                return;
            }
            addCommandToQue(command);
        });
    }
    
    private void sendCommand(Retalk2Command command){
        executorService.submit(() -> {
            if(!getStatus().equals(Status.Connected)){
                onSendCommandRejected(command);
                return;
            }

            addCommandToQue(command);
        });
    }

    private void addCommandToQue(Retalk2Command command) {
        commandId++;
        commandQue.put(commandId, command);
        Retalk2CommandEncoder encoder = new Retalk2CommandEncoder();
        byte[] cmdData = command.getData();
        byte[] cmdHead = String.format("RE_FRAME(%d,%d,%d,%d,%d)",
                cmdData.length, commandId, -1, 0,
                clientId).getBytes(StandardCharsets.UTF_8);
        encoder.add(cmdHead);
        encoder.add(cmdData);
        
        if(sendFrame(encoder.getDataBuffer()))
            command.setSended();
        else
            onSendCommandFailed(command);
    }
    
    private void sendHello(){        
        sendInitCommand(new Retalk2CommandHello(this));
    }
    
    private void sendGetCanvases(){
        sendInitCommand(new Retalk2CommandGetCanvases(this));
    }
    
    private void sendSetCanvas(int index){
        sendInitCommand(new Retalk2CommandSetCanvas(this, index));
    }
    
    void onSendCommandRejected(Retalk2Command command){
        Object[] params = {command};
        log(Level.WARNING, "Command rejected {0}", params);
    }
    
    void onSendCommandFailed(Retalk2Command command){
        Object[] params = {command};
        log(Level.WARNING, "Command failed {0}", params);
    }
    
    void onAnimationControllFailed(String sceneName, String animationName, Retalk2CommandAnimationControll.AnimationAction action) {
        log(Level.WARNING, "Animation \""+animationName+"\" of scene \""+sceneName+"\" \""+action.toString()+"\" failed");
    }

    void onAnimationControllDone(String sceneName, String animationName, Retalk2CommandAnimationControll.AnimationAction action) {
        log(Level.FINEST, "Animation \""+animationName+"\" of scene \""+sceneName+"\" \""+action.toString()+"\" done");
    }

    void onGetLoadedScenesError() {
        log(Level.SEVERE, "Get loaded scenes failed");
    }

    void onSceneActivationDone(String sceneName, int slot) {
        scenesModel.onSceneActivated(sceneName, slot);
    }

    void onSceneActivationFailed(String sceneName, int slot) {
        log(Level.WARNING, "Activation of scene \""+sceneName+"\" on slot\""+Integer.toString(slot)+"\" failed");
    }

    void onSceneLoadDone(String sceneName) {
        scenesModel.onSceneLoaded(sceneName);
    }

    void onSceneLoadFailed(String sceneName) {
        scenesModel.onSceneNotLoaded(sceneName);
    }

    void onSceneUnloadDone(String sceneName) {
        scenesModel.onSceneUnLoaded(sceneName);
    }

    void onSceneUnloadFailed(String sceneName) {
        log(Level.SEVERE, "Scene \""+sceneName+"\" unload failed");
    }

    void onSlotDeactivationDone(int slot) {
        scenesModel.onSlotDeactivated(slot);
    }

    void onSlotDeactivationFailed(int slot) {
        log(Level.SEVERE, "Slot \""+Integer.toString(slot)+"\" deactivation failed");
    }

    void onActivatedScenesRecived(ArrayList<String> scenes) {
        scenesModel.onActivatedScenesRecived(scenes);
    }

    void onGetActiveScenesError() {
        log(Level.SEVERE, "Get active scenes failed");
    }

    void onLodedScenesRecived(ArrayList<String> scenes) {
        scenesModel.onLodedScenesRecived(scenes);
    }
 
    void onInputParserBufferEnded() {
        log(Level.SEVERE, "Input parser buffer is full");
    }
    
    SocketChannel getSocketChannel() {
        return socketChannel;
    }
    
    int getCanvasIndex() {
        return canvasIndex;
    }
    
    void onReciveFrame(int requestID, byte[] data){
        Retalk2Command command = commandQue.get(requestID);
        if(command != null) {
            command.addResponce(data);
            commandQue.remove(requestID);
        }
    }
    
    void onRecivedClientID(int clientId) {
        this.clientId = clientId;
        log(Level.FINEST, "Recived client ID: "+Integer.toString(clientId));
        if(canvasName.isEmpty()){
            setStatus(Status.Connected);
            sendGetLoadedScenes();
            sendGetActivatedScenes();
        } else
            sendGetCanvases();
    }
    
    void onReciveCanvasSet(){
        log(Level.FINEST, "Canvas set to \""+canvasName+"\"");
        setStatus(Status.Connected);
        sendGetLoadedScenes();
        sendGetActivatedScenes();
    }
    
    void oReciveCanvasNotSet(){
        log(Level.SEVERE, "Canvas didn't set");
        disconnect();
    }
    
    void onReciveCanvases(ArrayList<String> canvases){
        int index = canvases.indexOf(canvasName);
        if(index == -1){
            log(Level.SEVERE, "No canvas \""+canvasName+"\" in RE");
            disconnect();
            return;
        }
        canvasIndex = index;
        sendSetCanvas(index);
    }
    
    public Retalk2ConnectionController(){
        super();
    }
    
    @Override
    protected String getNameForLogger(){
        if(canvasName.isEmpty())
            return String.format(NAMEFORMAT, hostName);
        else
            return String.format(NAMEWITHCANVASFORMAT, hostName, canvasName);
    }
    
    @Override
    public void sendSetExport(String sceneName, String exportName, String exportValue){
        sendCommand(new Retalk2CommandSetExport(this, sceneName, exportName, exportValue));
    }
    
    @Override
    public void sendAnimationPlay(String sceneName, String animationName){
        sendCommand(new Retalk2CommandAnimationControll(this, sceneName, 
                animationName, Retalk2CommandAnimationControll.AnimationAction.Play));
    }
    
    @Override
    public void sendAnimationStop(String sceneName, String animationName){
        sendCommand(new Retalk2CommandAnimationControll(this, sceneName, 
                animationName, 
                Retalk2CommandAnimationControll.AnimationAction.Stop));
    }
    
    @Override
    public void sendAnimationRewind(String sceneName, String animationName){
        sendCommand(new Retalk2CommandAnimationControll(this, sceneName, 
                animationName, 
                Retalk2CommandAnimationControll.AnimationAction.Rewind));
    }
    
    @Override
    public void sendActivateScene(String sceneName, int slot){
        if(canvasIndex == NOCANVASINDEX)
            sendCommand(new Retalk2CommandAcivateScene(this, sceneName, slot));
        else
            sendCommand(new Retalk2CommandCanvasActivateScene(this, sceneName, slot));
    }
    
    @Override
    public void sendDeactivateSlot(int slot){
        sendCommand(new Retalk2CommandDeactivateSlot(this, slot));
    }
    
    @Override
    public void sendLoadScene(String sceneName){
        if(canvasIndex == NOCANVASINDEX)
            sendCommand(new Retalk2CommandLoadScene(this, sceneName));
        else
            sendCommand(new Retalk2CommandCanvasLoadScene(this, sceneName));
    }
    
    @Override
    public void sendUnLoadScene(String sceneName){
        if(canvasIndex == NOCANVASINDEX)
            sendCommand(new Retalk2CommandUnloadScene(this, sceneName));
        else
            sendCommand(new Retalk2CommandCanvasUnloadScene(this, sceneName));
    }

    @Override
    public void sendGetLoadedScenes(){
        sendCommand(new Retalk2CommandGetLoadedScenes(this));
    }
    
    @Override
    public void sendGetActivatedScenes(){
        sendCommand(new Retalk2CommandGetActiveScenes(this));
    }

    @Override
    public boolean isConnected() {
        return getStatus().equals(Status.Connected);            
    }

    @Override
    public void connect(){
        setStatus(Status.Connecting);
        commandQue.clear();
        clientId = -1;
        commandId = -1;
        canvasIndex = -1;
        
        connectionThread = new Thread(() -> {
            try {
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                socketChannel.connect(new InetSocketAddress(hostName, port));
                
                while (!socketChannel.finishConnect()) {
                    Thread.sleep(10);
                }    
                
                if(!socketChannel.isConnected())
                    throw new IOException(String.format("RE \"%s\" connection failed", hostName));
                
                inputParser = new Retalk2ConnectionInputParser(this);
                inputParser.start();
                
                setStatus(Status.PreConnected);
                
                sendHello();
            } catch (IOException | InterruptedException ex) {
                try {
                    if(ex instanceof InterruptedException)
                        log(Level.INFO, "Connection interrupted");
                    else
                        log(Level.SEVERE, "Connection failed", ex);
                    socketChannel.close();
                } catch (IOException ex1) {
                    log(Level.SEVERE, "Socket close failed", ex1);
                } finally {
                    setStatus(Status.Disconnected);
                }
            }
        });
        connectionThread.start();
    }

    @Override
    public void disconnect() {
        if(socketChannel != null && socketChannel.isOpen()){
            try {
                socketChannel.close();
            } catch (IOException ex) {
                log(Level.SEVERE, "Socket close failed", ex);
            }
        }
        
        if(connectionThread != null)
            connectionThread.interrupt();
        
        if(inputParser != null)
            inputParser.interrupt();
        
        socketChannel = null;
        connectionThread = null;
        inputParser = null;
        setStatus(Status.Disconnected);
    }  

    @Override
    public Status getStatus() {
        synchronized(statusLock){
            return status;
        }
    }

    @Override
    public int getPort() {
        synchronized(portLock){
            return port;
        }
    }

    @Override
    public void setPort(int port) {
        boolean isChanged = false;
        synchronized(portLock){
            if(getStatus().equals(Status.Disconnected) && this.port != port){
                this.port = port;
                isChanged = true;
            }
        }
        if(isChanged){
            synchronized(listeners){
                listeners.stream().forEach((listener) -> {
                    listener.portChanged(this, port);
                });
            }
        }
    }

    @Override
    public String getHostName() {
        synchronized(hostnameLock){
            return hostName;
        }
    }

    @Override
    public void setHostName(String hostName) {
        boolean isChanged = false;
        synchronized(hostnameLock){
            if(getStatus().equals(Status.Disconnected) && !this.hostName.equals(hostName)){
                this.hostName = hostName;
                isChanged = true;
            }
        }
        if(isChanged){
            synchronized(listeners){
                listeners.stream().forEach((listener) -> {
                    listener.hostNameChanged(this, hostName);
                });
            }
        }
    }

    @Override
    public String getCanvasName() {
        synchronized(canvasNameLock){
            return canvasName;
        }
    }

    @Override
    public void setCanvasName(String canvasName) {
        boolean isChanged = false;
        synchronized(canvasNameLock){
            if(getStatus().equals(Status.Disconnected) && !this.canvasName.equals(canvasName)){
                this.canvasName = canvasName;
                isChanged = true;
            }
        }
        if(isChanged){
            synchronized(listeners){
                listeners.stream().forEach((listener) -> {
                    listener.canvasChanged(this, canvasName);
                });
            }
        }
    }

    @Override
    public void refeshREStatus() {
        if(!getStatus().equals(Status.Connected)){
            log(Level.CONFIG, "Can't refresh: not connected");
            return;
        }
        
        sendGetLoadedScenes();
        sendGetActivatedScenes();
    }

    @Override
    public IREConnectionModel getConnectionModel() {
        return this;
    }

    @Override
    public void addListener(IREConnectionModelListener listener) {
        synchronized(listeners){
            if(!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    @Override
    public void removeListener(IREConnectionModelListener listener) {
        synchronized(listeners){
            listeners.remove(listener);
        }
    }

}
