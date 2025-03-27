package com.xpdustry.claj.server;

import arc.util.Log;
import arc.util.serialization.Base64Coder;

import java.util.Scanner;

import com.xpdustry.claj.server.util.Strings;


public class ClajControl extends arc.util.CommandHandler {
  public ClajControl(ClajRelay server) {
    super("");
    registerCommands(server);
    
    arc.util.Threads.daemon("Server Control", () -> {
      try (Scanner scanner = new Scanner(System.in)) {
        while (scanner.hasNext()) handleCommand(scanner.nextLine());
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
      
      Log.info("Rooms:");
      server.rooms.forEach(r -> {
        Log.info("&lk|&fr Room @:", r.value.idToString());
        Log.info("&lk| |&fr [H] Connection @&fr - @", Strings.conIDToString(r.value.host), Strings.getIP(r.value.host));
        r.value.clients.forEach(e -> 
          Log.info("&lk| |&fr [C] Connection @&fr - @", Strings.conIDToString(e.value), Strings.getIP(e.value))
        );
        Log.info("&lk|&fr");
      });
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

    register("warn-deprecated", "[on|off]", "Warn the client if their CLaJ version is obsolete.", args -> {
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
    
    register("warn-closing", "[on|off]", "Warn all clients when the server is closing.", args -> {
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
        server.rooms.forEach(e -> e.value.message(args[1]));
        Log.info("Message sent to all rooms.");
        return;
      }
      
      ClajRoom room = getRoom(server, args[0]);
      
      if (room == null) Log.err("Room @ not found.", args[0]);
      else {
        room.message(args[1]);
        Log.info("Message sent to room @.", args[0]);
      }
    });
  }
  
  
  private static ClajRoom getRoom(ClajRelay server, String roomId) {
    try { return server.get(bytesToLong(Base64Coder.decode(roomId, Base64Coder.urlsafeMap))); } 
    catch (Exception ignored) { return null; }
  }
  
  /** Copy of {@link com.xpdustry.claj.client.ClajLink#bytesToLong(byte[])} */
  private static long bytesToLong(final byte[] b) {
    if (b.length != Long.BYTES) throw new IndexOutOfBoundsException("must be " + Long.BYTES + " bytes");
    long result = 0;
    for (int i=0; i<Long.BYTES; i++) {
        result <<= 8;
        result |= (b[i] & 0xFF);
    }
    return result;
  }
}
