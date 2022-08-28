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
public interface IREConnectionController {
    public void connect();
    
    public void disconnect();
    
    public void refeshREStatus();
}
