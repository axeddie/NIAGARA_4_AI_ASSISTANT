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
import javax.baja.ui.text.PasswordRenderer;
import javax.baja.ui.text.TextRenderer;
import javax.baja.workbench.BWbShell;

/**
 * Modal Settings & Account dialog for Eddie's Workbench AI Assistant.
 * Supports:
 *  1. Personal Google Account (Direct Gemini API Key masked & AES-GCM-256 encrypted on disk,
 *     GEMINI_API_KEY environment variable detection, and Google Browser OAuth 2.0 / CLI import)
 *  2. Company AI Gateway (https://aoai-farm.bosch-temp.com) Subscription Key, Subscription ID,
 *     1-click import from an active StationBuilder Program Object, and Microsoft 365 Work SSO.
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
    cfg.load();

    BFont headerFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont labelFont = BFont.make("Tahoma", 11.0);
    BFont smallFont = BFont.make("Tahoma", 10.0);

    final BLabel statusBanner = new BLabel("Ready. All saved API keys are encrypted on disk with AES-GCM-256.", smallFont);
    statusBanner.setForeground(BBrush.makeSolid(BColor.make(30, 95, 65)));

    // ============================================================================
    // Section 2: Personal Google Account (Direct Gemini API + Secure Storage)
    // ============================================================================
    BLabel secGoogleTitle = new BLabel("2. Personal Google Account (Optional — Unlocks Google Ultra / Personal Models)", headerFont);
    secGoogleTitle.setForeground(BBrush.makeSolid(BColor.make(20, 95, 55)));

    BLabel googleSecurityNote = new BLabel(
      "Note: Google blocks username/password login in desktop apps. Use a personal Google AI Studio API Key (masked below & encrypted on disk with AES-GCM-256) or Browser OAuth.",
      smallFont);
    googleSecurityNote.setForeground(BBrush.makeSolid(BColor.make(85, 95, 110)));

    final BTextField geminiKeyField = new BTextField(cfg.getStoredGeminiApiKeyOnly(), 38, true);
    applySecretMask(geminiKeyField, true);

    String initialGoogleState;
    if (!cfg.getStoredGeminiApiKeyOnly().isEmpty())
    {
      initialGoogleState = "Encrypted Key Stored: " + EddiesAiConfig.maskSecretPreview(cfg.getStoredGeminiApiKeyOnly());
    }
    else if (cfg.isGeminiApiKeyFromEnv())
    {
      initialGoogleState = "Active from Windows Env Var (GEMINI_API_KEY / GOOGLE_API_KEY): "
        + EddiesAiConfig.maskSecretPreview(cfg.getGeminiApiKey());
    }
    else if (cfg.isGoogleSignedIn())
    {
      initialGoogleState = "Signed in via Google OAuth: " + cfg.getGoogleUserEmail();
    }
    else
    {
      initialGoogleState = "Not configured — Only Company Farm models are shown until a Google Key (AIza...) or OAuth is added.";
    }
    final BLabel googleStatusLabel = new BLabel(initialGoogleState, smallFont);
    googleStatusLabel.setForeground(BBrush.makeSolid(BColor.make(25, 80, 140)));

    BGridPane googleGrid = new BGridPane(2);
    googleGrid.setRowGap(6.0);
    googleGrid.setColumnGap(8.0);
    googleGrid.setStretchColumn(1);
    googleGrid.setColumnAlign(BHalign.left);
    googleGrid.add("gl1", new BLabel("Gemini API Key (Masked):", labelFont));
    googleGrid.add("gf1", geminiKeyField);
    googleGrid.add("gl2", new BLabel("Current Google Status:", smallFont));
    googleGrid.add("gf2", googleStatusLabel);

    Command openAiStudioCmd = new Command(owner, "Get Key (Google AI Studio)")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        try
        {
          EddiesAiAuthManager.openBrowserUrl("https://aistudio.google.com/app/apikey");
          statusBanner.setText("Opened https://aistudio.google.com/app/apikey in your browser.");
        }
        catch (Exception e)
        {
          statusBanner.setText("Open https://aistudio.google.com/app/apikey in your browser.");
        }
        statusBanner.relayout();
        statusBanner.repaint();
        return null;
      }
    };
    BButton openAiStudioBtn = new BButton(openAiStudioCmd, true, false);

    Command testGoogleCmd = new Command(owner, "Test Google Key")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        statusBanner.setText("Testing Personal Google Gemini API connection...");
        statusBanner.relayout();
        statusBanner.repaint();
        EddiesAiClient.testPersonalGoogleConnectionAsync(
          geminiKeyField.getText(),
          new EddiesAiClient.AiResponseCallback()
          {
            @Override
            public void onSuccess(final String responseText, final String usedModel)
            {
              SwingUtilities.invokeLater(new Runnable()
              {
                @Override
                public void run()
                {
                  String preview = responseText != null ? responseText.replace('\n', ' ').trim() : "OK";
                  if (preview.length() > 95) preview = preview.substring(0, 95) + "...";
                  statusBanner.setText("Verified [" + usedModel + "]: " + preview);
                  statusBanner.relayout();
                  statusBanner.repaint();
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
                  statusBanner.setText("Google Test Failed: " + errorMessage);
                  statusBanner.relayout();
                  statusBanner.repaint();
                }
              });
            }
          });
        return null;
      }
    };
    BButton testGoogleBtn = new BButton(testGoogleCmd, true, false);

    final EddiesAiAuthManager.AuthCallback googleOAuthCb = new EddiesAiAuthManager.AuthCallback()
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
            googleStatusLabel.setText(summaryMessage);
            statusBanner.setText(summaryMessage);
            googleStatusLabel.relayout();
            googleStatusLabel.repaint();
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

    Command googleOAuthCmd = new Command(owner, "Browser OAuth / Import CLI")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        EddiesAiAuthManager.signInWithGoogleAsync(googleOAuthCb);
        return null;
      }
    };
    BButton googleOAuthBtn = new BButton(googleOAuthCmd, true, false);

    Command clearGoogleCmd = new Command(owner, "Clear Google Key")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        geminiKeyField.setText("");
        cfg.setGeminiApiKey("");
        cfg.clearGoogleAuth();
        if (!cfg.isGooglePersonalReady())
        {
          cfg.setSelectedModel(EddiesAiConfig.DEFAULT_COMPANY_MODEL);
          cfg.save();
        }
        googleStatusLabel.setText(cfg.isGeminiApiKeyFromEnv()
          ? "Using GEMINI_API_KEY from Windows Environment Variable."
          : "Cleared stored Google API Key & OAuth session (Company Farm models active).");
        statusBanner.setText("Cleared stored Personal Google credentials from disk.");
        googleStatusLabel.relayout();
        googleStatusLabel.repaint();
        statusBanner.relayout();
        statusBanner.repaint();
        if (onSettingsSaved != null) onSettingsSaved.run();
        return null;
      }
    };
    BButton clearGoogleBtn = new BButton(clearGoogleCmd, true, false);

    BGridPane googleBtns = new BGridPane(4);
    googleBtns.setColumnGap(6.0);
    googleBtns.add("gb1", openAiStudioBtn);
    googleBtns.add("gb2", testGoogleBtn);
    googleBtns.add("gb3", googleOAuthBtn);
    googleBtns.add("gb4", clearGoogleBtn);

    BGridPane secGoogleStack = new BGridPane(1);
    secGoogleStack.setRowGap(6.0);
    secGoogleStack.setStretchColumn(0);
    secGoogleStack.setColumnAlign(BHalign.left);
    secGoogleStack.add("gt", secGoogleTitle);
    secGoogleStack.add("gn", googleSecurityNote);
    secGoogleStack.add("gg", googleGrid);
    secGoogleStack.add("gb", googleBtns);

    BBorderPane secGoogleBox = new BBorderPane(secGoogleStack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    secGoogleBox.setFill(BBrush.makeSolid(BColor.make(243, 250, 246)));
    secGoogleBox.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(180, 215, 195))));

    // ============================================================================
    // Section 1: Company AI Platform / Model Farm Subscription & M365 SSO
    // ============================================================================
    BLabel sec1Title = new BLabel("1. Company AI Platform Subscription (Model Farm Gateway & M365 SSO — Default)", headerFont);
    sec1Title.setForeground(BBrush.makeSolid(BColor.make(25, 65, 130)));

    final BTextField domainField = new BTextField(cfg.getModelFarmDomain(), 38, true);
    final BTextField apiKeyField = new BTextField(cfg.getStoredModelFarmApiKeyOnly(), 38, true);
    applySecretMask(apiKeyField, true);
    final BTextField subIdField = new BTextField(cfg.getModelFarmSubscriptionId(), 38, true);
    final BCheckBox useProxyCheck = new BCheckBox(
      "Use Corporate Laptop Local Proxy (Px 127.0.0.1:3128)",
      cfg.isModelFarmUseProxy());
    final BTextField proxyHostField = new BTextField(cfg.getModelFarmProxyHost(), 16, true);
    final BTextField proxyPortField = new BTextField(Integer.toString(cfg.getModelFarmProxyPort()), 8, true);

    Command importFromStationCmd = new Command(owner, "Import Key from Active Station Program")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (StationContextExtractor.tryImportCredentialsFromShell(shell))
        {
          domainField.setText(cfg.getModelFarmDomain());
          apiKeyField.setText(cfg.getStoredModelFarmApiKeyOnly());
          subIdField.setText(cfg.getModelFarmSubscriptionId());
          statusBanner.setText("Imported & encrypted ApiKey / Domain / SubscriptionId from active station Program!");
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
    formGrid.add("l2", new BLabel("Subscription Key (Masked):", labelFont));
    formGrid.add("f2", apiKeyField);
    formGrid.add("l3", new BLabel("Subscription ID:", labelFont));
    formGrid.add("f3", subIdField);

    BGridPane proxyRow = new BGridPane(4);
    proxyRow.setColumnGap(6.0);
    proxyRow.add("phL", new BLabel("Proxy Host:", smallFont));
    proxyRow.add("phF", proxyHostField);
    proxyRow.add("ppL", new BLabel("Port:", smallFont));
    proxyRow.add("ppF", proxyPortField);

    String initialSsoText = cfg.isCopilotSignedIn()
      ? "M365 SSO Signed in: " + cfg.getCopilotUserDisplay()
      : "M365 Work SSO: Not signed in";
    final BLabel ssoStatusLabel = new BLabel(initialSsoText, smallFont);

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

    Command signOutCmd = new Command(owner, "Sign Out M365 SSO")
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

    BGridPane companyBtns = new BGridPane(3);
    companyBtns.setColumnGap(6.0);
    companyBtns.add("cb1", importStationBtn);
    companyBtns.add("cb2", m365SsoBtn);
    companyBtns.add("cb3", signOutBtn);

    BGridPane sec1Stack = new BGridPane(1);
    sec1Stack.setRowGap(6.0);
    sec1Stack.setStretchColumn(0);
    sec1Stack.setColumnAlign(BHalign.left);
    sec1Stack.add("s1t", sec1Title);
    sec1Stack.add("fg", formGrid);
    sec1Stack.add("pxChk", useProxyCheck);
    sec1Stack.add("pxRow", proxyRow);
    sec1Stack.add("ssoSt", ssoStatusLabel);
    sec1Stack.add("cBtns", companyBtns);

    BBorderPane sec1Box = new BBorderPane(sec1Stack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    sec1Box.setFill(BBrush.makeSolid(BColor.make(246, 248, 252)));
    sec1Box.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(205, 214, 228))));

    // ---- Main Dialog Layout ----
    BGridPane mainStack = new BGridPane(1);
    mainStack.setRowGap(10.0);
    mainStack.setStretchColumn(0);
    mainStack.setColumnAlign(BHalign.left);
    mainStack.add("sec1", sec1Box);
    mainStack.add("secGoogle", secGoogleBox);
    mainStack.add("status", new BBorderPane(statusBanner, BInsets.make(2.0, 4.0, 2.0, 4.0)));

    final BDialog[] dialogRef = new BDialog[1];
    final boolean[] keysMasked = new boolean[] { true };

    Command toggleMaskCmd = new Command(owner, "Show / Unmask Keys")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        keysMasked[0] = !keysMasked[0];
        applySecretMask(geminiKeyField, keysMasked[0]);
        applySecretMask(apiKeyField, keysMasked[0]);
        statusBanner.setText(keysMasked[0]
          ? "API keys masked (hidden & copy disabled)."
          : "API keys visible (will be encrypted with AES-GCM-256 on Save).");
        statusBanner.relayout();
        statusBanner.repaint();
        return null;
      }
    };
    BButton toggleMaskBtn = new BButton(toggleMaskCmd, true, false);

    Command saveCmd = new Command(owner, "Save & Encrypt")
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

        String enteredGeminiKey = geminiKeyField.getText();
        cfg.setGeminiApiKey(enteredGeminiKey);

        // Put Company AI Subscription first (Section 1) and Personal Google Account second (Section 2)
        // If user entered a valid personal Google key, unlock Google models; if cleared, revert to Company default.
        if (cfg.isGooglePersonalReady() && cfg.getSelectedModel().contains("Company Farm"))
        {
          cfg.setSelectedModel(EddiesAiConfig.DEFAULT_GOOGLE_MODEL);
        }
        else if (!cfg.isGooglePersonalReady() && EddiesAiConfig.isPersonalGoogleModelName(cfg.getSelectedModel()))
        {
          cfg.setSelectedModel(EddiesAiConfig.DEFAULT_COMPANY_MODEL);
        }

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
    footerBar.setLeft(toggleMaskBtn);
    footerBar.setRight(footerBtns);

    BEdgePane contentPane = new BEdgePane();
    contentPane.setCenter(mainStack);
    contentPane.setBottom(new BBorderPane(footerBar, BInsets.make(10.0, 0.0, 0.0, 0.0)));

    BBorderPane paddedContent = new BBorderPane(contentPane, BInsets.make(12.0));

    BDialog dlg = new BDialog(
      owner,
      "Eddie's AI Assistant — Personal Google Account & AI Settings",
      true,
      paddedContent);
    dialogRef[0] = dlg;
    dlg.setResizable(true);
    dlg.computePreferredSize();
    dlg.setBoundsCenteredOnOwner();
    dlg.open();
  }

  private static void applySecretMask(BTextField field, boolean masked)
  {
    if (field == null) return;
    try
    {
      if (masked)
      {
        field.setRenderer(new PasswordRenderer());
        field.setAllowCopying(false);
      }
      else
      {
        field.setRenderer(new TextRenderer());
        field.setAllowCopying(true);
      }
      field.relayout();
      field.repaint();
    }
    catch (Throwable ignore)
    {
    }
  }
}
