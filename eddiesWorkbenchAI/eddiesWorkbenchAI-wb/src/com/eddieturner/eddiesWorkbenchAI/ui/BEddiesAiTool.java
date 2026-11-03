/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import javax.baja.nre.annotations.NiagaraType;
import javax.baja.sys.BIcon;
import javax.baja.sys.Sys;
import javax.baja.sys.Type;
import javax.baja.ui.CommandArtifact;
import javax.baja.workbench.BWbShell;
import javax.baja.workbench.tool.BWbTool;

/**
 * Workbench tool that automatically installs and toggles Eddie's resizable
 * right-side AI prompt panel in Niagara Workbench.
 */
@NiagaraType
public class BEddiesAiTool extends BWbTool
{
/*+ ------------ BEGIN BAJA AUTO GENERATED CODE ------------ +*/
/*@ $com.eddieturner.eddiesWorkbenchAI.ui.BEddiesAiTool(2979906276)1.0$ @*/
/* Generated Mon Nov 02 17:00:00 PST 2026 by Slot-o-Matic (c) Tridium, Inc. 2012-2026 */

  //region Type

  @Override
  public Type getType() { return TYPE; }
  public static final Type TYPE = Sys.loadType(BEddiesAiTool.class);

  //endregion Type

/*+ ------------ END BAJA AUTO GENERATED CODE -------------- +*/

  public static final BEddiesAiTool DEFAULT = new BEddiesAiTool();

  private static final BIcon ICON = BIcon.make("module://icons/x16/lightBulb.png");

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

  public BEddiesAiTool()
  {
    try
    {
      EddiesAiShellManager.scheduleInstall();
    }
    catch (Throwable ignore)
    {
    }
  }

  @Override
  public BIcon getIcon()
  {
    EddiesAiShellManager.scheduleInstall();
    return ICON;
  }

  @Override
  public CommandArtifact invoke(BWbShell shell) throws Exception
  {
    EddiesAiShellManager.togglePanel(shell);
    return null;
  }
}
