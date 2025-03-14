package com.xpdustry.claj.client;


public class Main extends mindustry.mod.Mod {
  @Override
  public void init() {
    CLaJ.init();
    //CLaJServers.refresh(); //will be done when opening the room window
    
    new JoinViaClajDialog();
    new CreateClajRoomDialog();
  }
}
