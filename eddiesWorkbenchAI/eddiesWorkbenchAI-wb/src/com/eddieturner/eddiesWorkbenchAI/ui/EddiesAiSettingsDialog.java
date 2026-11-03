/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import javax.swing.SwingUtilities;
import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BFont;
import javax.baja.gx.BInsets;
import javax.baja.ui.BBorder;
import javax.baja.ui.BButton;
import javax.baja.ui.BCheckBox;
import javax.baja.ui.BDialog;
import javax.baja.ui.BLabel;
import javax.baja.ui.BTextField;
import javax.baja.ui.BWidget;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.enums.BHalign;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.workbench.BWbShell;

/**
 * Modal Settings & Login dialog for Eddie's Company Copilot / AI Platform subscription.
 * Supports:
 *  - Company AI Gateway (https://aoai-farm.bosch-temp.com) Subscription Key & Subscription ID
 *  - 1-click import from an active StationBuilder Program Object on the WireSheet
 *  - Microsoft 365 Work Account Browser SSO (login.microsoftonline.com + MFA)
 *  - Local corporate proxy (127.0.0.1:3128 Px) toggle
 */
public final class EddiesAiSettingsDialog
{
  private EddiesAiSettingsDialog()
  {
  }

  public static void open(
    final BWidget owner,
    final BWbShell shell,
    final Runnable onSettingsSaved)
  {
    final EddiesAiConfig cfg = EddiesAiConfig.get();

    BFont headerFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont labelFont = BFont.make("Tahoma", 11.0);
    BFont smallFont = BFont.make("Tahoma", 10.0);

    // ---- Section 1: Company AI Platform / Model Farm Subscription ----
    BLabel sec1Title = new BLabel("1. Company AI Platform Subscription (Model Farm Gateway)", headerFont);
    sec1Title.setForeground(BBrush.makeSolid(BColor.make(25, 65, 130)));

    final BTextField domainField = new BTextField(cfg.getModelFarmDomain(), 38, true);
    final BTextField apiKeyField = new BTextField(cfg.getModelFarmApiKey(), 38, true);
    final BTextField subIdField = new BTextField(cfg.getModelFarmSubscriptionId(), 38, true);
    final BCheckBox useProxyCheck = new BCheckBox(
      "Use Corporate Laptop Local Proxy (Px 127.0.0.1:3128)",
      cfg.isModelFarmUseProxy());
    final BTextField proxyHostField = new BTextField(cfg.getModelFarmProxyHost(), 16, true);
    final BTextField proxyPortField = new BTextField(Integer.toString(cfg.getModelFarmProxyPort()), 8, true);

    final BLabel statusBanner = new BLabel("Ready.", smallFont);
    statusBanner.setForeground(BBrush.makeSolid(BColor.make(45, 95, 155)));

    Command importFromStationCmd = new Command(owner, "Import Key from Active Station Program")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (StationContextExtractor.tryImportCredentialsFromShell(shell))
        {
          domainField.setText(cfg.getModelFarmDomain());
          apiKeyField.setText(cfg.getModelFarmApiKey());
          subIdField.setText(cfg.getModelFarmSubscriptionId());
          statusBanner.setText("Imported ApiKey / Domain / SubscriptionId from active station Program!");
        }
        else
        {
          statusBanner.setText("No Program component with an 'ApiKey' slot found in the active view.");
        }
        return null;
      }
    };
    BButton importStationBtn = new BButton(importFromStationCmd, true, false);

    BGridPane formGrid = new BGridPane(2);
    formGrid.setRowGap(6.0);
    formGrid.setColumnGap(8.0);
    formGrid.setStretchColumn(1);
    formGrid.setColumnAlign(BHalign.left);

    formGrid.add("l1", new BLabel("Gateway Domain:", labelFont));
    formGrid.add("f1", domainField);
    formGrid.add("l2", new BLabel("Subscription API Key:", labelFont));
    formGrid.add("f2", apiKeyField);
    formGrid.add("l3", new BLabel("Subscription ID:", labelFont));
    formGrid.add("f3", subIdField);

    BGridPane proxyRow = new BGridPane(4);
    proxyRow.setColumnGap(6.0);
    proxyRow.add("phL", new BLabel("Proxy Host:", smallFont));
    proxyRow.add("phF", proxyHostField);
    proxyRow.add("ppL", new BLabel("Port:", smallFont));
    proxyRow.add("ppF", proxyPortField);

    BGridPane sec1Stack = new BGridPane(1);
    sec1Stack.setRowGap(6.0);
    sec1Stack.setStretchColumn(0);
    sec1Stack.setColumnAlign(BHalign.left);
    sec1Stack.add("s1t", sec1Title);
    sec1Stack.add("fg", formGrid);
    sec1Stack.add("pxChk", useProxyCheck);
    sec1Stack.add("pxRow", proxyRow);
    sec1Stack.add("impBtn", importStationBtn);

    BBorderPane sec1Box = new BBorderPane(sec1Stack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    sec1Box.setFill(BBrush.makeSolid(BColor.make(246, 248, 252)));
    sec1Box.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(205, 214, 228))));

    // ---- Section 2: Microsoft 365 Work Account SSO (Company Copilot) ----
    BLabel sec2Title = new BLabel("2. Company Microsoft 365 Work Account (Browser SSO + MFA)", headerFont);
    sec2Title.setForeground(BBrush.makeSolid(BColor.make(25, 65, 130)));

    String initialSsoText = cfg.isCopilotSignedIn()
      ? "Signed in: " + cfg.getCopilotUserDisplay()
      : "Not signed in via Microsoft 365 SSO (using Subscription API Key above)";
    final BLabel ssoStatusLabel = new BLabel(initialSsoText, labelFont);

    final EddiesAiAuthManager.AuthCallback authCb = new EddiesAiAuthManager.AuthCallback()
    {
      @Override
      public void onStatus(final String statusMessage)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            statusBanner.setText(statusMessage);
            statusBanner.relayout();
            statusBanner.repaint();
          }
        });
      }

      @Override
      public void onSuccess(final String summaryMessage)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            ssoStatusLabel.setText(summaryMessage);
            statusBanner.setText(summaryMessage);
            ssoStatusLabel.relayout();
            ssoStatusLabel.repaint();
            statusBanner.relayout();
            statusBanner.repaint();
            if (onSettingsSaved != null) onSettingsSaved.run();
          }
        });
      }

      @Override
      public void onError(final String errorMessage)
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            statusBanner.setText(errorMessage);
            statusBanner.relayout();
            statusBanner.repaint();
          }
        });
      }
    };

    Command m365SsoCmd = new Command(owner, "Sign in with Microsoft 365 (Browser SSO)")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        EddiesAiAuthManager.signInWithMicrosoft365Async(authCb);
        return null;
      }
    };
    BButton m365SsoBtn = new BButton(m365SsoCmd, true, false);

    Command importLocalCopilotCmd = new Command(owner, "Import Local Copilot Login")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        EddiesAiAuthManager.importLocalCopilotToken(authCb);
        return null;
      }
    };
    BButton importLocalBtn = new BButton(importLocalCopilotCmd, true, false);

    Command signOutCmd = new Command(owner, "Sign Out SSO")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        cfg.clearCopilotAuth();
        ssoStatusLabel.setText("Signed out of Microsoft 365 SSO.");
        statusBanner.setText("Cleared Microsoft 365 SSO session.");
        if (onSettingsSaved != null) onSettingsSaved.run();
        return null;
      }
    };
    BButton signOutBtn = new BButton(signOutCmd, true, false);

    BGridPane ssoBtns = new BGridPane(3);
    ssoBtns.setColumnGap(6.0);
    ssoBtns.add("b1", m365SsoBtn);
    ssoBtns.add("b2", importLocalBtn);
    ssoBtns.add("b3", signOutBtn);

    BLabel ssoHint = new BLabel(
      "Opens your default browser (Edge/Chrome) so corporate SSO, Windows login, and MFA authenticate without typing your password here.",
      smallFont);
    ssoHint.setForeground(BBrush.makeSolid(BColor.make(100, 110, 125)));

    BGridPane sec2Stack = new BGridPane(1);
    sec2Stack.setRowGap(6.0);
    sec2Stack.setStretchColumn(0);
    sec2Stack.setColumnAlign(BHalign.left);
    sec2Stack.add("s2t", sec2Title);
    sec2Stack.add("ssoSt", ssoStatusLabel);
    sec2Stack.add("ssoBtns", ssoBtns);
    sec2Stack.add("ssoHint", ssoHint);

    BBorderPane sec2Box = new BBorderPane(sec2Stack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    sec2Box.setFill(BBrush.makeSolid(BColor.make(246, 248, 252)));
    sec2Box.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(205, 214, 228))));

    // ---- Main Dialog Layout ----
    BGridPane mainStack = new BGridPane(1);
    mainStack.setRowGap(10.0);
    mainStack.setStretchColumn(0);
    mainStack.setColumnAlign(BHalign.left);
    mainStack.add("sec1", sec1Box);
    mainStack.add("sec2", sec2Box);
    mainStack.add("status", new BBorderPane(statusBanner, BInsets.make(2.0, 4.0, 2.0, 4.0)));

    final BDialog[] dialogRef = new BDialog[1];

    Command saveCmd = new Command(owner, "Save & Close")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        cfg.setModelFarmDomain(domainField.getText());
        cfg.setModelFarmApiKey(apiKeyField.getText());
        cfg.setModelFarmSubscriptionId(subIdField.getText());
        cfg.setModelFarmUseProxy(useProxyCheck.isSelected());
        cfg.setModelFarmProxyHost(proxyHostField.getText());
        try
        {
          cfg.setModelFarmProxyPort(Integer.parseInt(proxyPortField.getText().trim()));
        }
        catch (NumberFormatException ignore) {}
        cfg.save();

        if (onSettingsSaved != null)
        {
          onSettingsSaved.run();
        }
        if (dialogRef[0] != null)
        {
          dialogRef[0].close();
        }
        return null;
      }
    };
    BButton saveBtn = new BButton(saveCmd, true, false);

    Command cancelCmd = new Command(owner, "Cancel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (dialogRef[0] != null)
        {
          dialogRef[0].close();
        }
        return null;
      }
    };
    BButton cancelBtn = new BButton(cancelCmd, true, false);

    BGridPane footerBtns = new BGridPane(2);
    footerBtns.setColumnGap(8.0);
    footerBtns.add("save", saveBtn);
    footerBtns.add("cancel", cancelBtn);

    BEdgePane footerBar = new BEdgePane();
    footerBar.setRight(footerBtns);

    BEdgePane contentPane = new BEdgePane();
    contentPane.setCenter(mainStack);
    contentPane.setBottom(new BBorderPane(footerBar, BInsets.make(10.0, 0.0, 0.0, 0.0)));

    BBorderPane paddedContent = new BBorderPane(contentPane, BInsets.make(12.0));

    BDialog dlg = new BDialog(
      owner,
      "Eddie's AI Assistant — Company Copilot / Model Farm Login",
      true,
      paddedContent);
    dialogRef[0] = dlg;
    dlg.setResizable(true);
    dlg.computePreferredSize();
    dlg.setBoundsCenteredOnOwner();
    dlg.setVisible(true);
  }
}
