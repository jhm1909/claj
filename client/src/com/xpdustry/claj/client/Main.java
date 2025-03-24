package com.xpdustry.claj.client;

import com.xpdustry.claj.client.dialogs.*;


public class Main extends mindustry.mod.Mod {
  @Override
  public void init() {
    //CLaJServers.refresh(); //will be done when opening the room window
    
    new JoinViaClajDialog();
    new CreateClajRoomDialog();
  }
}
