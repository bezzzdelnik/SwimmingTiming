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
public class Scene {
    public enum Status {
        Loaded,
        Unloaded,
        Loading
    }
    
    private Status status = Status.Unloaded;
    private final String name;
    private int slot = -1;
    private boolean preLoaded;
    private final Object statusLock = new Object();
    private final Object slotLock = new Object();
    private final Object preLoadLock = new Object();
    public static final int NOSLOT = -1;
    
    private Scene(String name, boolean preLoaded, Status status) {
        this.name = name;
        this.preLoaded = preLoaded;
        this.status = status;
    }
    
    private Scene(String name, boolean preLoaded) {
        this.name = name;
        this.preLoaded = preLoaded;
    }
    
    void setLoadStatus(Status status){
        synchronized(statusLock){
            this.status = status;
        }
    }
    
    void setSlot(int slot){
        synchronized(slotLock){
            this.slot = slot;
        }
    }
    
    void setPreLoaded(boolean preLoaded) {
        synchronized(preLoadLock){
            this.preLoaded = preLoaded;
        }
    }

    static Scene createScene(String name) {
        return new Scene(name, false);
    }
    
    static Scene createPreloadedScene(String name) {
        return new Scene(name, true);
    }

    static Scene createNullScene() {
        return new Scene(null, true, null);
    }
    
    public String getName() {
        return name;
    }

    public Status getStatus() {
        synchronized(statusLock){
            return status;
        }
    }

    public int getSlot() {
        synchronized(slotLock){
            return slot;
        }
    }

    public boolean isActivated() {
        synchronized(slotLock){
            return slot != -1;
        }
    }

    public boolean isPreLoaded() {
        synchronized(preLoadLock){
            return preLoaded;
        }
    }
}
