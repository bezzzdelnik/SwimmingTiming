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
public interface IREConnectionModel {
    public enum Status {
        Disconnected,
        Connecting,
        PreConnected,
        Connected,
        LoadingScene
    }
 
    public String getCanvasName();

    public String getHostName();

    public int getPort();

    public Status getStatus();

    public void setCanvasName(String canvasName);

    public void setHostName(String hostName);

    public void setPort(int port);
    
    public boolean isConnected();

    public void addListener(IREConnectionModelListener listener);
    
    public void removeListener(IREConnectionModelListener listener);
}
