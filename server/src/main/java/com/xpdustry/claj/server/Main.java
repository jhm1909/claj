package com.xpdustry.claj.server;

import arc.net.ArcNet;
import arc.net.Connection;
import arc.util.Log;
import arc.util.Strings;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Main {
  public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
  public static final DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  public static final String logFormat = "&lk&fb[@]&fr @ @&fr";

  public static void main(String[] args) {
    // Ignore connection closed errors
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
        
        Distributor distributor = new Distributor();
        new Control(distributor);
        distributor.init(Integer.parseInt(args[0]));
        Log.info("Server loaded. Type @ for help.", "'help'");
        distributor.run();
        
    } catch (Throwable error) {
        Log.err("Could not to load redirect system", error);
    }
  }

  public static String getIP(Connection connection) {
      return connection.getRemoteAddressTCP().getAddress().getHostAddress();
  }
}
