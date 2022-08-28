/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad;

/**
 *
 * @author Oleg Voronov
 */
public class ScenesModelController {
    private final AbstractREConnectionController connectionController;

    ScenesModelController(AbstractREConnectionController connectionController) {
        this.connectionController = connectionController;
    }
    
    IScenesModel getScenesModel(){
        return connectionController.getScenesModel();
    }
    
    Scene getScene(String sceneName){
        return connectionController.getScenesModel().getScene(sceneName);
    }
    
    public void setExport(String sceneName, String name, String value){
        if(getScene(sceneName).getStatus().equals(Scene.Status.Loaded)){
            connectionController.sendSetExport(sceneName, name, value);
        }
    }
    
    public void load(String sceneName){
        if(getScene(sceneName).getStatus().equals(Scene.Status.Unloaded)){
           connectionController.sendLoadScene(sceneName);
        }
    }
    
    public void unload(String sceneName){
        if(getScene(sceneName).getStatus().equals(Scene.Status.Loaded)){
           connectionController.sendUnLoadScene(sceneName);
        }
    }
    
    public void activate(String sceneName, int slot){
        if(getScene(sceneName).getStatus().equals(Scene.Status.Loaded)){
           connectionController.sendActivateScene(sceneName, slot);
        }
    }
    
    public void playAnimation(String sceneName, String name){
        if(getScene(sceneName).getStatus().equals(Scene.Status.Loaded)){
           connectionController.sendAnimationPlay(sceneName, name);
        }
    }
    
    public void deactivate(String sceneName){
        if(getScene(sceneName).isActivated()){
           connectionController.sendDeactivateSlot(getScene(sceneName).getSlot());
        }
    }
}
