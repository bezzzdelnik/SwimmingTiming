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
public interface IScenesModel {

    void addListener(IScenesModelListener listener);

    void addPreloadedScene(String name);

    Scene getScene(String name);
    
    Scene getScene(int index);

    int getScenesCount();

    void removeListener(IScenesModelListener listener);

    void removePreloadedScene(String name);
    
}
