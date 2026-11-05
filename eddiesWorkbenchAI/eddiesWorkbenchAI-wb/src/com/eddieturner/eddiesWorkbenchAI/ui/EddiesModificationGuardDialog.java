/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BFont;
import javax.baja.gx.BImage;
import javax.baja.gx.BInsets;
import javax.baja.ui.BBorder;
import javax.baja.ui.BButton;
import javax.baja.ui.BDialog;
import javax.baja.ui.BLabel;
import javax.baja.ui.BWidget;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.enums.BHalign;
import javax.baja.ui.enums.BValign;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.ui.pane.BScrollPane;
import javax.baja.ui.text.BTextEditor;

/**
 * Mandatory guardrail confirmation dialog displayed before any Station or Workbench file modification
 * (big or small). Presents an efficient, readable summary of the proposed operations and offers
 * exactly three choices:
 *   1. "Cancel"
 *   2. "Make Duplicate Only"
 *   3. "Proceed"
 */
public final class EddiesModificationGuardDialog extends BDialog
{
  public static final int CHOICE_CANCEL = 0;
  public static final int CHOICE_MAKE_DUPLICATE_ONLY = 1;
  public static final int CHOICE_PROCEED = 2;

  private int selectedChoice = CHOICE_CANCEL;

  private EddiesModificationGuardDialog(
    BWidget owner,
    String targetSummaryLabel,
    String operationHeadline,
    String efficientActionDescription)
  {
    super(owner, "Confirm Station / File Modification", true);
    buildUi(targetSummaryLabel, operationHeadline, efficientActionDescription);
  }

  /**
   * Opens the modal guardrail dialog and returns one of:
   * {@link #CHOICE_CANCEL}, {@link #CHOICE_MAKE_DUPLICATE_ONLY}, or {@link #CHOICE_PROCEED}.
   */
  public static int showGuardrail(
    BWidget owner,
    String targetSummaryLabel,
    String operationHeadline,
    String efficientActionDescription)
  {
    EddiesModificationGuardDialog dlg = new EddiesModificationGuardDialog(
      owner,
      targetSummaryLabel,
      operationHeadline,
      efficientActionDescription);
    dlg.setBoundsCenteredOnOwner();
    dlg.open();
    return dlg.selectedChoice;
  }

  private void buildUi(
    String targetSummaryLabel,
    String operationHeadline,
    String efficientActionDescription)
  {
    BFont titleFont = BFont.make("Tahoma", 12.0, BFont.BOLD);
    BFont subFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont hintFont = BFont.make("Tahoma", 10.0);

    // 1. Top Header Banner
    BImage shieldIcon = makeIcon("module://icons/x16/lightBulb.png");
    BLabel headlineLabel = new BLabel(
      shieldIcon,
      (operationHeadline != null && !operationHeadline.isEmpty())
        ? operationHeadline
        : "Review Proposed AI Modifications",
      titleFont);
    headlineLabel.setTextIconGap(6.0);
    headlineLabel.setForeground(BBrush.makeSolid(BColor.make(20, 60, 115)));

    String tgtText = "Active Target: " + ((targetSummaryLabel != null && !targetSummaryLabel.isEmpty())
      ? targetSummaryLabel
      : "Current Workbench View");
    BLabel targetLabel = new BLabel(tgtText, subFont);
    targetLabel.setForeground(BBrush.makeSolid(BColor.make(50, 90, 145)));

    BGridPane bannerStack = new BGridPane(1);
    bannerStack.setRowGap(4.0);
    bannerStack.setColumnAlign(BHalign.left);
    bannerStack.add("headline", headlineLabel);
    bannerStack.add("target", targetLabel);

    BBorderPane topBanner = new BBorderPane(bannerStack, BInsets.make(8.0, 12.0, 8.0, 12.0));
    topBanner.setFill(BBrush.makeSolid(BColor.make(232, 240, 252)));
    topBanner.setBorder(BBorder.make(
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL,
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(190, 205, 228)),
      0.0, BBorder.NONE, BBrush.NULL
    ));

    // 2. Center Scrollable Description
    BLabel descHeader = new BLabel("Summary of What Will Be Done:", subFont);
    BTextEditor summaryEditor = new BTextEditor(
      efficientActionDescription != null ? efficientActionDescription : "",
      false);
    summaryEditor.setAllowCopying(true);

    BScrollPane scrollPane = new BScrollPane(summaryEditor);
    scrollPane.setPreferredSize(540.0, 230.0);
    BBorderPane scrollBorder = new BBorderPane(scrollPane, BInsets.make(2.0));
    scrollBorder.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(180, 190, 205))));

    BLabel modeExplanation = new BLabel(
      "• Proceed: Applies changes to the live target (with full Snapshot Undo available).\n"
      + "• Make Duplicate Only: Leaves the original untouched and saves the modified copy to Station Files or This Machine.",
      hintFont);
    modeExplanation.setForeground(BBrush.makeSolid(BColor.make(75, 85, 100)));

    BEdgePane centerPane = new BEdgePane();
    centerPane.setTop(new BBorderPane(descHeader, BInsets.make(0.0, 0.0, 4.0, 0.0)));
    centerPane.setCenter(scrollBorder);
    centerPane.setBottom(new BBorderPane(modeExplanation, BInsets.make(6.0, 2.0, 2.0, 2.0)));

    // 3. Bottom Button Bar: "Cancel" | "Make Duplicate Only" | "Proceed"
    Command cancelCmd = new Command(this, "Cancel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        selectedChoice = CHOICE_CANCEL;
        close();
        return null;
      }
    };
    BButton cancelBtn = new BButton(cancelCmd, true, false);

    Command duplicateCmd = new Command(this, "Make Duplicate Only")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        selectedChoice = CHOICE_MAKE_DUPLICATE_ONLY;
        close();
        return null;
      }
    };
    BButton duplicateBtn = new BButton(duplicateCmd, true, false);

    Command proceedCmd = new Command(this, "Proceed")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        selectedChoice = CHOICE_PROCEED;
        close();
        return null;
      }
    };
    BButton proceedBtn = new BButton(proceedCmd, true, true);

    BGridPane buttonBar = new BGridPane(3);
    buttonBar.setColumnGap(10.0);
    buttonBar.setValign(BValign.center);
    buttonBar.add("cancel", cancelBtn);
    buttonBar.add("duplicate", duplicateBtn);
    buttonBar.add("proceed", proceedBtn);

    BEdgePane footer = new BEdgePane();
    footer.setRight(buttonBar);

    BBorderPane footerBorder = new BBorderPane(footer, BInsets.make(8.0, 12.0, 10.0, 12.0));
    footerBorder.setFill(BBrush.makeSolid(BColor.make(245, 247, 251)));
    footerBorder.setBorder(BBorder.make(
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(210, 218, 230)),
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL
    ));

    BEdgePane root = new BEdgePane();
    root.setTop(topBanner);
    root.setCenter(new BBorderPane(centerPane, BInsets.make(10.0, 12.0, 8.0, 12.0)));
    root.setBottom(footerBorder);
    root.setPreferredSize(580.0, 380.0);

    setContent(root);
  }

  private static BImage makeIcon(String ord)
  {
    try
    {
      return BImage.make(ord);
    }
    catch (Throwable t)
    {
      return BImage.NULL;
    }
  }
}
