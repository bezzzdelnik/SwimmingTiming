/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package consoles;

import java.io.*;
import java.util.Arrays;



/**
 *
 * @author org.ovrn
 */
public class Console implements ConsolePrinter {
    protected final CommandsMap commandRootMap = new CommandsMap();
    protected final CommandsMap commandSetMap = new CommandsMap();
    protected final CommandsMap commandGetMap = new CommandsMap();
    private final BufferedReader inBuffer;
    private final PrintStream outStream;

    public Console(InputStream inputStream, PrintStream printStream) {
        inBuffer = new BufferedReader(new InputStreamReader(inputStream));
        outStream = printStream;
        commandRootMap.put("set", this::commandSet);
        commandRootMap.put("get", this::commandGet);
    }

    @Override
    public void println(String msg) {
        outStream.println(msg);
    }

    @Override
    public void println(Object obj) {
        outStream.println(obj);
    }

    @Override
    public void println(String format, Object... args) {
        outStream.println(String.format(format, args));
    }

    public void runPromt() {
        String userInput;
        try {
            while ((userInput = this.inBuffer.readLine()) != null) {
                String[] commandWithArgs = userInput.split(" ");
                parseCommand(commandWithArgs, commandRootMap);
            }
        } catch (IOException ex) {
            println("Can't read console with exception:");
            println(ex);
        }
        println("Session end");
    }

    protected void parseCommand(String[] commandWithArgs, CommandsMap commandsMap) {
        String command = commandWithArgs[0];
        String[] commandsArgs;
        if (commandWithArgs.length > 1) {
            commandsArgs = Arrays.copyOfRange(commandWithArgs, 1, commandWithArgs.length);
        } else {
            commandsArgs = new String[0];
        }
        CommandConsumer commandConsumer = commandsMap.get(command);
        if (commandConsumer == null) {
            println("Unknown command");
            return;
        }
        try {
            commandConsumer.accept(commandsArgs);
        } catch (ArrayIndexOutOfBoundsException ex) {
            println("Not enouth arguments");
            println(ex);
        }
    }
    
    private void commandSet(String[] commandArgs){
        parseCommand(commandArgs, commandSetMap);
    }
    
    private void commandGet(String[] commandArgs){
        parseCommand(commandArgs, commandGetMap);
    }
}
