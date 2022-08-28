/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;

/**
 *
 * @author Oleg Voronov
 */
public abstract class AbstractREConnectionController implements IREConnectionController{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractREConnectionController.class.getName());
    protected static final String NAMEFORMAT = "RE %s: ";
    protected static final String NAMEWITHCANVASFORMAT = "RE %s/%s: ";
    protected final ScenesModel scenesModel;
    
    protected void log(Level level, String msg, Throwable thrown){
        LOGGER.debug(level.toString() + " " + msg, thrown);

//        log(logRecord);
    }
    
    protected void log(Level level, String msg, Object[] params){
        LOGGER.debug(level.toString() + " " + msg, params);
    }
    
    protected void log(Level level, String msg){
        LOGGER.debug(level.toString() + " " + msg);
    }
    
//    protected void log(LogRecord logRecord){
//        logRecord.setMessage(getNameForLogger()+logRecord.getMessage());
//        LOGGER.log(logRecord);
//    }
    
    protected AbstractREConnectionController(){
        this.scenesModel = new ScenesModel();
    }
    
    protected abstract String getNameForLogger();    
    
    public abstract void sendSetExport(String sceneName, String exportName, String exportValue);
    
    public abstract void sendAnimationPlay(String sceneName, String animationName);
    
    public abstract void sendAnimationStop(String sceneName, String animationName);
    
    public abstract void sendAnimationRewind(String sceneName, String animationName);
    
    public abstract void sendActivateScene(String sceneName, int slot);
    
    public abstract void sendDeactivateSlot(int slot);
    
    public abstract void sendLoadScene(String sceneName);
    
    public abstract void sendUnLoadScene(String sceneName);

    public abstract void sendGetLoadedScenes();
    
    public abstract void sendGetActivatedScenes();

    public IScenesModel getScenesModel() {
        return scenesModel;
    }
    
    public SceneController getSceneController(String sceneName) throws NullPointerException{
        Scene sceneModel = scenesModel.getScene(sceneName);
        if(sceneModel.getName() == null)
            throw new NullPointerException(String.format("Scene with name: %s not exist", sceneName));
        
        return new SceneController(new ScenesModelController(this), sceneName);
    }

    public abstract IREConnectionModel getConnectionModel();

    public void sendAnimationPause(String sceneName, String animation) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void sendAnimationContinue(String sceneName, String animation) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public void sendAnimationBreakLoop(String sceneName, String animation) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
