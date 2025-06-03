package com.xpdustry.claj.server;

import arc.struct.IntMap;
import arc.struct.LongMap;
import arc.util.Log;

import java.util.Scanner;

import com.xpdustry.claj.server.util.Strings;


public class ClajControl extends arc.util.CommandHandler {
  public ClajControl(ClajRelay server) {
    super("");
    registerCommands(server);
    
    arc.util.Threads.daemon("Server Control", () -> {
      try (Scanner scanner = new Scanner(System.in)) {
        while (scanner.hasNext()) {
          try { handleCommand(scanner.nextLine()); }
          catch (Throwable e) { Log.err(e); }
        }
      }
    });
    
    // Why the JVM throwing me a NoClassDefFoundError when i stop the server, if i remove that?
    ResponseType.values();
    new CommandResponse(null, null, null);
  }

  public void handleCommand(String line){
    CommandResponse response = handleMessage(line);

    if (response.type == ResponseType.unknownCommand) {
      int minDst = 0;
      Command closest = null;

      for (Command command : getCommandList()) {
        int dst = Strings.levenshtein(command.text, response.runCommand);
        if (dst < 3 && (closest == null || dst < minDst)) {
          minDst = dst;
          closest = command;
        }
      }

      if (closest != null) Log.err("Command not found. Did you mean \"" + closest.text + "\"?");
      else Log.err("Invalid command. Type 'help' for help.");
    }
    else if(response.type == ResponseType.fewArguments)
      Log.err("Too few command arguments. Usage: " + response.command.text + " " + response.command.paramText);
    else if(response.type == ResponseType.manyArguments)
      Log.err("Too many command arguments. Usage: " + response.command.text + " " + response.command.paramText);
  }


  void registerCommands(ClajRelay server) {
    register("help", "Display the command list.", args -> {
      Log.info("Commands:");
      getCommandList().each(c -> 
        Log.info("&lk|&fr &b&lb" + c.text + (c.paramText.isEmpty() ? "" : " &lc&fi") + c.paramText + 
                 "&fr - &lw" + c.description));
    });
    
    register("version", "Displays server version info.", arg -> {
      Log.info("Version: @", Main.serverVersion);
      Log.info("Java Version: @", arc.util.OS.javaVersion);
  });
    
    register("gc", "Trigger a garbage collection.", arg -> {
      int pre = (int)((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);
      System.gc();
      int post = (int)((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);
      Log.info("@ MB collected. Memory usage now at @ MB.", pre - post, post);
    });
    
    register("exit", "Stop the server.", args -> {
      Log.info("Shutting down CLaJ server.");
      server.stop();
    });
    
    register("debug", "[on|off]", "Enable/Disable the debug log level.", args -> {
      if (args.length == 0) Log.info("Debug log level is @.", ClajConfig.debug ? "enabled" : "disabled");
      
      else if (Strings.isFalse(args[0])) {
        Log.level = Log.LogLevel.info;
        ClajConfig.debug = false;
        ClajConfig.save();
        Log.info("Debug log level disabled.");
        
      } else if (Strings.isTrue(args[0])) {
        Log.level = Log.LogLevel.debug;
        ClajConfig.debug = true;
        ClajConfig.save();
        Log.info("Debug log level enabled.");
        
      } else Log.err("Invalid argument.");
    });

    register("rooms", "Displays created rooms.", args -> {
      if (server.rooms.isEmpty()) {
        Log.info("No created rooms.");
        return;
      }
      
      Log.info("Rooms: [total: @]", server.rooms.size);
      for (ClajRoom r : new LongMap.Values<>(server.rooms)) {
        Log.info("&lk|&fr Room @:", r.idString);
        Log.info("&lk| |&fr [H] Connection @&fr - @", Strings.conIDToString(r.host), Strings.getIP(r.host));
        for (arc.net.Connection c : new IntMap.Values<>(r.clients))
          Log.info("&lk| |&fr [C] Connection @&fr - @", Strings.conIDToString(c), Strings.getIP(c));
        Log.info("&lk|&fr");
      }
    });

    register("limit", "[amount]", "Sets spam packet limit. (0 to disable)", args -> {
      if (args.length == 0) {
        if (ClajConfig.spamLimit == 0) Log.info("Current limit: disabled.");
        else Log.info("Current limit: @ packets per 3 seconds.", ClajConfig.spamLimit);
      } else {
        int limit = Strings.parseInt(args[0]);
        if (limit < 0) {
          Log.err("Invalid input.");
          return;
        }
        ClajConfig.spamLimit = limit;
        if (ClajConfig.spamLimit == 0) Log.info("Packet spam limit disabled.");
        else Log.info("Packet spam limit set to @ packets per 3 seconds.", ClajConfig.spamLimit);
        ClajConfig.save();
      }
    });

    register("blacklist", "[add|del] [IP]", "Manages the IP blacklist.", args -> {
      if (args.length == 0) {
        if (ClajConfig.blacklist.isEmpty()) Log.info("Blacklist is empty.");
        else {
          Log.info("Blacklist:");
          ClajConfig.blacklist.each(ip -> Log.info("&lk|&fr IP: @", ip));  
        }

      } else if (args.length == 1) {
        Log.err("Missing IP argument.");

      } else if (args[0].equals("add")) {
        if (ClajConfig.blacklist.addUnique(args[0])) {
          ClajConfig.save();
          Log.info("IP added to blacklist.");
        } else Log.err("IP already blacklisted.");
        
      } else if (args[0].equals("del")) {
        if (ClajConfig.blacklist.remove(args[0])) {
          ClajConfig.save();
          Log.info("IP removed from blacklist.");  
        } else Log.err("IP not blacklisted.");
        
      } else Log.err("Invalid argument. Must be 'add' or 'del'.");
    });

    register("warn-deprecated", "[on|off]", "Warn the client if it's CLaJ version is obsolete.", args -> {
      if (args.length == 0) 
        Log.info("Warn message when a client using an obsolete CLaJ version: @.", 
                 ClajConfig.warnDeprecated ? "enabled" : "disabled");

      else if (Strings.isFalse(args[0])) {
        ClajConfig.warnDeprecated = false;
        ClajConfig.save();
        Log.info("Warn message disabled.");
        
      } else if (Strings.isTrue(args[0])) {
        ClajConfig.warnDeprecated = true;
        ClajConfig.save();
        Log.info("Warn message enabled.");
        
      } else Log.err("Invalid argument.");
    });
    
    register("warn-closing", "[on|off]", "Warn all rooms when the server is closing.", args -> {
      if (args.length == 0) 
        Log.info("Warn message when closing the server: @.", 
                 ClajConfig.warnClosing ? "enabled" : "disabled");

      else if (Strings.isFalse(args[0])) {
        ClajConfig.warnClosing = false;
        ClajConfig.save();
        Log.info("Warn message disabled.");
        
      } else if (Strings.isTrue(args[0])) {
        ClajConfig.warnClosing = true;
        ClajConfig.save();
        Log.info("Warn message enabled.");
        
      } else Log.err("Invalid argument.");
    });
    
    register("say", "<room|all> <text...>", "Send a message to a room or all rooms.", args -> {
        if (args[0].equals("all")) {
          for (ClajRoom r : new LongMap.Values<>(server.rooms)) r.message(args[1]);
          Log.info("Message sent to all rooms.");
          return;
        }
        
        try {
          ClajRoom room = server.get(Strings.base64ToLong(args[0]));
          if (room != null) {
            room.message(args[1]);
            Log.info("Message sent to room @.", args[0]);
          } else Log.err("Room @ not found.", args[0]);
        } catch (Exception ignored) { Log.err("Room @ not found.", args[0]); }      
      });
    }
  }
