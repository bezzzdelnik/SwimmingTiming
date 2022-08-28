/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.console;

import consoles.CommandsMap;
import consoles.Console;
import orad.AbstractREConnectionController;
import orad.IScenesModel;
import orad.Scene;
import orad.retalk2.Retalk2ConnectionController;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

/**
 *
 * @author org.ovrn
 */

public final class CommandPrompt extends Console {
    private final CommandsMap commandSendMap = new CommandsMap();
    private final AbstractREConnectionController connectionController;

    public CommandPrompt(AbstractREConnectionController connectionController, InputStream inputStream, PrintStream printStream) {
        super(inputStream, printStream);
        this.connectionController = connectionController;
        prepareCommands();
    }
    
    public static void main(String[] args) {
        CommandPrompt commandPromt = new CommandPrompt(new Retalk2ConnectionController(), System.in, System.out);
        Logger.getLogger(AbstractREConnectionController.class.getName()).addHandler(new StreamHandler(System.out, new SimpleFormatter()));
        commandPromt.connectionController.getConnectionModel().setHostName("localhost");
        commandPromt.connectionController.getConnectionModel().setCanvasName("Canvas1");
        commandPromt.commandRootMap.put("exit", (String[] arg) -> System.exit(0));
        commandPromt.runPromt();
    }
    
    void commandGetScenes(String[] commandArgs) {
        printScenesStatus(connectionController.getScenesModel());
    }

    private void printScenesStatus(IScenesModel scenesModel) {
        if (scenesModel.getScenesCount() == 0) {
            println("There is no scenes");
            return;
        }
        println("Scenes count:%d", scenesModel.getScenesCount());
        for (int i = 0; i < scenesModel.getScenesCount(); i++) {
            Scene scene = scenesModel.getScene(i);
            println("%s %s %s", scene.getStatus().toString(), scene.isActivated() ? String.format("%03d", scene.getSlot()) : "---", scene.getName());
        }
    }
    
    void commandConnect(String[] commandArgs) {
        try{
            connectionController.getConnectionModel().setHostName(commandArgs[0]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            println("No hostname specified, using previous: %s", connectionController.getConnectionModel().getHostName());
        }
        connectionController.connect();
    }
    
    void commandDisconnect(String[] commandArgs) {
        connectionController.disconnect();
    }
    
    void commandGetLoadedScenes(String[] commandArgs) {
        connectionController.sendGetLoadedScenes();
    }
    
    void commandGetActiveScenes(String[] commandArgs) {
        connectionController.sendGetActivatedScenes();
    }

    void commandSendExport(String[] commandArgs){
        String sceneName = commandArgs[0];
        String exportName = commandArgs[1];
        String value = commandArgs[2];
        connectionController.sendSetExport(sceneName, exportName, value);
    }
    
    void commandSend(String[] commandArgs){
        parseCommand(commandArgs, commandSendMap);
    }
    
    void commandLoadScene(String[] commandArgs) {
        String sceneName = commandArgs[0];
        connectionController.sendLoadScene(sceneName);
    }
    
    void commandUnLoadScene(String[] commandArgs) {
        String sceneName = commandArgs[0];
        connectionController.sendUnLoadScene(sceneName);
    }
    
    void commandActivateScene(String[] commandArgs) {
        String sceneName = commandArgs[0];
        int slot = Integer.parseInt(commandArgs[1]);
        connectionController.sendActivateScene(sceneName,slot);
    }
    
    void commandAnimationPlay(String[] commandArgs) {
        String sceneName = commandArgs[0];
        String animation = commandArgs[1];
        connectionController.sendAnimationPlay(sceneName,animation);
    }
    
    void commandAnimationPause(String[] commandArgs) {
        String sceneName = commandArgs[0];
        String animation = commandArgs[1];
        connectionController.sendAnimationPause(sceneName,animation);
    }
    
    void commandAnimationContinue(String[] commandArgs) {
        String sceneName = commandArgs[0];
        String animation = commandArgs[1];
        connectionController.sendAnimationContinue(sceneName,animation);
    }
    
    void commandAnimationStop(String[] commandArgs) {
        String sceneName = commandArgs[0];
        String animation = commandArgs[1];
        connectionController.sendAnimationStop(sceneName,animation);
    }
    
    void commandAnimationRewind(String[] commandArgs) {
        String sceneName = commandArgs[0];
        String animation = commandArgs[1];
        connectionController.sendAnimationRewind(sceneName,animation);
    }
    
    void commandAnimationBreakLoop(String[] commandArgs) {
        String sceneName = commandArgs[0];
        String animation = commandArgs[1];
        connectionController.sendAnimationBreakLoop(sceneName,animation);
    }
    
    void commandDeactivateSlot(String[] commandArgs) {
        int slot = Integer.parseInt(commandArgs[0]);
        connectionController.sendDeactivateSlot(slot);
    }
    
    void commandSetHostname(String[] commandArgs) throws ArrayIndexOutOfBoundsException {
        String hosthame = commandArgs[0];
        connectionController.getConnectionModel().setHostName(hosthame);
    }
    
    void commandGetHostname(String[] commandArgs){
        println(this.connectionController.getConnectionModel().getHostName());
    }

    void commandSetCanvas(String[] commandArgs) {
        try{
            String canvasName = commandArgs[0];
            connectionController.getConnectionModel().setCanvasName(canvasName);
        } catch(ArrayIndexOutOfBoundsException ex){
            println("No canvas name specified, canvas name set to \"\"");
            connectionController.getConnectionModel().setCanvasName("");
        }
    }
    
    void commandGetCanvas(String[] commandArgs){
        println(this.connectionController.getConnectionModel().getCanvasName());
    }
    
    void commandGetStatus(String[] commandArgs){
        println(this.connectionController.getConnectionModel().getStatus());
    }
    
    private void prepareCommands() {
        commandRootMap.put("connect", this::commandConnect);
        commandRootMap.put("disconnect", this::commandDisconnect);
        commandRootMap.put("send", this::commandSend);
        
        commandSendMap.put("loadscene", this::commandLoadScene);
        commandSendMap.put("unloadscene", this::commandUnLoadScene);
        commandSendMap.put("activatescene", this::commandActivateScene);
        commandSendMap.put("deactivateslot", this::commandDeactivateSlot);
        commandSendMap.put("animationplay", this::commandAnimationPlay);
        commandSendMap.put("animationstop", this::commandAnimationStop);
        commandSendMap.put("animationrewind", this::commandAnimationRewind);
        commandSendMap.put("animationpause", this::commandAnimationPause);
        commandSendMap.put("animationcontinue", this::commandAnimationContinue);
        commandSendMap.put("animationbreak", this::commandAnimationBreakLoop);
        commandSendMap.put("getloadedscenes", this::commandGetLoadedScenes);
        commandSendMap.put("getactivescenes", this::commandGetActiveScenes);
        commandSendMap.put("export", this::commandSendExport);
        
        commandSetMap.put("hostname", this::commandSetHostname);
        commandSetMap.put("canvas", this::commandSetCanvas);
        
        commandGetMap.put("hostname", this::commandGetHostname);
        commandGetMap.put("scenes", this::commandGetScenes);
        commandGetMap.put("canvas", this::commandGetCanvas);
        commandGetMap.put("status", this::commandGetStatus);
    }
}
