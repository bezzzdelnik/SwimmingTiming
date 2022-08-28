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
public class SceneController {
    private final ScenesModelController scenesModelController;
    private final String sceneName;
    
    SceneController(ScenesModelController scenesModelController, String sceneName) {
        this.scenesModelController = scenesModelController;
        this.sceneName = sceneName;
    }
    
    public void setExport(String name, String value){
        scenesModelController.setExport(sceneName, name, value);
    }
    
    public void load(){
        scenesModelController.load(sceneName);
    }
    
    public void unload(){
        scenesModelController.unload(sceneName);
    }
    
    public void activate(int slot){
        scenesModelController.activate(sceneName, slot);
    }
    
    public void playAnimation(String name){
        scenesModelController.playAnimation(sceneName, name);
    }
    
    public void deactivate(){
        scenesModelController.deactivate(sceneName);
    }
}
