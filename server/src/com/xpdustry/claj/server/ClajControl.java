package com.xpdustry.claj.server;

import arc.util.Log;

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
        Log.info("| &b&lb " + c.text + (c.paramText.isEmpty() ? "" : " &lc&fi") + c.paramText + 
                 "&fr - &lw" + c.description));
    });
    
    register("exit", "Stop the server.", args -> {
      Log.info("Shutting down CLaJ server.");
      server.stop();
    });

    register("rooms", "Displays created rooms.", args -> {
      Log.info("Rooms:");
      server.rooms.forEach(r -> {
          Log.info("| Room @:", r.value.idToString());
          Log.info("| | [H] Connection @&fr - @", Strings.conIDToString(r.value.host), Strings.getIP(r.value.host));
          r.value.clients.forEach(e -> 
            Log.info("| | [C] Connection @&fr - @", Strings.conIDToString(e.value), Strings.getIP(e.value))
          );
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
        Log.info("Blacklist:");
        ClajConfig.blacklist.each(ip -> Log.info("| IP: @", ip));

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

    register("warn-deprecated", "Warn the client if their CLaJ version is obsolete.", args -> {
      ClajConfig.warnDeprecated = !ClajConfig.warnDeprecated;
      Log.info("Warn message when a client using an obsolete CLaJ version @.", 
               ClajConfig.warnDeprecated ? "enabled" : "disabled");
    });
    
    register("warn-closing", "Warn all clients when the server is closing.", args -> {
      ClajConfig.warnClosing = !ClajConfig.warnClosing;
      Log.info("Warn message when closing the server @.", ClajConfig.warnClosing ? "enabled" : "disabled");
    });
    
  }
}
