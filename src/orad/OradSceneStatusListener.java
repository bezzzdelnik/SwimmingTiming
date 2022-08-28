/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad;

/**
 *
 * @author org.ovrn
 */
public interface OradSceneStatusListener {
    
    public void loadStatusChanged(Scene.Status status);
    public void slotActivationChanged(int slot);
}
