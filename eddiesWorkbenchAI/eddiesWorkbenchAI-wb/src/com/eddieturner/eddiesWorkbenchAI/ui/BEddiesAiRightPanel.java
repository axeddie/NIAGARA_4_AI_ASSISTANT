/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BFont;
import javax.baja.gx.BImage;
import javax.baja.gx.BInsets;
import javax.baja.ui.BBorder;
import javax.baja.ui.BButton;
import javax.baja.ui.BCheckBox;
import javax.baja.ui.BDialog;
import javax.baja.ui.BLabel;
import javax.baja.ui.BListDropDown;
import javax.baja.ui.BWidget;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.enums.BHalign;
import javax.baja.ui.enums.BOrientation;
import javax.baja.ui.enums.BValign;
import javax.baja.ui.event.BKeyEvent;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.ui.pane.BScrollPane;
import javax.baja.ui.pane.BSplitPane;
import javax.baja.ui.text.BTextEditor;
import javax.baja.ui.text.Position;
import javax.baja.workbench.BWbShell;

/**
 * Resizable right-side AI prompt panel for Niagara Workbench, purpose-built
 * for Eddie's Company Copilot / AI Platform subscription (https://aoai-farm.bosch-temp.com),
 * active station context extraction, and hybrid station modification execution.
 */
public final class BEddiesAiRightPanel
{
  private static final String[] COMPANY_MODELS = new String[] {
    "gpt-5-nano-2025-08-07 (Company Farm ⭐)",
    "askbosch-prod-farm-openai-gpt-4o-mini-2024-07-18 (Company Farm ⭐)",
    "gemini-2.5-flash-lite (Company Farm ⭐)",
    "gemini-3.5-flash (Company Farm)",
    "gemini-2.5-pro (Company Farm)",
    "gemini-2.5-flash (Company Farm)",
    "gpt-5-2025-08-07 (Company Farm)",
    "gpt-5.6-sol-2026-07-09 (Company Farm)",
    "claude-sonnet-4-5@20250929 (Company Farm)",
    "claude-opus-4-8 (Company Farm)"
  };

  private final BWbShell shell;
  private final Runnable onCloseRequested;
  private final BEdgePane rootPane = new BEdgePane();

  private BListDropDown modelDropDown;
  private BLabel authStatusLabel;
  private BLabel contextValueLabel;
  private BCheckBox includeSelectionCheck;
  private BCheckBox autoApplyCheck;
  private BTextEditor conversationEditor;
  private BScrollPane conversationScrollPane;
  private String rawConversationText = "";
  private int lastWrappedColumns = -1;
  private BTextEditor promptEditor;
  private BLabel statusLabel;

  private BLabel pendingPlanLabel;
  private BButton previewPlanBtn;
  private BButton applyPlanBtn;
  private BButton undoBtn;

  private StationContextExtractor.StationTarget lastSyncedTarget;
  private List<Map<String, String>> pendingPlan = new ArrayList<Map<String, String>>();
  private volatile Thread activeRequestThread;

  public BEddiesAiRightPanel(BWbShell shell, Runnable onCloseRequested)
  {
    this.shell = shell;
    this.onCloseRequested = onCloseRequested;
    buildUi();
    try
    {
      syncContextFromShell();
    }
    catch (Throwable ignore)
    {
    }
    try
    {
      refreshAuthBadge();
    }
    catch (Throwable ignore)
    {
    }
  }

  public BEdgePane getWidget()
  {
    return rootPane;
  }

  private void buildUi()
  {
    // 1. Top section: Title Header + Model/Context Controls + Quick Action Chips
    BEdgePane topContainer = new BEdgePane();
    topContainer.setTop(buildHeaderBar());
    topContainer.setCenter(buildContextAndModelBar());
    topContainer.setBottom(buildQuickActionsBar());

    // 2. Center section: Vertical SplitPane between Conversation History (+ Action Bar) and Prompt Input
    BSplitPane verticalSplit = new BSplitPane(BOrientation.vertical, 68.0);
    verticalSplit.setContinuousLayout(true);
    verticalSplit.setDividerWidth(6.0);
    verticalSplit.setWidget1(buildConversationPane());
    verticalSplit.setWidget2(buildPromptComposerPane());

    rootPane.setTop(topContainer);
    rootPane.setCenter(verticalSplit);
    rootPane.setPreferredSize(390.0, 640.0);
  }

  private BWidget buildHeaderBar()
  {
    BEdgePane header = new BEdgePane();

    BImage aiIcon = makeIcon("module://icons/x16/lightBulb.png");
    BFont titleFont = BFont.make("Tahoma", 12.0, BFont.BOLD);
    BLabel titleLabel = new BLabel(aiIcon, "Eddie's AI Assistant", titleFont);
    titleLabel.setTextIconGap(6.0);

    // Header right buttons: Account/Login, Clear Chat, Hide Panel
    Command accountCmd = new Command(rootPane, "Account", null, null, "Company Copilot / Model Farm Login & Settings")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        EddiesAiSettingsDialog.open(rootPane, shell, new Runnable()
        {
          @Override
          public void run()
          {
            refreshAuthBadge();
          }
        });
        return null;
      }
    };
    BButton accountBtn = new BButton(accountCmd, true, false);

    BImage clearIcon = makeIcon("module://icons/x16/delete.png");
    Command clearCmd = new Command(rootPane, "Clear", clearIcon, null, "Clear conversation history")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        resetConversation();
        return null;
      }
    };
    BButton clearBtn = new BButton(clearCmd, true, false);

    BImage closeIcon = makeIcon("module://icons/x16/close.png");
    Command hideCmd = new Command(rootPane, "Hide", closeIcon, null, "Hide Eddie's AI Panel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (onCloseRequested != null)
        {
          onCloseRequested.run();
        }
        return null;
      }
    };
    BButton hideBtn = new BButton(hideCmd, true, false);

    BGridPane btnRow = new BGridPane(3);
    btnRow.setColumnGap(4.0);
    btnRow.setValign(BValign.center);
    btnRow.add("account", accountBtn);
    btnRow.add("clear", clearBtn);
    btnRow.add("hide", hideBtn);

    header.setLeft(titleLabel);
    header.setRight(btnRow);

    BBorderPane headerBorder = new BBorderPane(header, BInsets.make(6.0, 8.0, 6.0, 8.0));
    headerBorder.setFill(BBrush.makeSolid(BColor.make(232, 238, 247)));
    headerBorder.setBorder(BBorder.make(
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL,
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(190, 200, 214)),
      0.0, BBorder.NONE, BBrush.NULL
    ));
    return headerBorder;
  }

  private BWidget buildContextAndModelBar()
  {
    BFont boldSmall = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont normalSmall = BFont.make("Tahoma", 11.0);
    BFont badgeFont = BFont.make("Tahoma", 10.0);

    // Row 1: Model selector
    BLabel modelLabel = new BLabel("Model:", boldSmall);
    modelDropDown = new BListDropDown()
    {
      @Override
      public void doListActionPerformed()
      {
        super.doListActionPerformed();
        Object sel = getSelectedItem();
        if (sel != null)
        {
          EddiesAiConfig.get().setSelectedModel(sel.toString());
          EddiesAiConfig.get().save();
          refreshAuthBadge();
          if (statusLabel != null)
          {
            statusLabel.setText("Model: " + sel);
          }
        }
      }
    };

    String savedModel = EddiesAiConfig.get().getSelectedModel();
    if (savedModel != null && savedModel.contains("Argon"))
    {
      savedModel = COMPANY_MODELS[0];
      EddiesAiConfig.get().setSelectedModel(savedModel);
      EddiesAiConfig.get().save();
    }
    int selectedIdx = 0;
    for (int i = 0; i < COMPANY_MODELS.length; i++)
    {
      modelDropDown.getList().addItem(COMPANY_MODELS[i]);
      if (COMPANY_MODELS[i].equalsIgnoreCase(savedModel) || COMPANY_MODELS[i].startsWith(savedModel))
      {
        selectedIdx = i;
      }
    }
    modelDropDown.setSelectedIndex(selectedIdx);

    BEdgePane modelRow = new BEdgePane();
    modelRow.setLeft(new BBorderPane(modelLabel, BInsets.make(2.0, 6.0, 2.0, 0.0)));
    modelRow.setCenter(modelDropDown);

    // Row 2: Auth / Gateway Status Row
    BLabel authTitle = new BLabel("Auth:", boldSmall);
    authStatusLabel = new BLabel("Checking...", badgeFont);
    authStatusLabel.setForeground(BBrush.makeSolid(BColor.make(30, 115, 65)));

    BEdgePane authRow = new BEdgePane();
    authRow.setLeft(new BBorderPane(authTitle, BInsets.make(1.0, 6.0, 1.0, 0.0)));
    authRow.setCenter(new BBorderPane(authStatusLabel, BInsets.make(1.0, 2.0, 1.0, 4.0)));

    // Row 3: Active Workbench Context
    BLabel ctxTitle = new BLabel("Target:", boldSmall);
    contextValueLabel = new BLabel("None", normalSmall);
    contextValueLabel.setForeground(BBrush.makeSolid(BColor.make(45, 95, 155)));

    BImage syncIcon = makeIcon("module://icons/x16/refresh.png");
    Command syncCmd = new Command(rootPane, "Sync", syncIcon, null, "Sync with active Workbench view")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        syncContextFromShell();
        refreshAuthBadge();
        return null;
      }
    };
    BButton syncBtn = new BButton(syncCmd, true, false);

    BEdgePane ctxRow = new BEdgePane();
    ctxRow.setLeft(new BBorderPane(ctxTitle, BInsets.make(2.0, 6.0, 2.0, 0.0)));
    ctxRow.setCenter(new BBorderPane(contextValueLabel, BInsets.make(2.0, 2.0, 2.0, 4.0)));
    ctxRow.setRight(syncBtn);

    // Row 4: Context & Auto-Apply Checkboxes
    includeSelectionCheck = new BCheckBox("Include active station view context", true);
    autoApplyCheck = new BCheckBox("Auto-apply station changes", EddiesAiConfig.get().isAutoApplyChanges());

    BGridPane checkRow = new BGridPane(2);
    checkRow.setColumnGap(8.0);
    checkRow.add("inc", includeSelectionCheck);
    checkRow.add("auto", autoApplyCheck);

    BGridPane stack = new BGridPane(1);
    stack.setRowGap(4.0);
    stack.setStretchColumn(0);
    stack.setColumnAlign(BHalign.left);
    stack.add("modelRow", modelRow);
    stack.add("authRow", authRow);
    stack.add("ctxRow", ctxRow);
    stack.add("checkRow", checkRow);

    BBorderPane wrapper = new BBorderPane(stack, BInsets.make(6.0, 8.0, 6.0, 8.0));
    wrapper.setFill(BBrush.makeSolid(BColor.make(246, 248, 251)));
    wrapper.setBorder(BBorder.make(
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL,
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(215, 220, 228)),
      0.0, BBorder.NONE, BBrush.NULL
    ));
    return wrapper;
  }

  private BWidget buildQuickActionsBar()
  {
    BGridPane chips = new BGridPane(4);
    chips.setColumnGap(4.0);
    chips.setUniformColumnWidth(true);

    chips.add("explain", makeQuickPromptButton(
      "Explain",
      "Explain the active station component/wiresheet, its properties, facets, and links."));
    chips.add("buildLogic", makeQuickPromptButton(
      "Build Logic",
      "Create and wire the following logic on the active WireSheet: "));
    chips.add("program", makeQuickPromptButton(
      "Program Code",
      "Write or update the Niagara 4 BProgram Java code on the active target for: "));
    chips.add("diagnose", makeQuickPromptButton(
      "Troubleshoot",
      "Check this station target and its child points/links for configuration or status issues."));

    BBorderPane wrapper = new BBorderPane(chips, BInsets.make(5.0, 8.0, 5.0, 8.0));
    wrapper.setBorder(BBorder.make(
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL,
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(220, 224, 230)),
      0.0, BBorder.NONE, BBrush.NULL
    ));
    return wrapper;
  }

  private BButton makeQuickPromptButton(String label, final String templateText)
  {
    Command cmd = new Command(rootPane, label)
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (promptEditor != null)
        {
          syncContextFromShell();
          promptEditor.setText(templateText);
          promptEditor.requestFocus();
        }
        return null;
      }
    };
    return new BButton(cmd, true, false);
  }

  private BWidget buildConversationPane()
  {
    BFont sectionFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BLabel sectionLabel = new BLabel("Conversation & Station Execution Log", sectionFont);
    BBorderPane header = new BBorderPane(sectionLabel, BInsets.make(4.0, 8.0, 3.0, 8.0));

    conversationEditor = new BTextEditor("", false);
    conversationEditor.setAllowCopying(true);

    conversationScrollPane = new BScrollPane(conversationEditor)
    {
      @Override
      public void doLayout(BWidget[] kids)
      {
        int cols = computeWrapColumns();
        if (cols != lastWrappedColumns && rawConversationText != null && !rawConversationText.isEmpty())
        {
          lastWrappedColumns = cols;
          conversationEditor.setText(wordWrapText(rawConversationText, cols));
        }
        super.doLayout(kids);
      }
    };
    resetConversation();

    BBorderPane scrollWrapper = new BBorderPane(conversationScrollPane, BInsets.make(0.0, 6.0, 4.0, 6.0));

    BEdgePane pane = new BEdgePane();
    pane.setTop(header);
    pane.setCenter(scrollWrapper);
    pane.setBottom(buildStationActionBar());
    return pane;
  }

  private BWidget buildStationActionBar()
  {
    BFont barFont = BFont.make("Tahoma", 10.0, BFont.BOLD);
    pendingPlanLabel = new BLabel("Station Plan: None pending", barFont);
    pendingPlanLabel.setForeground(BBrush.makeSolid(BColor.make(70, 85, 110)));

    Command previewCmd = new Command(rootPane, "Preview")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (pendingPlan == null || pendingPlan.isEmpty())
        {
          BDialog.info(rootPane, "Station Action Plan", "No pending station modifications to preview.");
          return null;
        }
        String summary = StationActionExecutor.summarizePlan(pendingPlan);
        int res = BDialog.confirm(
          rootPane,
          "Preview Station Modifications",
          summary + "\nApply these changes to the active station now?");
        if (res == BDialog.YES || res == BDialog.OK)
        {
          applyPendingPlan();
        }
        return null;
      }
    };
    previewPlanBtn = new BButton(previewCmd, true, false);
    previewPlanBtn.setEnabled(false);

    Command applyCmd = new Command(rootPane, "Apply Changes")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        applyPendingPlan();
        return null;
      }
    };
    applyPlanBtn = new BButton(applyCmd, true, false);
    applyPlanBtn.setEnabled(false);

    Command undoCmd = new Command(rootPane, "Undo")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        String msg = StationActionExecutor.undoLastBatch();
        appendSystemLog(msg);
        updateActionButtonsState();
        syncContextFromShell();
        return null;
      }
    };
    undoBtn = new BButton(undoCmd, true, false);
    undoBtn.setEnabled(false);

    BGridPane btnGroup = new BGridPane(3);
    btnGroup.setColumnGap(4.0);
    btnGroup.add("prev", previewPlanBtn);
    btnGroup.add("app", applyPlanBtn);
    btnGroup.add("undo", undoBtn);

    BEdgePane bar = new BEdgePane();
    bar.setLeft(new BBorderPane(pendingPlanLabel, BInsets.make(3.0, 4.0, 3.0, 4.0)));
    bar.setRight(btnGroup);

    BBorderPane wrapper = new BBorderPane(bar, BInsets.make(4.0, 6.0, 4.0, 6.0));
    wrapper.setFill(BBrush.makeSolid(BColor.make(239, 244, 252)));
    wrapper.setBorder(BBorder.make(
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(205, 215, 230)),
      0.0, BBorder.NONE, BBrush.NULL,
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(205, 215, 230)),
      0.0, BBorder.NONE, BBrush.NULL
    ));
    return wrapper;
  }

  private BWidget buildPromptComposerPane()
  {
    BFont sectionFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BLabel promptHeader = new BLabel("Prompt (Enter to Send, Shift+Enter for New Line)", sectionFont);
    BBorderPane header = new BBorderPane(promptHeader, BInsets.make(4.0, 8.0, 3.0, 8.0));

    promptEditor = new BTextEditor("", true)
    {
      @Override
      public void keyPressed(BKeyEvent event)
      {
        if (event.getKeyCode() == BKeyEvent.VK_ENTER && !event.isShiftDown())
        {
          event.consume();
          sendPrompt();
          return;
        }
        super.keyPressed(event);
      }
    };

    BScrollPane inputScroll = new BScrollPane(promptEditor);
    BBorderPane inputBorder = new BBorderPane(inputScroll, BInsets.make(2.0));
    inputBorder.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(180, 190, 205))));
    BBorderPane inputMargin = new BBorderPane(inputBorder, BInsets.make(0.0, 6.0, 4.0, 6.0));

    // Bottom bar: status label on left, Clear + Send buttons on right
    statusLabel = new BLabel("Ready (Company Copilot / Model Farm)", BFont.make("Tahoma", 10.0));
    statusLabel.setForeground(BBrush.makeSolid(BColor.make(100, 110, 125)));

    Command clearInputCmd = new Command(rootPane, "Clear Input")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        promptEditor.setText("");
        promptEditor.requestFocus();
        return null;
      }
    };
    BButton clearInputBtn = new BButton(clearInputCmd, true, false);

    BImage sendIcon = makeIcon("module://icons/x16/mediaPlay.png");
    Command sendCmd = new Command(rootPane, "Send", sendIcon, null, "Send prompt to Company Copilot / Model Farm")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        sendPrompt();
        return null;
      }
    };
    BButton sendBtn = new BButton(sendCmd, true, true);

    BGridPane actionBtns = new BGridPane(2);
    actionBtns.setColumnGap(5.0);
    actionBtns.add("clearInput", clearInputBtn);
    actionBtns.add("send", sendBtn);

    BEdgePane footer = new BEdgePane();
    footer.setLeft(new BBorderPane(statusLabel, BInsets.make(4.0, 2.0, 4.0, 4.0)));
    footer.setRight(actionBtns);
    BBorderPane footerWrapper = new BBorderPane(footer, BInsets.make(2.0, 6.0, 6.0, 6.0));

    BEdgePane composer = new BEdgePane();
    composer.setTop(header);
    composer.setCenter(inputMargin);
    composer.setBottom(footerWrapper);
    return composer;
  }

  public void resetConversation()
  {
    if (conversationEditor == null) return;
    StringBuilder welcome = new StringBuilder();
    welcome.append("========================================\n");
    welcome.append("  Eddie's Workbench AI (Company Copilot)\n");
    welcome.append("========================================\n\n");
    welcome.append("Connected to your Company AI / Copilot Gateway (https://aoai-farm.bosch-temp.com).\n\n");
    welcome.append("What you can do:\n");
    welcome.append(" 1. Ask questions about the active WireSheet, component properties, links, or PX view.\n");
    welcome.append(" 2. Give instructions to modify the station (e.g., 'Create two NumericWritables Input1 and Input2, add an Add block, and link them to a Result NumericWritable').\n");
    welcome.append(" 3. Click 'Account' in the top-right header to set your Subscription Key, import it from an active StationBuilder Program block, or sign in with Microsoft 365 Work SSO.\n");
    setConversationRawText(welcome.toString(), false);
  }

  public void refreshAuthBadge()
  {
    if (authStatusLabel == null) return;
    try
    {
      EddiesAiConfig cfg = EddiesAiConfig.get();
      cfg.load();
      String domain = cfg.getModelFarmDomain();
      boolean hasKey = !cfg.getModelFarmApiKey().isEmpty();
      boolean hasSso = cfg.isCopilotSignedIn();

      if (hasKey)
      {
        String subId = cfg.getModelFarmSubscriptionId();
        authStatusLabel.setText("Ready (" + domain + (subId.isEmpty() ? ")" : " | SubID set)"));
        authStatusLabel.setForeground(BBrush.makeSolid(BColor.make(25, 120, 60)));
      }
      else if (hasSso)
      {
        authStatusLabel.setText("M365 SSO: " + cfg.getCopilotUserDisplay());
        authStatusLabel.setForeground(BBrush.makeSolid(BColor.make(25, 120, 60)));
      }
      else
      {
        authStatusLabel.setText("Needs Key/Login — Click 'Account' above");
        authStatusLabel.setForeground(BBrush.makeSolid(BColor.make(180, 65, 30)));
      }
      authStatusLabel.relayout();
      authStatusLabel.repaint();
    }
    catch (Throwable ignore)
    {
    }
  }

  public void syncContextFromShell()
  {
    if (shell == null || contextValueLabel == null) return;
    try
    {
      lastSyncedTarget = StationContextExtractor.extract(shell);
      contextValueLabel.setText(lastSyncedTarget.summaryLabel);
      contextValueLabel.relayout();
      contextValueLabel.repaint();
      refreshAuthBadge();
    }
    catch (Throwable ignore)
    {
    }
  }

  public void focusPromptInput()
  {
    syncContextFromShell();
    if (promptEditor != null)
    {
      promptEditor.requestFocus();
    }
  }

  private void sendPrompt()
  {
    if (promptEditor == null) return;
    String rawText = promptEditor.getText();
    if (rawText == null || rawText.trim().isEmpty())
    {
      return;
    }

    final String prompt = rawText.trim();
    promptEditor.setText("");

    if (prompt.regionMatches(true, 0, "/key ", 0, 5))
    {
      String newKey = prompt.substring(5).trim();
      EddiesAiConfig.get().setModelFarmApiKey(newKey);
      EddiesAiConfig.get().save();
      refreshAuthBadge();
      if (statusLabel != null)
      {
        statusLabel.setText("Saved Model Farm Subscription API Key!");
      }
      return;
    }

    if (autoApplyCheck != null)
    {
      EddiesAiConfig.get().setAutoApplyChanges(autoApplyCheck.isSelected());
      EddiesAiConfig.get().save();
    }

    syncContextFromShell();
    final String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
    final String selectedModel = modelDropDown != null && modelDropDown.getSelectedItem() != null
      ? modelDropDown.getSelectedItem().toString()
      : "gpt-5-nano-2025-08-07";

    boolean includeCtx = (includeSelectionCheck == null || includeSelectionCheck.isSelected());
    String ctxSummary = (lastSyncedTarget != null) ? lastSyncedTarget.summaryLabel : "None";
    final String fullContextBlock = (includeCtx && lastSyncedTarget != null)
      ? lastSyncedTarget.fullContextPromptBlock
      : "";

    StringBuilder log = new StringBuilder(rawConversationText);
    if (log.length() > 0 && log.charAt(log.length() - 1) != '\n')
    {
      log.append("\n");
    }
    log.append("\n----------------------------------------\n");
    log.append("[").append(timestamp).append("] You:\n");
    if (includeCtx)
    {
      log.append("  (Station Target: ").append(ctxSummary).append(")\n");
    }
    log.append(prompt).append("\n\n");
    log.append("Working... Waiting for Company AI Platform (").append(selectedModel).append(")...\n");

    setConversationRawText(log.toString(), true);

    if (statusLabel != null)
    {
      statusLabel.setText("Working... Calling Company AI Gateway...");
    }

    activeRequestThread = EddiesAiClient.sendPromptAsync(
      selectedModel,
      fullContextBlock,
      prompt,
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
              handleAiResponse(responseText, usedModel);
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
              handleAiError(errorMessage);
            }
          });
        }
      });

    promptEditor.requestFocus();
  }

  private void handleAiResponse(String responseText, String usedModel)
  {
    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
    List<Map<String, String>> extractedPlan = StationActionExecutor.extractActionPlan(responseText);
    String cleanDisplay = StationActionExecutor.stripActionTagsForDisplay(responseText);

    String currentText = removeWorkingPlaceholder(rawConversationText);
    StringBuilder log = new StringBuilder(currentText);
    log.append("[").append(timestamp).append("] AI (").append(usedModel).append("):\n");
    log.append(cleanDisplay).append("\n");

    if (!extractedPlan.isEmpty())
    {
      pendingPlan = extractedPlan;
      if (autoApplyCheck != null && autoApplyCheck.isSelected())
      {
        String execLog = StationActionExecutor.executePlan(shell, pendingPlan);
        pendingPlan = new ArrayList<Map<String, String>>();
        log.append("\n=== Auto-Applied Station Modifications ===\n").append(execLog);
        syncContextFromShell();
      }
      else
      {
        log.append("\n>>> ").append(pendingPlan.size())
          .append(" station modification action(s) ready! Click 'Preview' or 'Apply Changes' below.\n");
      }
    }

    setConversationRawText(log.toString(), true);
    updateActionButtonsState();

    if (statusLabel != null)
    {
      statusLabel.setText("Response received at " + timestamp);
    }
  }

  private void handleAiError(String errorMessage)
  {
    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
    String currentText = removeWorkingPlaceholder(rawConversationText);
    StringBuilder log = new StringBuilder(currentText);
    log.append("[").append(timestamp).append("] Error:\n");
    log.append(errorMessage).append("\n");

    setConversationRawText(log.toString(), true);

    if (statusLabel != null)
    {
      statusLabel.setText("Error at " + timestamp);
    }
  }

  private void applyPendingPlan()
  {
    if (pendingPlan == null || pendingPlan.isEmpty()) return;
    String execLog = StationActionExecutor.executePlan(shell, pendingPlan);
    pendingPlan = new ArrayList<Map<String, String>>();
    appendSystemLog("=== Applied Station Modifications ===\n" + execLog);
    updateActionButtonsState();
    syncContextFromShell();
  }

  private void updateActionButtonsState()
  {
    boolean hasPending = (pendingPlan != null && !pendingPlan.isEmpty());
    if (pendingPlanLabel != null)
    {
      if (hasPending)
      {
        pendingPlanLabel.setText("Pending Plan: " + pendingPlan.size() + " action(s) ready");
        pendingPlanLabel.setForeground(BBrush.makeSolid(BColor.make(20, 95, 170)));
      }
      else
      {
        pendingPlanLabel.setText("Station Plan: None pending");
        pendingPlanLabel.setForeground(BBrush.makeSolid(BColor.make(70, 85, 110)));
      }
      pendingPlanLabel.relayout();
      pendingPlanLabel.repaint();
    }
    if (previewPlanBtn != null) previewPlanBtn.setEnabled(hasPending);
    if (applyPlanBtn != null) applyPlanBtn.setEnabled(hasPending);
    if (undoBtn != null) undoBtn.setEnabled(StationActionExecutor.hasUndo());
  }

  private void appendSystemLog(String text)
  {
    if (conversationEditor == null) return;
    StringBuilder sb = new StringBuilder(rawConversationText);
    if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n')
    {
      sb.append("\n");
    }
    sb.append("\n").append(text).append("\n");
    setConversationRawText(sb.toString(), true);
  }

  private void setConversationRawText(String rawText, boolean scrollBottom)
  {
    this.rawConversationText = (rawText != null) ? rawText : "";
    if (conversationEditor == null) return;
    int cols = computeWrapColumns();
    this.lastWrappedColumns = cols;
    conversationEditor.setText(wordWrapText(this.rawConversationText, cols));
    if (scrollBottom)
    {
      scrollToBottom(conversationEditor);
    }
  }

  private int computeWrapColumns()
  {
    double availWidth = 0.0;
    if (conversationScrollPane != null && conversationScrollPane.getWidth() > 80.0)
    {
      availWidth = conversationScrollPane.getWidth();
    }
    else if (rootPane.getWidth() > 80.0)
    {
      availWidth = rootPane.getWidth() - 16.0;
    }
    else
    {
      availWidth = 370.0;
    }

    double charWidth = 7.2;
    try
    {
      if (conversationEditor != null && conversationEditor.getRenderer() != null)
      {
        double cw = conversationEditor.getRenderer().getColumnWidth(null, 0);
        if (cw > 2.0)
        {
          charWidth = cw;
        }
      }
    }
    catch (Throwable ignore)
    {
    }

    // Reserve ~28px for vertical scrollbar and internal margins
    int cols = (int) ((availWidth - 28.0) / charWidth);
    return Math.max(28, cols);
  }

  private static String wordWrapText(String text, int maxCols)
  {
    if (text == null || text.isEmpty() || maxCols <= 10) return text != null ? text : "";
    StringBuilder out = new StringBuilder(text.length() + 128);
    String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++)
    {
      String line = lines[i];
      if (line.endsWith("\r"))
      {
        line = line.substring(0, line.length() - 1);
      }

      if (line.length() <= maxCols)
      {
        out.append(line);
      }
      else if (isDividerLine(line))
      {
        out.append(line.substring(0, maxCols));
      }
      else
      {
        wrapSingleParagraph(line, maxCols, out);
      }

      if (i < lines.length - 1)
      {
        out.append('\n');
      }
    }
    return out.toString();
  }

  private static boolean isDividerLine(String s)
  {
    if (s.length() < 8) return false;
    char first = s.charAt(0);
    if (first != '=' && first != '-' && first != '*') return false;
    for (int i = 1; i < s.length(); i++)
    {
      if (s.charAt(i) != first) return false;
    }
    return true;
  }

  private static void wrapSingleParagraph(String line, int maxCols, StringBuilder out)
  {
    // Preserve leading indentation (and list prefix spacing) up to 6 spaces on wrapped lines
    int indentLen = 0;
    while (indentLen < line.length() && indentLen < 6 && line.charAt(indentLen) == ' ')
    {
      indentLen++;
    }
    String contIndent = "";
    if (line.startsWith(" 1. ") || line.startsWith(" 2. ") || line.startsWith(" 3. ")
        || line.startsWith(" - ") || line.startsWith(" * "))
    {
      contIndent = "    ";
    }
    else if (indentLen > 0)
    {
      StringBuilder ind = new StringBuilder(indentLen);
      for (int k = 0; k < indentLen; k++) ind.append(' ');
      contIndent = ind.toString();
    }

    String remaining = line;
    boolean firstLine = true;
    while (!remaining.isEmpty())
    {
      String prefix = firstLine ? "" : contIndent;
      int limit = Math.max(12, maxCols - prefix.length());
      if (remaining.length() <= limit)
      {
        out.append(prefix).append(remaining);
        break;
      }

      int breakIdx = remaining.lastIndexOf(' ', limit);
      if (breakIdx <= (firstLine ? indentLen : 0))
      {
        // Hard-break a single long token (like a long ORD or URL)
        out.append(prefix).append(remaining.substring(0, limit)).append('\n');
        remaining = remaining.substring(limit);
      }
      else
      {
        out.append(prefix).append(remaining.substring(0, breakIdx)).append('\n');
        remaining = remaining.substring(breakIdx + 1);
      }
      firstLine = false;
    }
  }

  private static String removeWorkingPlaceholder(String text)
  {
    if (text == null) return "";
    int idx = text.lastIndexOf("Working... Waiting for Company AI Platform");
    if (idx >= 0)
    {
      return text.substring(0, idx);
    }
    return text;
  }

  private static void scrollToBottom(BTextEditor editor)
  {
    try
    {
      int lineCount = editor.getModel().getLineCount();
      if (lineCount > 0)
      {
        int lastLine = Math.max(0, lineCount - 1);
        editor.scrollToVisible(new Position(lastLine, 0));
      }
    }
    catch (Exception ignore)
    {
    }
  }

  private static BImage makeIcon(String ord)
  {
    try
    {
      return BImage.make(ord);
    }
    catch (Exception e)
    {
      return BImage.NULL;
    }
  }
}
