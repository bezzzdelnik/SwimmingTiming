/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad;

import java.util.ArrayList;

/**
 *
 * @author Oleg Voronov
 */
public class ScenesModel implements IScenesModel {
    private final ArrayList<Scene> scenes = new ArrayList<>();
    private final ArrayList<IScenesModelListener> listeners = new ArrayList<>();
    
    private boolean containsScene(String name){
        synchronized(scenes){
            return scenes.stream().anyMatch((scene) -> (scene.getName().equals(name)));
        }
    }
    
    private Scene getOrCreateScene(String name){
        
        synchronized(scenes){
            for (Scene scene : scenes) {
                if(scene.getName().equals(name))
                    return scene;
            }

            Scene scene = Scene.createScene(name);
            scenes.add(scene);
            notifySceneAdded(name);
        
            return scene;
        }
    } 
    
    private Scene createIfNotExistScene(String name){
        synchronized(scenes){
            for (Scene scene : scenes) {
                if(scene.getName().equals(name))
                    return Scene.createNullScene();
            }

            Scene scene = Scene.createScene(name);
            scenes.add(scene);
            notifySceneAdded(name);
        
            return scene;
        }
    }

    private void notifySlotChanged(String name, int slot){
        listeners.stream().forEach((listener) -> {
            listener.sceneSlotChanged(name, slot);
        });
    }
    
    private void notifyStatusChanged(String name, Scene.Status status){
        listeners.stream().forEach((listener) -> {
            listener.sceneStatusChanged(name, status);
        });
    }
    
    private void notifyPreloadStatusChanged(String name, boolean isPreloaded){
        listeners.stream().forEach((listener) -> {
            listener.scenePreloadStatusChanged(name, isPreloaded);
        });
    }
    
    private void notifySceneAdded(String name){
        listeners.stream().forEach((listener) -> {
            listener.sceneAdded(name);
        });
    }
    
    private void notifySceneRemoved(String name){
        listeners.stream().forEach((listener) -> {
            listener.sceneRemoved(name);
        });
    }
    
    private void removeScene(Scene scene){
        synchronized(scenes){
            if(scenes.remove(scene))
                notifySceneRemoved(scene.getName());
        }
    }
    
    ScenesModel() {
    }
    
    public void onSceneActivated(String sceneName, int slot) {
        synchronized(scenes){
            scenes.stream().filter((scene) -> (scene.getSlot() == slot)).forEach((scene) -> {
                scene.setSlot(Scene.NOSLOT);
                notifySlotChanged(scene.getName(), Scene.NOSLOT);
            });
        }
        
        Scene scene = getOrCreateScene(sceneName);
        if(!scene.getStatus().equals(Scene.Status.Loaded)){
            scene.setLoadStatus(Scene.Status.Loaded);
            notifyStatusChanged(sceneName, Scene.Status.Loaded);
        }
        scene.setSlot(slot);
        notifySlotChanged(sceneName, slot);
    }

    public void onSceneLoaded(String sceneName) {
        Scene scene = getOrCreateScene(sceneName);
        if(!scene.getStatus().equals(Scene.Status.Loaded)){
            scene.setLoadStatus(Scene.Status.Loaded);
            notifyStatusChanged(sceneName, Scene.Status.Loaded);
        }
    }

    public void onSceneNotLoaded(String sceneName) {
        Scene scene = getOrCreateScene(sceneName);
        if(!scene.getStatus().equals(Scene.Status.Unloaded)){
            scene.setLoadStatus(Scene.Status.Unloaded);
            notifyStatusChanged(sceneName, Scene.Status.Unloaded);
        }
    }

    public void onSceneUnLoaded(String sceneName) {
        Scene scene = getOrCreateScene(sceneName);
        if(!scene.getStatus().equals(Scene.Status.Unloaded)){
            scene.setLoadStatus(Scene.Status.Unloaded);
            notifyStatusChanged(sceneName, Scene.Status.Unloaded);
        }
        if(!scene.isPreLoaded())
            removeScene(scene);
    }

    public void onSceneNotUnLoaded(String sceneName) {
    }

    public void onSlotDeactivated(Integer slot) {
        synchronized(scenes){
            scenes.stream().filter((scene) -> (scene.getSlot() == slot)).forEach((scene) -> {
                scene.setSlot(Scene.NOSLOT);
                notifySlotChanged(scene.getName(), Scene.NOSLOT);
            });
        }
    }

    public void onLodedScenesRecived(ArrayList<String> sceneNames) {
        synchronized(scenes){
            scenes.stream().forEach((Scene scene)->{
                boolean loaded = false;
                if(sceneNames.contains(scene.getName()))
                    loaded = true;

                switch(scene.getStatus()){
                    case Loaded:
                        if(!loaded){
                            scene.setLoadStatus(Scene.Status.Unloaded);
                            notifyStatusChanged(scene.getName(), Scene.Status.Unloaded);
                        }    
                        break;
                    case Unloaded:
                        if(loaded){
                            scene.setLoadStatus(Scene.Status.Loaded);
                            notifyStatusChanged(scene.getName(), Scene.Status.Loaded);
                        } 
                        break;
                    case Loading:
                        if(loaded){
                            scene.setLoadStatus(Scene.Status.Loaded);
                            notifyStatusChanged(scene.getName(), Scene.Status.Loaded);
                        } 
                        break;
                    default:
                        throw new AssertionError(scene.getStatus().name());

                }
            });
        }
        
        sceneNames.stream().forEach((sceneName) -> {            
            Scene scene = createIfNotExistScene(sceneName);
            if(scene.getName() != null){
                scene.setLoadStatus(Scene.Status.Loaded);
                notifyStatusChanged(sceneName, Scene.Status.Loaded);
            }
        });
    }

    public void onActivatedScenesRecived(ArrayList<String> slots) {
        synchronized(scenes){
            scenes.stream().forEach((scene) -> {
                int index = slots.indexOf(scene.getName());
                if(index == scene.getSlot())
                    return;

                scene.setSlot(index);
                notifySlotChanged(scene.getName(), index);
            });
        }
    }

    @Override
    public Scene getScene(String name){
        for (Scene scene : scenes) {
            if(scene.getName().equals(name))
                return scene;
        }
        
        return Scene.createNullScene();
    }
    
    @Override
    public void addListener(IScenesModelListener listener){
        synchronized(listeners){
            if(!listeners.contains(listener))
                listeners.add(listener);
        }
    }
    
    @Override
    public void removeListener(IScenesModelListener listener){
        synchronized(listeners){
            listeners.remove(listener);
        }
    }

    @Override
    public int getScenesCount(){
        return scenes.size();
    }
    
    @Override
    public void addPreloadedScene(String name){
        Scene scene = getOrCreateScene(name);
        if(!scene.isPreLoaded())
            notifyPreloadStatusChanged(name, true);
    }
    
    @Override
    public void removePreloadedScene(String name){
        Scene scene = getScene(name);
        
        if(scene.getName() == null || !scene.isPreLoaded())
            return;
        
        if(scene.getStatus().equals(Scene.Status.Unloaded))
            removeScene(scene);
        else {
            scene.setPreLoaded(false);
            notifyPreloadStatusChanged(name, true);
        }
    }

    @Override
    public Scene getScene(int index) {
        if(index < scenes.size())
            return scenes.get(index);
        else
            return Scene.createNullScene();
    }
}
