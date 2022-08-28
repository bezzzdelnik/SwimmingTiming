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
public interface IREConnectionModelListener {
    
    public void statusChanged(IREConnectionModel model, IREConnectionModel.Status status);
    
    public void hostNameChanged(IREConnectionModel model, String hostname);
    
    public void canvasChanged(IREConnectionModel model, String canvas);
    
    public void portChanged(IREConnectionModel model, int port);
}
