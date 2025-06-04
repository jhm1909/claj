package com.xpdustry.claj.server;

import com.xpdustry.claj.server.plugin.Plugins;

import arc.files.Fi;


public class ClajVars {
  public static ClajRelay relay;
  public static ClajControl control;
  public static String serverVersion;
  public static Plugins plugins;
  
  public static Fi workingDirectory = Fi.get("");
  public static Fi pluginsDirectory = workingDirectory.child("plugins");
  
}
