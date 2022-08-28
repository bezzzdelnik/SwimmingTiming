/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package consoles;

/**
 *
 * @author org.ovrn
 */
public interface ConsolePrinter {

    void println(String msg);

    void println(Object obj);

    void println(String format, Object... args);
    
}
