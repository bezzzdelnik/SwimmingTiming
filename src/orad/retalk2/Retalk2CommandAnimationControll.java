/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package orad.retalk2;

import java.util.EnumMap;

/**
 *
 * @author org.ovrn
 */
class Retalk2CommandAnimationControll extends Retalk2Command{
    public enum AnimationAction {
        Play,
        Stop,
        Rewind,
        Pause,
        Continue,
        BreakLoop
    }
    private final static EnumMap<AnimationAction, String> actionsMap;
    static {
        actionsMap = new EnumMap<>(AnimationAction.class);
        actionsMap.put(AnimationAction.Play, "Play");
        actionsMap.put(AnimationAction.Stop, "Stop");
        actionsMap.put(AnimationAction.Rewind, "Rewind");
        actionsMap.put(AnimationAction.Pause, "Pause");
        actionsMap.put(AnimationAction.Continue, "Continue");
        actionsMap.put(AnimationAction.BreakLoop, "Break loop");
    }
    private final String sceneName;
    private final String animationName;
    private final AnimationAction action;
    private final static byte[] FRAMEPREFIX = "\u0008Q\u0000\u0000\u0000".getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMEINFIX = ("\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\n\u000f\u0000\u0000\u0000"
            + "^AnimationGroup").getBytes(Retalk2Settings.ENCODINGNAME);
    private final static byte[] FRAMESUFFIX = ("\u0008\u00ff\u00ff\u00ff\u00ff"
            + "\u0008\u0000\u0000\u0000\u0000"
            + "\u0008\u0001\u0000\u0000\u0000"
            + "\n\u0013\u0000\u0000\u0000"
            + "^MethodModification"
            + "\n\u0000\u0000\u0000\u0000").getBytes(Retalk2Settings.ENCODINGNAME);
    
    Retalk2CommandAnimationControll(Retalk2ConnectionController connection, String nameScene, String nameAnimation, AnimationAction action) {
        super(connection);
        sceneName = nameScene;
        animationName = nameAnimation;
        this.action = action;
    }

    @Override
    protected void processData(byte[] responceData) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    byte[] getData() {
        Retalk2CommandEncoder encoder = new Retalk2CommandEncoder();
        encoder.add(FRAMEPREFIX);
        encoder.addUnicodeString(sceneName);
        encoder.add(FRAMEINFIX);
        encoder.addUnicodeString(animationName);
        encoder.add(FRAMESUFFIX);
        encoder.addAsciiString(actionsMap.get(action));       
        
        return encoder.getArray();
    }

    @Override
    protected void onRecivedError() {
        connectionModel.onAnimationControllFailed(sceneName, animationName, action);
    }

    @Override
    protected void onRecivedOk() {
        connectionModel.onAnimationControllDone(sceneName, animationName, action);
    }
    
}
