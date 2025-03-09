package com.xpdustry.claj.client;

import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;

import mindustry.Vars;


public class JoinViaClajDialog extends mindustry.ui.dialogs.BaseDialog {
  String lastLink = "CLaJLink#ip:port";
  boolean valid;
  String output;

  public JoinViaClajDialog() {
    super("@claj.join.name");

    cont.table(table -> {
      table.add("@claj.join.link").padRight(5f).left();
      table.field(lastLink, this::setLink).size(550f, 54f).maxTextLength(100).valid(this::setLink);
    }).row();

    cont.label(() -> output).width(550f).left();

    buttons.defaults().size(140f, 60f).pad(4f);
    buttons.button("@cancel", this::hide);
    buttons.button("@ok", this::joinRoom).disabled(button -> !valid || lastLink.isEmpty() || Vars.net.active());
    
    //Adds the 'Join via CLaJ' button
    Table root = (Table)((Stack)Vars.ui.join.getChildren().get(1)).getChildren().get(1);
    root.button("@claj.join.name", mindustry.gen.Icon.play, this::show);
    // poor mobile players =<
    if (!Vars.steam && !Vars.mobile) root.getCells().insert(4, root.getCells().remove(6));
    else root.getCells().insert(3, root.getCells().remove(4));
  }

  public void joinRoom() {
    if (Vars.player.name.trim().isEmpty()) {
      Vars.ui.showInfo("@noname");
      return;
    }
    
    output = validateLink(lastLink);
    valid = output.equals("@claj.join.valid");
    if (!valid) {
      Vars.ui.showErrorMessage(output);
      return;
    }

    CLaJ.joinRoom(CLaJ.Link.fromString(lastLink), () -> {
      Vars.ui.join.hide();
      hide();
    });

    Vars.ui.loadfrag.show("@connecting");
    Vars.ui.loadfrag.setButton(() -> {
      Vars.ui.loadfrag.hide();
      Vars.netClient.disconnectQuietly();
    });
  }
  
  public boolean setLink(String link) {
    if (lastLink.equals(link)) return valid;

    output = validateLink(lastLink);
    valid = output.equals("@claj.join.valid");
    lastLink = link;
    return valid;
  }
  
  String validateLink(String link) {
    link = link.trim();
    if (!link.startsWith(CLaJ.Link.prefix)) return "@claj.join.missing-prefix";

    int key = link.indexOf('#');
    if (key == -1 || key == CLaJ.Link.prefixLength) return "@claj.join.missing-key";
    if (key != CLaJ.Link.keyLength+CLaJ.Link.prefixLength) return "@claj.join.wrong-key-length";

    int semicolon = link.indexOf(':');
    if (semicolon == key+1 || key == link.length()-2) return "@claj.join.missing-host";
    if (semicolon == -1) return "@claj.join.missing-port";

    int port = arc.util.Strings.parseInt(link.substring(semicolon+1));
    if (port < 0 || port > 65535) return "@claj.join.invalid-port";

    return "@claj.join.valid";
  }
}
