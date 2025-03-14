package com.xpdustry.claj.client.dialogs;

import com.xpdustry.claj.client.Claj;
import com.xpdustry.claj.client.ClajLink;

import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;

import mindustry.Vars;


public class JoinViaClajDialog extends mindustry.ui.dialogs.BaseDialog {
  String lastLink = "claj://";
  boolean valid;
  String output;

  public JoinViaClajDialog() {
    super("@claj.join.name");

    cont.labelWrap("@claj.join.note").padBottom(10f).left().row();
    cont.table(table -> {
      table.add("@claj.join.link").padRight(5f).left();
      table.field(lastLink, this::setLink).size(550f, 54f).maxTextLength(100).valid(this::setLink).row();
      table.add();
      table.label(() -> output).width(550f).left().row();
    }).row();

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
    
    ClajLink link;
    try { link = ClajLink.fromString(lastLink); } 
    catch (Exception e) {
      valid = false;
      Vars.ui.showErrorMessage(arc.Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage());
      return;
    }

    Vars.ui.loadfrag.show("@connecting");
    Vars.ui.loadfrag.setButton(() -> {
      Vars.ui.loadfrag.hide();
      Vars.netClient.disconnectQuietly();
    });
    
    arc.util.Time.runTask(2f, () -> 
      Claj.joinRoom(link, () -> {
        Vars.ui.join.hide();
        hide();
      })
    );
  }
  
  public boolean setLink(String link) {
    if (lastLink.equals(link)) return valid;

    lastLink = link;
    try { 
      ClajLink.fromString(lastLink); 
      output = "@claj.join.valid";
      return valid = true;
      
    } catch (Exception e) {
      output = arc.Core.bundle.get("claj.join.invalid") + ' ' + e.getLocalizedMessage();
      return valid = false;
    }
  }
}
