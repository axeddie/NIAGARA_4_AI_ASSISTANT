/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BFont;
import javax.baja.gx.BInsets;
import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.BIcon;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.ui.BButton;
import javax.baja.ui.BLabel;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.enums.BHalign;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.workbench.BWbShell;
import javax.baja.workbench.sidebar.BWbSideBar;

/**
 * Workbench SideBar entry (Window -> Side Bars -> Eddie's AI Assistant)
 * that ensures Eddie's resizable right-side AI panel is docked in the shell
 * and provides quick controls to show/toggle the right-side panel or open login settings.
 */
@NiagaraType
public class BEddiesAiSideBar extends BWbSideBar
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.eddieturner.eddiesWorkbenchAI.ui.BEddiesAiSideBar(2979906276)1.0$ @*/
/* Generated Tue Nov 03 07:00:00 PST 2026 by Slot-o-Matic (c) Tridium, Inc. 2012-2026 */

  //region Type

  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BEddiesAiSideBar.class);

  //endregion Type

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  public static final BEddiesAiSideBar DEFAULT = new BEddiesAiSideBar();

  private static final BIcon ICON = BIcon.make("module://icons/x16/lightBulb.png");

  private BLabel statusLabel;

  static
  {
    try
    {
      EddiesAiShellManager.scheduleInstall();
    }
    catch (Throwable ignore)
    {
    }
  }

  public BEddiesAiSideBar()
  {
    try
    {
      initUi();
      EddiesAiShellManager.scheduleInstall();
    }
    catch (Throwable ignore)
    {
    }
  }

  private void initUi()
  {
    BFont boldFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont smallFont = BFont.make("Tahoma", 10.0);

    BLabel titleLabel = new BLabel("Eddie's AI Assistant (Right Panel)", boldFont);
    statusLabel = new BLabel("Right-side AI panel docked.", smallFont);
    statusLabel.setForeground(BBrush.makeSolid(BColor.make(45, 95, 155)));

    Command showRightCmd = new Command(this, "Show Right-Side AI Panel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        BWbShell shell = getWbShell();
        if (shell != null)
        {
          EddiesAiShellManager.setPanelVisible(shell, true);
        }
        else
        {
          EddiesAiShellManager.installOnAllShells();
        }
        return null;
      }
    };
    BButton showRightBtn = new BButton(showRightCmd, true, false);

    Command toggleRightCmd = new Command(this, "Toggle Right-Side AI Panel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        BWbShell shell = getWbShell();
        if (shell != null)
        {
          EddiesAiShellManager.togglePanel(shell);
        }
        return null;
      }
    };
    BButton toggleRightBtn = new BButton(toggleRightCmd, true, false);

    Command accountCmd = new Command(this, "Account / Model Farm Login")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        BWbShell shell = getWbShell();
        EddiesAiSettingsDialog.open(BEddiesAiSideBar.this, shell, null);
        return null;
      }
    };
    BButton accountBtn = new BButton(accountCmd, true, false);

    BGridPane grid = new BGridPane(1);
    grid.setRowGap(6.0);
    grid.setStretchColumn(0);
    grid.setColumnAlign(BHalign.left);
    grid.add("title", titleLabel);
    grid.add("status", statusLabel);
    grid.add("showBtn", showRightBtn);
    grid.add("toggleBtn", toggleRightBtn);
    grid.add("accountBtn", accountBtn);

    BEdgePane root = new BEdgePane();
    root.setTop(new BBorderPane(grid, BInsets.make(8.0)));
    setContent(root);
  }

  @Override
  public BIcon getIcon()
  {
    EddiesAiShellManager.scheduleInstall();
    return ICON;
  }

  @Override
  public void activeViewChanged()
  {
    super.activeViewChanged();
    try
    {
      EddiesAiShellManager.installOnAllShells();
      BWbShell shell = getWbShell();
      if (shell != null && statusLabel != null)
      {
        EddiesAiShellManager.setPanelVisible(shell, true);
        statusLabel.setText("Active ORD: " + (shell.getActiveOrd() != null ? shell.getActiveOrd().toString() : "None"));
      }
    }
    catch (Throwable ignore)
    {
    }
  }
}
