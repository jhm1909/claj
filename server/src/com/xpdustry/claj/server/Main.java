package com.xpdustry.claj.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import arc.net.ArcNet;
import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;


public class Main {
  static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
  static final DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  static final String logFormat = "&lk&fb[@]&fr @ @&fr";

  public static ClajRelay relay;
  public static ClajControl control;
  
  public static void main(String[] args) {
    try {
      // Ignore SocketException errors
      ArcNet.errorHandler = e -> { if (!(Strings.getFinalCause(e) instanceof java.net.SocketException)) Log.err(e); };
      Log.logger = (level, text) -> {
        //err has red text instead of reset.
        if(level == Log.LogLevel.err) text = text.replace(ColorCodes.reset, ColorCodes.lightRed + ColorCodes.bold);
  
        text = Log.format(Strings.format(logFormat, dateformat.format(LocalDateTime.now()), tags[level.ordinal()], text));
        System.out.println(text);
      };
      Log.formatter = (text, useColors, arg) -> {
        text = Strings.format(text.replace("@", "&fb&lb@&fr"), arg);
        return useColors ? Log.addColors(text) : Log.removeColors(text);
      };  
      // Sets log level to debug
      Log.level = Log.LogLevel.debug;
      
      // Parse server port
      if (args.length == 0) throw new RuntimeException("FATAL: Need a port as an argument!");
      int port = Integer.parseInt(args[0]);
      if (port < 0 || port > 0xffff) throw new RuntimeException("Invalid port range");
      
      // Load settings and init server
      ClajConfig.load();
      relay = new ClajRelay();
      try { relay.bind(port, port); } 
      catch (java.io.IOException e) { throw new RuntimeException(e); }
      // Register commands
      control = new ClajControl(relay);
      
      Log.info("Server hosted on port @. Type @ for help.", port, "'help'");    

    } catch (Throwable t) {
      Log.err("Failed to load server", t);
    }
    
    // Start the server
    try { relay.run(); } 
    catch (Throwable t) { Log.err(t); } 
    finally {
      relay.close();
      ClajConfig.forcesave();
      Log.info("Server closed.");
    }
  }
}
