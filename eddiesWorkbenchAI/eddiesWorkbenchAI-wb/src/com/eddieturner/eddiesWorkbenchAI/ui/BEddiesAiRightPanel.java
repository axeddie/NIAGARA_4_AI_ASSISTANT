/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javax.swing.SwingUtilities;
import javax.baja.ui.BDialog;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.transfer.Clipboard;
import javax.baja.ui.transfer.TransferEnvelope;
import javax.baja.workbench.BWbShell;
import com.tridium.ui.fx.BFxWidget;

/**
 * Resizable right-side AI prompt panel for Niagara Workbench, rendered with a modern
 * Dark-Theme HTML5/CSS3/JavaScript interface hosted inside a JavaFX WebView (BFxWidget).
 * Supports Personal Google Account (Direct Gemini API) and Company Copilot / AI Platform
 * (https://aoai-farm.bosch-temp.com), active station context extraction, and guarded
 * station/file modification execution with multi-level Snapshot Undo.
 */
public final class BEddiesAiRightPanel
{
  private static final String IPC_PREFIX = "EDDIES_IPC:";

  private static final String[] COMPANY_FARM_MODELS = new String[] {
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

  private static final String[] GOOGLE_PERSONAL_MODELS = new String[] {
    "Gemini 4 Argon (Medium) (Google Ultra ⭐)",
    "Gemini 4 Argon (High) (Google Ultra)",
    "gemini-2.5-pro (Google Personal ⭐)",
    "gemini-2.5-flash (Google Personal ⭐)",
    "gemini-2.5-flash-lite (Google Personal)",
    "gemini-2.0-flash (Google Personal)"
  };

  private static String[] getAvailableModels(EddiesAiConfig cfg)
  {
    if (cfg != null && cfg.isGooglePersonalReady())
    {
      String[] combined = new String[COMPANY_FARM_MODELS.length + GOOGLE_PERSONAL_MODELS.length];
      System.arraycopy(COMPANY_FARM_MODELS, 0, combined, 0, COMPANY_FARM_MODELS.length);
      System.arraycopy(GOOGLE_PERSONAL_MODELS, 0, combined, COMPANY_FARM_MODELS.length, GOOGLE_PERSONAL_MODELS.length);
      return combined;
    }
    return COMPANY_FARM_MODELS;
  }

  private final BWbShell shell;
  private final Runnable onCloseRequested;
  private final BEdgePane rootPane = new BEdgePane();
  private EddiesAiFxWebWidget fxWebWidget;

  private final List<String> pendingJsQueue = new ArrayList<String>();
  private volatile boolean webPageLoaded = false;

  private boolean includeContextSelected = true;
  private boolean autoApplySelected = true;
  private String currentSelectedModel = EddiesAiConfig.DEFAULT_MODEL;

  private StationContextExtractor.StationTarget lastSyncedTarget;
  private long lastActivityCheckMs = 0L;
  private String lastObservedOrdStr = "";
  private Object lastObservedViewRef = null;
  private String lastContextSummary = "None";
  private List<Map<String, String>> pendingPlan = new ArrayList<Map<String, String>>();
  private volatile Thread activeRequestThread;

  public BEddiesAiRightPanel(BWbShell shell, Runnable onCloseRequested)
  {
    this.shell = shell;
    this.onCloseRequested = onCloseRequested;
    try
    {
      EddiesAiConfig cfg = EddiesAiConfig.get();
      cfg.load();
      if (cfg.getSelectedModel() != null && !cfg.getSelectedModel().isEmpty())
      {
        this.currentSelectedModel = cfg.getSelectedModel();
      }
      this.autoApplySelected = cfg.isAutoApplyChanges();
    }
    catch (Throwable ignore)
    {
    }

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

  public void setPanelVisible(boolean visible)
  {
    rootPane.setVisible(visible);
    if (fxWebWidget != null)
    {
      fxWebWidget.setVisible(visible);
    }
  }

  private void buildUi()
  {
    fxWebWidget = new EddiesAiFxWebWidget();
    rootPane.setCenter(fxWebWidget);
    rootPane.setPreferredSize(390.0, 640.0);
  }

  public void resetConversation()
  {
    runJs("showWelcomeCard();");
    setStatusText("Conversation cleared.");
  }

  public void refreshAuthBadge()
  {
    try
    {
      EddiesAiConfig cfg = EddiesAiConfig.get();
      cfg.load();
      String savedModel = cfg.getSelectedModel();
      if (savedModel != null && !savedModel.isEmpty())
      {
        this.currentSelectedModel = savedModel;
      }
      String[] availableModels = getAvailableModels(cfg);
      StringBuilder jsModels = new StringBuilder("[");
      for (int i = 0; i < availableModels.length; i++)
      {
        if (i > 0) jsModels.append(", ");
        jsModels.append(jsStr(availableModels[i]));
      }
      jsModels.append("]");

      String badge = cfg.getAuthSummaryBadge();
      boolean needsKey = badge != null && badge.startsWith("Needs ");
      runJs("setModelList(" + jsModels.toString() + ", " + jsStr(currentSelectedModel) + ");"
        + "updateAuthBadge("
        + jsStr(badge) + ", "
        + (needsKey ? "true" : "false") + ", "
        + jsStr(currentSelectedModel) + ");");
    }
    catch (Throwable ignore)
    {
    }
  }

  /**
   * Lightweight, throttled handler for BWbShell.ActivityListener.
   */
  public void onShellActivity()
  {
    if (shell == null) return;
    long now = System.currentTimeMillis();
    if (now - lastActivityCheckMs < 400L)
    {
      return;
    }
    lastActivityCheckMs = now;
    try
    {
      Object curOrd = shell.getActiveOrd();
      Object curView = shell.getActiveView();
      String ordStr = (curOrd != null) ? curOrd.toString() : "";
      if (ordStr.equals(lastObservedOrdStr) && curView == lastObservedViewRef)
      {
        return;
      }
      lastObservedOrdStr = ordStr;
      lastObservedViewRef = curView;

      String newSummary = StationContextExtractor.extractLightweightSummary(shell);
      if (newSummary != null && !newSummary.equals(lastContextSummary))
      {
        lastContextSummary = newSummary;
        runJs("updateTargetBadge(" + jsStr(newSummary) + ");");
      }
    }
    catch (Throwable ignore)
    {
    }
  }

  public void syncContextFromShell()
  {
    if (shell == null) return;
    try
    {
      lastSyncedTarget = StationContextExtractor.extract(shell);
      lastObservedOrdStr = (lastSyncedTarget.activeOrd != null) ? lastSyncedTarget.activeOrd.toString() : "";
      lastObservedViewRef = lastSyncedTarget.activeView;
      lastContextSummary = (lastSyncedTarget.summaryLabel != null) ? lastSyncedTarget.summaryLabel : "None";
      runJs("updateTargetBadge(" + jsStr(lastContextSummary) + ");");
      refreshAuthBadge();
    }
    catch (Throwable ignore)
    {
    }
  }

  public void focusPromptInput()
  {
    syncContextFromShell();
    runJs("focusPromptBox();");
  }

  private void handleIpcFromWeb(String rawAlert)
  {
    if (rawAlert == null || !rawAlert.startsWith(IPC_PREFIX))
    {
      return;
    }
    String rest = rawAlert.substring(IPC_PREFIX.length());
    int colonIdx = rest.indexOf(':');
    final String cmd = (colonIdx >= 0) ? rest.substring(0, colonIdx) : rest;
    final String payload = (colonIdx >= 0) ? rest.substring(colonIdx + 1) : "";

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        dispatchIpcOnUiThread(cmd, payload);
      }
    });
  }

  private void dispatchIpcOnUiThread(String cmd, String payload)
  {
    try
    {
      if ("account".equals(cmd))
      {
        EddiesAiSettingsDialog.open(rootPane, shell, new Runnable()
        {
          @Override
          public void run()
          {
            refreshAuthBadge();
          }
        });
      }
      else if ("clearChat".equals(cmd))
      {
        resetConversation();
      }
      else if ("hidePanel".equals(cmd))
      {
        if (onCloseRequested != null)
        {
          onCloseRequested.run();
        }
      }
      else if ("syncContext".equals(cmd))
      {
        syncContextFromShell();
        refreshAuthBadge();
        setStatusText("Synced with active Workbench view.");
      }
      else if ("selectModel".equals(cmd))
      {
        if (payload != null && !payload.isEmpty())
        {
          currentSelectedModel = payload;
          EddiesAiConfig.get().setSelectedModel(payload);
          EddiesAiConfig.get().save();
          refreshAuthBadge();
          setStatusText("Model: " + payload);
        }
      }
      else if ("optionsChanged".equals(cmd))
      {
        if (payload != null)
        {
          String[] parts = payload.split(",");
          if (parts.length >= 2)
          {
            includeContextSelected = "1".equals(parts[0]);
            autoApplySelected = "1".equals(parts[1]);
            EddiesAiConfig.get().setAutoApplyChanges(autoApplySelected);
            EddiesAiConfig.get().save();
          }
        }
      }
      else if ("arrangeWs".equals(cmd))
      {
        arrangeActiveWiresheetNow();
      }
      else if ("reviewApplyPlan".equals(cmd))
      {
        if (pendingPlan == null || pendingPlan.isEmpty())
        {
          BDialog.info(rootPane, "Station / File Action Plan", "No pending modifications to review.");
          return;
        }
        openGuardrailAndExecute(pendingPlan);
      }
      else if ("undoLast".equals(cmd))
      {
        executeUndoFlow();
      }
      else if ("copyClipboard".equals(cmd))
      {
        copyTextToClipboard(payload);
        setStatusText("Copied code block to clipboard.");
      }
      else if ("sendPrompt".equals(cmd))
      {
        // Format: inc|auto|model|promptText
        int p1 = payload.indexOf('|');
        int p2 = (p1 >= 0) ? payload.indexOf('|', p1 + 1) : -1;
        int p3 = (p2 >= 0) ? payload.indexOf('|', p2 + 1) : -1;
        if (p1 > 0 && p2 > p1 && p3 > p2)
        {
          includeContextSelected = "1".equals(payload.substring(0, p1));
          autoApplySelected = "1".equals(payload.substring(p1 + 1, p2));
          String model = payload.substring(p2 + 1, p3);
          String promptText = payload.substring(p3 + 1);
          if (model != null && !model.isEmpty())
          {
            currentSelectedModel = model;
          }
          sendPrompt(promptText);
        }
      }
    }
    catch (Throwable ignore)
    {
    }
  }

  private void copyTextToClipboard(final String text)
  {
    if (text == null) return;
    try
    {
      Clipboard.getDefault().setContents(TransferEnvelope.make(text));
    }
    catch (Throwable ignore)
    {
    }
    try
    {
      AccessController.doPrivileged(new PrivilegedAction<Void>()
      {
        @Override
        public Void run()
        {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
          return null;
        }
      });
    }
    catch (Throwable ignore)
    {
    }
  }

  private void executeUndoFlow()
  {
    if (!StationActionExecutor.hasUndo())
    {
      BDialog.info(rootPane, "Undo", "Nothing to undo.");
      return;
    }
    String latestSummary = StationActionExecutor.getLatestUndoSummary();
    int confirm = BDialog.confirm(
      rootPane,
      "Confirm Undo",
      "Revert the most recent modification?\n\n" + latestSummary);
    if (confirm != BDialog.YES && confirm != BDialog.OK)
    {
      return;
    }
    String msg = StationActionExecutor.undoLastBatch();
    WiresheetLayoutEngine.refreshActiveWiresheetView(shell);
    appendSystemMessage("Undo Executed", msg, false);
    updateActionButtonsState();
    syncContextFromShell();
    setStatusText(msg);
  }

  private void sendPrompt(String rawText)
  {
    if (rawText == null || rawText.trim().isEmpty())
    {
      return;
    }
    final String prompt = rawText.trim();

    if (prompt.regionMatches(true, 0, "/gkey ", 0, 6))
    {
      String newKey = prompt.substring(6).trim();
      EddiesAiConfig.get().setGeminiApiKey(newKey);
      if (EddiesAiConfig.get().isGooglePersonalReady())
      {
        EddiesAiConfig.get().setSelectedModel(EddiesAiConfig.DEFAULT_GOOGLE_MODEL);
      }
      EddiesAiConfig.get().save();
      refreshAuthBadge();
      appendSystemMessage("Credentials Saved", "Saved & AES-GCM-256 encrypted Personal Google Gemini API Key.", false);
      setStatusText("Saved & AES-GCM encrypted Personal Google Gemini API Key!");
      return;
    }

    if (prompt.regionMatches(true, 0, "/key ", 0, 5))
    {
      String newKey = prompt.substring(5).trim();
      EddiesAiConfig.get().setModelFarmApiKey(newKey);
      EddiesAiConfig.get().save();
      refreshAuthBadge();
      appendSystemMessage("Credentials Saved", "Saved & AES-GCM-256 encrypted Company Model Farm Subscription Key.", false);
      setStatusText("Saved & AES-GCM encrypted Model Farm Subscription API Key!");
      return;
    }

    EddiesAiConfig.get().setAutoApplyChanges(autoApplySelected);
    EddiesAiConfig.get().save();

    syncContextFromShell();
    final String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
    final String selectedModel = (currentSelectedModel != null && !currentSelectedModel.isEmpty())
      ? currentSelectedModel
      : EddiesAiConfig.DEFAULT_MODEL;

    String ctxSummary = (includeContextSelected && lastSyncedTarget != null) ? lastSyncedTarget.summaryLabel : "";
    final String fullContextBlock = (includeContextSelected && lastSyncedTarget != null)
      ? lastSyncedTarget.fullContextPromptBlock
      : "";

    runJs("appendUserMessage("
      + jsStr(timestamp) + ", "
      + jsStr(ctxSummary) + ", "
      + jsStr(prompt) + ");");

    setWorkingState(true, "Working... Waiting for AI response (" + selectedModel + ")...");

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
              handleAiResponse(responseText, usedModel, selectedModel, fullContextBlock, prompt, 0);
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
  }

  private void handleAiResponse(
    String responseText,
    String usedModel,
    final String selectedUiModel,
    final String baseContextBlock,
    final String originalUserPrompt,
    final int inspectRound)
  {
    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());

    // 1. Check if the AI requested read-only station/file inspection (<station-inspect>) before proposing actions
    List<Map<String, String>> inspectReqs = StationInspector.extractInspectRequests(responseText);
    List<Map<String, String>> extractedPlan = StationActionExecutor.extractActionPlan(responseText);

    if (!inspectReqs.isEmpty() && extractedPlan.isEmpty() && inspectRound < 3)
    {
      String cleanNote = StationActionExecutor.stripActionTagsForDisplay(responseText);
      if (cleanNote != null && !cleanNote.trim().isEmpty())
      {
        runJs("appendAiMessage("
          + jsStr(timestamp) + ", "
          + jsStr(usedModel) + ", "
          + jsStr(cleanNote.trim()) + ", 0);");
      }

      setWorkingState(true, "Working... Executing " + inspectReqs.size() + " station/file inspection query(s)...");

      final String inspectionOutput = StationInspector.executeInspections(shell, inspectReqs);
      final String augmentedContext = (baseContextBlock != null ? baseContextBlock : "")
        + "\n\n" + inspectionOutput;
      final String followUpPrompt = originalUserPrompt
        + "\n\n[System Note: The requested <station-inspect> results are now provided above. "
        + "Please provide your final answer or <station-actions> modification plan.]";

      activeRequestThread = EddiesAiClient.sendPromptAsync(
        selectedUiModel,
        augmentedContext,
        followUpPrompt,
        new EddiesAiClient.AiResponseCallback()
        {
          @Override
          public void onSuccess(final String nextResponse, final String nextUsedModel)
          {
            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                handleAiResponse(
                  nextResponse,
                  nextUsedModel,
                  selectedUiModel,
                  augmentedContext,
                  originalUserPrompt,
                  inspectRound + 1);
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
      return;
    }

    setWorkingState(false, "");

    String cleanDisplay = StationActionExecutor.stripActionTagsForDisplay(responseText);
    if (!extractedPlan.isEmpty())
    {
      pendingPlan = extractedPlan;
    }

    runJs("appendAiMessage("
      + jsStr(timestamp) + ", "
      + jsStr(usedModel) + ", "
      + jsStr(cleanDisplay) + ", "
      + extractedPlan.size() + ");");

    updateActionButtonsState();
    setStatusText("Response received at " + timestamp);

    if (!extractedPlan.isEmpty() && autoApplySelected)
    {
      openGuardrailAndExecute(pendingPlan);
    }
  }

  private void handleAiError(String errorMessage)
  {
    String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
    setWorkingState(false, "");
    appendSystemMessage("Error (" + timestamp + ")", errorMessage, true);
    setStatusText("Error at " + timestamp);
  }

  private void arrangeActiveWiresheetNow()
  {
    syncContextFromShell();
    List<Map<String, String>> arrangePlan = new ArrayList<Map<String, String>>();
    Map<String, String> arrangeAction = new HashMap<String, String>();
    arrangeAction.put("action", "arrangeWiresheet");
    arrangeAction.put("parent", "activeTarget");
    arrangePlan.add(arrangeAction);
    openGuardrailAndExecute(arrangePlan);
  }

  /**
   * Opens the mandatory 3-button Guardrail Dialog ("Cancel" | "Make Duplicate Only" | "Proceed")
   * for any modification operation (big or small) and executes the user's chosen branch.
   */
  private void openGuardrailAndExecute(List<Map<String, String>> planToReview)
  {
    if (planToReview == null || planToReview.isEmpty()) return;

    syncContextFromShell();
    String targetLabel = (lastSyncedTarget != null) ? lastSyncedTarget.summaryLabel : "Active Workbench View";
    String headline = StationActionExecutor.buildPlanHeadline(planToReview);
    String efficientSummary = StationActionExecutor.summarizePlan(planToReview);

    int choice = EddiesModificationGuardDialog.showGuardrail(
      rootPane,
      targetLabel,
      headline,
      efficientSummary);

    if (choice == EddiesModificationGuardDialog.CHOICE_CANCEL)
    {
      appendSystemMessage(
        "Modification Cancelled",
        "Plan retained in 'Review & Apply' if you wish to run it later.",
        false);
      setStatusText("Modification cancelled (nothing changed).");
      updateActionButtonsState();
      return;
    }

    if (choice == EddiesModificationGuardDialog.CHOICE_MAKE_DUPLICATE_ONLY)
    {
      String dupLog = StationActionExecutor.executePlanAsDuplicate(shell, rootPane, planToReview);
      appendSystemMessage("Make Duplicate Only", dupLog, false);
      setStatusText("Duplicate operation finished.");
      updateActionButtonsState();
      syncContextFromShell();
      return;
    }

    if (choice == EddiesModificationGuardDialog.CHOICE_PROCEED)
    {
      String execLog = StationActionExecutor.executePlan(shell, planToReview);
      if (planToReview == pendingPlan)
      {
        pendingPlan = new ArrayList<Map<String, String>>();
      }
      appendSystemMessage("Applied Modifications (Undo Snapshot Saved)", execLog, false);
      updateActionButtonsState();
      syncContextFromShell();
      setStatusText("Applied changes (Undo available).");
    }
  }

  private void updateActionButtonsState()
  {
    int pendingCount = (pendingPlan != null) ? pendingPlan.size() : 0;
    boolean hasUndo = StationActionExecutor.hasUndo();
    runJs("updateActionButtons(" + pendingCount + ", " + (hasUndo ? "true" : "false") + ");");
  }

  private void appendSystemMessage(String title, String details, boolean isError)
  {
    runJs("appendSystemMessage("
      + jsStr(title) + ", "
      + jsStr(details) + ", "
      + (isError ? "true" : "false") + ");");
  }

  private void setWorkingState(boolean isWorking, String message)
  {
    runJs("setWorkingState(" + (isWorking ? "true" : "false") + ", " + jsStr(message) + ");");
  }

  private void setStatusText(String text)
  {
    runJs("setStatusText(" + jsStr(text) + ");");
  }

  private void runJs(final String script)
  {
    if (script == null || script.isEmpty()) return;
    synchronized (pendingJsQueue)
    {
      if (!webPageLoaded || fxWebWidget == null || fxWebWidget.webEngine == null)
      {
        pendingJsQueue.add(script);
        return;
      }
    }
    BFxWidget.postfx(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          if (fxWebWidget != null && fxWebWidget.webEngine != null)
          {
            fxWebWidget.webEngine.executeScript(script);
          }
        }
        catch (Throwable ignore)
        {
        }
      }
    });
  }

  private static String jsStr(String s)
  {
    if (s == null) return "''";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    sb.append('\'');
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      switch (c)
      {
        case '\\':
          sb.append("\\\\");
          break;
        case '\'':
          sb.append("\\'");
          break;
        case '\"':
          sb.append("\\\"");
          break;
        case '\r':
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '<':
          sb.append("\\x3C");
          break;
        case '>':
          sb.append("\\x3E");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    sb.append('\'');
    return sb.toString();
  }

  /**
   * Inner BFxWidget hosting the JavaFX WebView for Eddie's AI Assistant.
   */
  private final class EddiesAiFxWebWidget extends BFxWidget
  {
    volatile WebView webView;
    volatile WebEngine webEngine;

    @Override
    public Node createNode()
    {
      return AccessController.doPrivileged(new PrivilegedAction<Node>()
      {
        @Override
        public Node run()
        {
          StackPane pane = new StackPane();
          pane.setStyle("-fx-background-color: #071122;");
          webView = new WebView();
          webView.setContextMenuEnabled(false);
          webEngine = webView.getEngine();
          try
          {
            webEngine.getHistory().setMaxSize(0);
          }
          catch (Throwable ignore)
          {
          }
          pane.getChildren().add(webView);
          return pane;
        }
      });
    }

    @Override
    protected void fxInitialized()
    {
      AccessController.doPrivileged(new PrivilegedAction<Void>()
      {
        @Override
        public Void run()
        {
          if (webEngine == null) return null;

          webEngine.setOnAlert(new EventHandler<WebEvent<String>>()
          {
            @Override
            public void handle(WebEvent<String> event)
            {
              if (event != null)
              {
                handleIpcFromWeb(event.getData());
              }
            }
          });

          webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>()
          {
            @Override
            public void changed(ObservableValue<? extends Worker.State> obs, Worker.State oldState, Worker.State newState)
            {
              if (newState == Worker.State.SUCCEEDED)
              {
                List<String> toRun;
                synchronized (pendingJsQueue)
                {
                  webPageLoaded = true;
                  toRun = new ArrayList<String>(pendingJsQueue);
                  pendingJsQueue.clear();
                }
                for (int i = 0; i < toRun.size(); i++)
                {
                  try
                  {
                    webEngine.executeScript(toRun.get(i));
                  }
                  catch (Throwable ignore)
                  {
                  }
                }
                SwingUtilities.invokeLater(new Runnable()
                {
                  @Override
                  public void run()
                  {
                    syncContextFromShell();
                    refreshAuthBadge();
                    updateActionButtonsState();
                  }
                });
              }
            }
          });

          String html = EddiesAiWebUiTemplate.buildHtml(getAvailableModels(EddiesAiConfig.get()), currentSelectedModel);
          webEngine.loadContent(html);
          return null;
        }
      });
    }
  }
}
