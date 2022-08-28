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
public interface IScenesModelListener {
    
    void sceneAdded(String name);
    
    void sceneRemoved(String name);
    
    void sceneStatusChanged(String name, Scene.Status status);
    
    void sceneSlotChanged(String name, int slot);
    
    void scenePreloadStatusChanged(String name, boolean isPreloaded);

}
