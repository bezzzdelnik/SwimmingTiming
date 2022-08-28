package orad;

///*
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
//package ru.rgd.orad.retalk2;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//
///**
// *
// * @author org.ovrn
// */
//public class OradScenesList extends ArrayList<OradScene>{
//    
//    public OradScenesList() {
//        super();
//    }
//    
//    OradScene findScene(String sceneName){
//        for (Iterator<OradScene> iterator = this.iterator(); iterator.hasNext();) {
//            OradScene next = iterator.next();
//            if(next.getName().equals(sceneName))
//                return next;
//        }
//        
//        return null;
//    }
//    
//    OradScene findOrCreateScene(String sceneName, Retalk2Interface retalkInerface) {
//        OradScene scene = findScene(sceneName);
//        if(scene == null) {
//            scene = new OradScene(sceneName, retalkInerface);
//            this.add(scene);
//        }        
//        
//        return scene;
//    }
//    
//    OradScene findSceneBySlot(int slot) {
//        for (Iterator<OradScene> iterator = this.iterator(); iterator.hasNext();) {
//            OradScene next = iterator.next();
//            if(next.getSlot() == slot)
//                return next;
//        }
//        
//        return null;
//    }
//}
