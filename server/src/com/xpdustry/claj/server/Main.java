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
  public static String serverVersion;
  
  public static void main(String[] args) {
    try {
      // Ignore connection reset, closed and broken errors
      ArcNet.errorHandler = e -> { 
        if (Strings.getFinalCause(e) instanceof java.net.SocketException) return;
        String m = e.getMessage().toLowerCase();
        if (m.contains("reset") || m.contains("closed") || m.contains("broken pipe")) return;
        Log.err(e); 
      };
      Log.logger = (level, text) -> {
        for (String line : text.split("\n")) {
          //err has red text instead of reset.
          if(level == Log.LogLevel.err) line = line.replace(ColorCodes.reset, ColorCodes.lightRed + ColorCodes.bold);
    
          line = Log.format(Strings.format(logFormat, dateformat.format(LocalDateTime.now()), tags[level.ordinal()], line));
          System.out.println(line);          
        }
      };
      Log.formatter = (text, useColors, arg) -> {
        text = Strings.format(text.replace("@", "&fb&lb@&fr"), arg);
        return useColors ? Log.addColors(text) : Log.removeColors(text);
      };  

      // Parse server port
      if (args.length == 0) throw new RuntimeException("Need a port as an argument!");
      int port = Integer.parseInt(args[0]);
      if (port < 0 || port > 0xffff) throw new RuntimeException("Invalid port range");
      
      // Get the server version from manifest
      try { 
        serverVersion = new java.util.jar.Manifest(Main.class.getResourceAsStream("/META-INF/MANIFEST.MF"))
                                         .getMainAttributes().getValue("Claj-Version");
      } catch (Exception e) {
        throw new RuntimeException("Unable to locate manifest properties.", e);
      }
      // Fallback to java property
      if (serverVersion == null) serverVersion = System.getProperty("Claj-Version");
      if (serverVersion == null) throw new RuntimeException("The 'Claj-Version' property is missing in the jar manifest.");
      
      // Load settings and init server
      ClajConfig.load();
      Log.level = ClajConfig.debug ? Log.LogLevel.debug : Log.LogLevel.info; // set log level
      relay = new ClajRelay();
      try { relay.bind(port, port); } 
      catch (Exception e) { throw new RuntimeException(e); }
      // Register commands
      control = new ClajControl(relay);
      
      Log.info("Server loaded and hosted on port @. Type @ for help.", port, "'help'");    

    } catch (Throwable t) {
      Log.err("Failed to load server", t);
      System.exit(1);
      return;
    }
    
    // Start the server
    try { relay.run(); } 
    catch (Throwable t) { Log.err(t); } 
    finally {
      relay.close();
      ClajConfig.save();
      Log.info("Server closed.");
    }
  }
}
