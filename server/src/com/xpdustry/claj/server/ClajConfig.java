package com.xpdustry.claj.server;

import com.xpdustry.claj.server.util.JsonSettings;

import arc.struct.Seq;
import arc.util.Log;


public class ClajConfig {
  public static final String fileName = "config.json";
  
  protected static JsonSettings settings;
  
  /** Limit for packet count sent within 3 sec that will lead to a disconnect. Note: only for clients, not hosts. */
  public static int spamLimit = 500;
  /** Warn a client that trying to create a room, that it's CLaJ version is deprecated. */
  public static boolean warnDeprecated = true;
  /** Warn all clients when the server is closing */
  public static boolean warnClosing = true;
  /** Simple ip blacklist */
  public static Seq<String> blacklist = new Seq<>();

  
  /** Load settings file and load values */
  @SuppressWarnings("unchecked")
  public static void load() {
    if (settings == null) {
      settings = new JsonSettings(new arc.files.Fi(fileName, arc.Files.FileType.local));
      
      // Start an autosave timer each minutes
      arc.util.Timer.schedule(() -> {
        try { 
          if (settings.modified()) {
            forcesave();
            Log.info("Settings saved.");
          }
        } catch (RuntimeException e) { Log.err("Failed to load settings", e); }
      }, 60, 60);  
    }
      
    settings.load();
    
    // Load values
    spamLimit = settings.getInt("spam-limit", spamLimit);
    warnDeprecated = settings.getBool("warn-deprecated", warnDeprecated);
    warnClosing = settings.getBool("warn-closing", warnClosing);
    blacklist = settings.get("blacklist", Seq.class, String.class, blacklist);
  }
  
  /** Save values */
  public static void save() {
    settings.put("spam-limit", spamLimit);
    settings.put("warn-deprecated", warnDeprecated);
    settings.put("warn-closing", warnClosing);
    settings.put("blacklist", String.class, blacklist);
  }
  
  /** Save values and write settings file */
  public static void forcesave() {
    save();
    settings.save();
  }
}
