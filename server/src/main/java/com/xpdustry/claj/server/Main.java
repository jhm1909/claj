package com.xpdustry.claj.server;

import arc.net.ArcNet;
import arc.util.Log;
import arc.util.Strings;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Main {
  public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
  public static final DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  public static final String logFormat = "&lk&fb[@]&fr @ @&fr";
  
  public static ClajRelay distributor;
  public static Control control;

  public static void main(String[] args) {
    // Ignore "connection closed" error
    ArcNet.errorHandler = e -> { if (!e.getMessage().toLowerCase().contains("connection is closed")) Log.err(e); };
    Log.logger = (level, text) -> {
      text = Log.format(Strings.format(logFormat, dateformat.format(LocalDateTime.now()), tags[level.ordinal()], text));
      System.out.println(text);
    };
    Log.formatter = (text, useColors, arg) -> {
      text = Strings.format(text.replace("@", "&fb&lb@&fr"), arg);
      return useColors ? Log.addColors(text) : Log.removeColors(text);
    };

    try {
      if (args.length == 0) throw new RuntimeException("Need a port as an argument!");
      
      distributor = new ClajRelay();
      distributor.init(Integer.parseInt(args[0]));
      control = new Control(distributor);
      Log.info("Server loaded. Type @ for help.", "'help'");
    } catch (Throwable error) {
      Log.err("Unable to load the redirect system", error);
    }
    
    try { 
      distributor.run(); 
    } catch (Throwable e) {
      Log.err("");
      Log.err("!!!!!!!!!! FATAL ERROR !!!!!!!!!!");
      Log.err(e);
      Log.err("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
      Log.err("");
    }
  }
}
