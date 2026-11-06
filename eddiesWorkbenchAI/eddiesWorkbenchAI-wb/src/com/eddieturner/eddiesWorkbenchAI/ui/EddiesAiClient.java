/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Asynchronous AI client for Eddie's Workbench AI Assistant.
 * Supports:
 *  1. Personal Google Account — Direct Google Gemini API (https://generativelanguage.googleapis.com)
 *     using an AES-GCM encrypted Gemini API Key (x-goog-api-key header, never in URL) or Google OAuth 2.0 Bearer token.
 *  2. Company Copilot / Model Farm Gateway (https://aoai-farm.bosch-temp.com) & Microsoft 365 Work SSO.
 */
public final class EddiesAiClient
{
  public interface AiResponseCallback
  {
    void onSuccess(String responseText, String usedModel);
    void onError(String errorMessage);
  }

  private static final int CONNECT_TIMEOUT_MS = 15000;
  private static final int READ_TIMEOUT_MS = 60000;

  private static final String SYSTEM_INSTRUCTIONS =
    "You are Eddie's Niagara 4 Workbench AI Assistant — an expert Tridium Niagara N4 Developer & HVAC Commissioning Engineer.\n"
    + "You are operating inside Niagara Workbench connected to the active station and file system shown in the context block.\n\n"
    + "1. WHEN THE USER ASKS A QUESTION OR REQUESTS EXPLANATION / BQL / TROUBLESHOOTING:\n"
    + "- Provide a clear, concise, technical response referencing the exact components, properties, facets, links, and files in the active view context.\n\n"
    + "2. AGENTIC INSPECTION (<station-inspect>[ ... ]</station-inspect>):\n"
    + "- If you need to inspect another station component/folder by ORD, run a BQL query across the station, list a directory, read lines of a file, or search the user's AI Brain & Local Niagara Reference Folders before making changes, output a <station-inspect>[ ... ]</station-inspect> block:\n"
    + "  * {\"inspect\": \"inspectOrd\", \"ord\": \"station:|slot:/Drivers/BcpBacnetNetwork\"}\n"
    + "  * {\"inspect\": \"runBql\", \"query\": \"station:|slot:/|bql:select name, out, slotPath from control:ControlPoint\"}\n"
    + "  * {\"inspect\": \"readFile\", \"file\": \"station:|file:^px/Main.px\", \"startLine\": 1, \"endLine\": 300}\n"
    + "  * {\"inspect\": \"listFiles\", \"dir\": \"station:|file:^\"}\n"
    + "  * {\"inspect\": \"searchKnowledge\", \"query\": \"ScrollPane\"}\n\n"
    + "3. WHEN THE USER ASKS TO CREATE, MODIFY, SET, LINK, RENAME, DELETE, OR EDIT ANYTHING IN THE STATION OR WORKBENCH FILES:\n"
    + "- Briefly explain what you are doing, and output a <station-actions>...</station-actions> block containing a valid JSON array of action objects.\n"
    + "- Note: Every modification plan automatically opens Eddie's Guardrail Confirmation Dialog ('Cancel' | 'Make Duplicate Only' | 'Proceed') and is backed by full Snapshot Undo.\n"
    + "- RULES FOR STATION & FILE ACTIONS:\n"
    + "  a. Component Names: Valid alphanumeric Niagara identifiers (no spaces or special characters like -, /, #).\n"
    + "  b. Wiresheet Layout (Strict Left-to-Right Signal Flow in Wixels; 1 wixel = 10 pixels / 1 grid cell):\n"
    + "     - Stage 0 (Left, x=4): Inputs, Sensors, Setpoints, Constants (spaced vertically: y=4, y=14, y=24...).\n"
    + "     - Stage 1 (Center, x=24): Math, Logic, Processing, PID (spaced vertically: y=8, y=18...).\n"
    + "     - Stage 2+ (Right, x=44, 64...): Downstream Logic, Results, Outputs, Commands, Actuators.\n"
    + "  c. Common Niagara TypeSpecs:\n"
    + "     - Points (control): control:NumericWritable, control:BooleanWritable, control:EnumWritable, control:StringWritable, control:NumericPoint, control:BooleanPoint, control:EnumPoint, control:StringPoint\n"
    + "     - Math (kitControl): kitControl:Add, kitControl:Subtract, kitControl:Multiply, kitControl:Divide, kitControl:Minimum, kitControl:Maximum, kitControl:Average, kitControl:AbsValue, kitControl:SquareRoot, kitControl:MinMaxAvg, kitControl:Reset, kitControl: SineWave\n"
    + "     - Logic & Control (kitControl): kitControl:And, kitControl:Or, kitControl:Not, kitControl:Xor, kitControl:Equal, kitControl:NotEqual, kitControl:GreaterThan, kitControl:GreaterThanEqual, kitControl:LessThan, kitControl:LessThanEqual, kitControl:Hysteresis, kitControl:LoopPoint, kitControl:LeadLagRuntime, kitControl:OneShot, kitControl:Latch, kitControl:Counter, kitControl:NumericSwitch, kitControl:NumericSelect, kitControl:BooleanDelay, kitControl:NumericDelay\n"
    + "     - Extensions & Schedules: history:NumericCovHistoryExt, history:NumericIntervalHistoryExt, history:BooleanCovHistoryExt, alarm:OutOfRangeAlarmExt, alarm:BooleanChangeOfStateAlarmExt, schedule:WeeklySchedule, schedule:BooleanSchedule, schedule:NumericSchedule, schedule:EnumSchedule\n"
    + "     - Containers & Programs: baja:Folder, program:Program\n"
    + "  d. Mandatory NiagaraProxyExt Rule: For Niagara Network proxy points (nd:NiagaraProxyExt / BNiagaraProxyExt), pointId MUST ALWAYS include 'station:|' before 'slot:/...' (e.g., 'station:|slot:/Drivers/BcpBacnetNetwork/FSD_7_A/points/FSD_4_7S7_OPEN_STATUS').\n"
    + "  e. PX Graphic Layout & Table Rules (kitPx / bajaui):\n"
    + "     - When a tab (<LabelPane>) inside a <TabbedPane> has more vertical content than fits in 1320.0,490.0, wrap the tab's <CanvasPane name=\"content\" viewSize=\"1320.0,800.0\"> inside a <ScrollPane name=\"content\"> directly under <LabelPane>.\n"
    + "     - For tabular point displays, prefer <BorderPane> containing a <GridPane name=\"content\" columnCount=\"N\" columnAlign=\"center\" stretchColumn=\"2\"> with status-bubble <BoundLabel> cells (popupEnabled=\"false\" for read-only points, popups enabled for commandable/setpoint points).\n"
    + "  f. Supported JSON Action Objects inside <station-actions>[ ... ]</station-actions>:\n"
    + "     - {\"action\": \"createComponent\", \"type\": \"control:NumericWritable\", \"name\": \"ZoneTemp\", \"parent\": \"activeTarget\", \"x\": 4, \"y\": 4, \"value\": \"72.5\", \"facets\": \"units=u:fahrenheit;°F;(K)|precision=i:1\", \"flags\": \"summary\"}\n"
    + "     - {\"action\": \"setSlotValue\", \"component\": \"ZoneTemp\", \"slot\": \"fallback\", \"value\": \"72.5\"} (supports deep dot-paths like \"proxyExt.pointId\" or \"alarmExt.offnormalAlgorithm.highLimit\")\n"
    + "     - {\"action\": \"addDynamicSlot\", \"component\": \"activeTarget\", \"slot\": \"CustomNote\", \"type\": \"baja:String\", \"value\": \"Verified\", \"flags\": \"summary\"}\n"
    + "     - {\"action\": \"addExtension\", \"component\": \"ZoneTemp\", \"type\": \"history:NumericIntervalHistoryExt\", \"name\": \"HistoryExt\", \"properties\": \"interval=00:15:00;enabled=true\"}\n"
    + "     - {\"action\": \"setFacets\", \"component\": \"ZoneTemp\", \"slot\": \"facets\", \"facets\": \"units=u:fahrenheit;°F;(K)|precision=i:1\"}\n"
    + "     - {\"action\": \"setSlotFlags\", \"component\": \"ZoneTemp\", \"slot\": \"out\", \"flags\": \"+summary,-hidden\"}\n"
    + "     - {\"action\": \"addTag\", \"component\": \"ZoneTemp\", \"tag\": \"n4:hs:zone\", \"value\": \"true\"} / {\"action\": \"removeTag\", \"component\": \"ZoneTemp\", \"tag\": \"n4:hs:zone\"}\n"
    + "     - {\"action\": \"addRelation\", \"source\": \"ZoneTemp\", \"relation\": \"n4:hs:equipRef\", \"target\": \"VAV_101\"}\n"
    + "     - {\"action\": \"link\", \"source\": \"Input1\", \"sourceSlot\": \"out\", \"target\": \"AddBlock\", \"targetSlot\": \"inA\"}\n"
    + "     - {\"action\": \"unlink\", \"source\": \"Input1\", \"target\": \"AddBlock\", \"targetSlot\": \"inA\"}\n"
    + "     - {\"action\": \"renameComponent\", \"oldName\": \"Input1\", \"newName\": \"SupplyAirTemp\"}\n"
    + "     - {\"action\": \"copyComponent\", \"component\": \"VAV_1\", \"newName\": \"VAV_2\", \"parent\": \"activeTarget\"}\n"
    + "     - {\"action\": \"moveComponent\", \"component\": \"Point1\", \"targetParent\": \"SubFolder\"}\n"
    + "     - {\"action\": \"deleteComponent\", \"component\": \"OldBlock\"}\n"
    + "     - {\"action\": \"invokeAction\", \"component\": \"ZoneTemp\", \"actionName\": \"override\", \"arg\": \"74.0\"}\n"
    + "     - {\"action\": \"batchBql\", \"query\": \"station:|slot:/Drivers|bql:select * from control:NumericWritable\", \"subAction\": \"setFacets\", \"slot\": \"facets\", \"facets\": \"units=u:fahrenheit;°F;(K)|precision=i:1\"}\n"
    + "     - {\"action\": \"updateProgram\", \"component\": \"activeTarget\", \"imports\": \"javax.baja.sys.*;\", \"source\": \"public void onExecute() throws Exception { ... }\"}\n"
    + "     - {\"action\": \"updatePxFile\", \"file\": \"activeFile\", \"xml\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><px version=\\\"1.0\\\" media=\\\"workbench:WbPxMedia\\\">...</px>\"}\n"
    + "     - {\"action\": \"patchFile\", \"file\": \"activeFile\", \"target\": \"<old snippet>\", \"replacement\": \"<new snippet>\"}\n"
    + "     - {\"action\": \"writeFile\", \"file\": \"station:|file:^px/NewGraphic.px\", \"content\": \"...\"}\n"
    + "     - {\"action\": \"copyFile\", \"source\": \"station:|file:^px/A.px\", \"target\": \"station:|file:^px/B.px\"} / {\"action\": \"renameFile\", ...} / {\"action\": \"deleteFile\", ...} / {\"action\": \"makeDir\", \"dir\": \"station:|file:^px/Sub\"}\n"
    + "     - {\"action\": \"importBog\", \"parent\": \"activeTarget\", \"bog\": \"<bajaObjectGraph version=\\\"4.0\\\">...</bajaObjectGraph>\"}\n"
    + "     - {\"action\": \"arrangeWiresheet\", \"parent\": \"activeTarget\"}\n\n"
    + "4. PERSISTENT LEARNING — SAVING RULES, STATION MEMORY & SKILLS (<ai-learn>[ ... ]</ai-learn>):\n"
    + "- You have a persistent local AI Brain workspace on the user's computer (shown in the AI BRAIN block below).\n"
    + "- Whenever the user gives you feedback, corrects a mistake, states a rule or preference (e.g., 'Always...', 'Never...', 'Remember...', 'From now on...'), shares architecture notes about the current station, or asks you to save a reusable skill/template, output an <ai-learn>[ ... ]</ai-learn> JSON array so it is permanently saved to disk for future sessions:\n"
    + "  * {\"learn\": \"saveRule\", \"category\": \"Points\", \"rule\": \"Always use control:NumericWritable with fallback set for setpoints instead of NumericPoint.\"}\n"
    + "  * {\"learn\": \"saveStationMemory\", \"station\": \"activeStation\", \"memory\": \"AHU-1 serves Floors 1-3; BACnet trunks are under /Drivers/BcpBacnetNetwork.\"}\n"
    + "  * {\"learn\": \"saveSkill\", \"name\": \"vav_reheat_standard\", \"description\": \"Standard VAV reheat logic and facets\", \"content\": \"...\"}\n";

  private EddiesAiClient()
  {
  }

  public static boolean isPersonalGoogleSelection(String selectedUiModel, EddiesAiConfig cfg)
  {
    return EddiesAiConfig.isPersonalGoogleModelName(selectedUiModel);
  }

  private static int getThinkingBudgetForUiModel(String selectedUiModel)
  {
    if (selectedUiModel == null) return 0;
    String m = selectedUiModel.toLowerCase();
    if (m.contains("argon") && m.contains("high"))
    {
      return 24576; // High reasoning effort
    }
    if (m.contains("argon"))
    {
      return 8192; // Medium reasoning effort (Argon Medium default)
    }
    return 0;
  }

  private static String getDisplayLabelForGoogleModel(String selectedUiModel, String apiModel)
  {
    if (selectedUiModel != null && selectedUiModel.contains("Argon"))
    {
      if (selectedUiModel.contains("High"))
      {
        return "Gemini 4 Argon (High) (Google Ultra)";
      }
      return "Gemini 4 Argon (Medium) (Google Ultra)";
    }
    return apiModel + " (Google Personal)";
  }

  public static Thread testPersonalGoogleConnectionAsync(
    final String candidateApiKey,
    final AiResponseCallback callback)
  {
    Thread worker = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          EddiesAiConfig cfg = EddiesAiConfig.get();
          cfg.load();
          String keyToTest = EddiesAiConfig.sanitizeKey(candidateApiKey);
          if (keyToTest.isEmpty())
          {
            keyToTest = cfg.getGeminiApiKey();
          }
          String oauthToken = "";
          if (keyToTest.isEmpty() && cfg.isGoogleSignedIn())
          {
            oauthToken = EddiesAiAuthManager.getValidGoogleAccessToken();
          }
          if (!EddiesAiConfig.isDirectGoogleAiStudioKey(keyToTest) && oauthToken.isEmpty())
          {
            callback.onError("Please paste a valid Google Gemini API Key (starting with AIza...) or sign in via Google OAuth first.");
            return;
          }

          String testModel = "gemini-2.5-flash";
          String reply = callGoogleDirectGemini(
            testModel,
            keyToTest,
            oauthToken,
            cfg.getGoogleOAuthProjectId(),
            "You are Eddie's Niagara Workbench AI Assistant.",
            "Reply with one short sentence confirming that Eddie's personal Google Gemini connection is working.",
            cfg);
          callback.onSuccess(reply, testModel + " (Direct Google API)");
        }
        catch (Exception e)
        {
          callback.onError(safeMessage(e));
        }
      }
    }, "EddiesWorkbenchAi-GoogleTest");
    worker.setDaemon(true);
    worker.start();
    return worker;
  }

  public static Thread sendPromptAsync(
    final String selectedUiModel,
    final String stationContextBlock,
    final String userPrompt,
    final AiResponseCallback callback)
  {
    return sendPromptAsync(selectedUiModel, "ActiveStation", stationContextBlock, userPrompt, callback);
  }

  public static Thread sendPromptAsync(
    final String selectedUiModel,
    final String activeStationKey,
    final String stationContextBlock,
    final String userPrompt,
    final AiResponseCallback callback)
  {
    Thread worker = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          EddiesAiConfig cfg = EddiesAiConfig.get();
          cfg.load();

          String brainBlock = "";
          try
          {
            brainBlock = EddiesAiBrainManager.buildBrainPromptBlock(activeStationKey);
          }
          catch (Throwable ignore)
          {
          }

          StringBuilder combinedCtx = new StringBuilder();
          if (brainBlock != null && !brainBlock.trim().isEmpty())
          {
            combinedCtx.append(brainBlock.trim()).append("\n\n");
          }
          if (stationContextBlock != null && !stationContextBlock.trim().isEmpty())
          {
            combinedCtx.append(stationContextBlock.trim()).append("\n\n");
          }
          combinedCtx.append("USER PROMPT:\n").append(userPrompt);
          String fullUserContent = combinedCtx.toString();

          boolean useGooglePersonal = isPersonalGoogleSelection(selectedUiModel, cfg);

          // Route 1: Personal Google Account (Direct Gemini API) — strictly requires a direct AIza... key or Google OAuth
          if (useGooglePersonal)
          {
            String geminiKey = cfg.getGeminiApiKey();
            if (!EddiesAiConfig.isDirectGoogleAiStudioKey(geminiKey))
            {
              geminiKey = "";
            }
            String googleOAuthToken = "";
            if (geminiKey.isEmpty() && cfg.isGoogleSignedIn())
            {
              googleOAuthToken = EddiesAiAuthManager.getValidGoogleAccessToken();
            }

            if (geminiKey.isEmpty() && googleOAuthToken.isEmpty())
            {
              callback.onError(
                "No Personal Google credential is configured for '" + selectedUiModel + "'.\n"
                + "Click 'Account' at the top-right of this panel to:\n"
                + "  1. Enter your Personal Google Gemini API Key (AIza... from aistudio.google.com/app/apikey), OR\n"
                + "  2. Sign in via Google OAuth / CLI.");
              return;
            }

            String googleModel = mapUiModelToGoogleDirectModel(selectedUiModel, cfg);
            int thinkingBudget = getThinkingBudgetForUiModel(selectedUiModel);
            String displayLabel = getDisplayLabelForGoogleModel(selectedUiModel, googleModel);
            try
            {
              String reply = callGoogleDirectGemini(
                googleModel,
                geminiKey,
                googleOAuthToken,
                cfg.getGoogleOAuthProjectId(),
                SYSTEM_INSTRUCTIONS,
                fullUserContent,
                thinkingBudget,
                cfg);
              callback.onSuccess(reply, displayLabel);
              return;
            }
            catch (Exception primaryGoogleErr)
            {
              String fallbackGoogleModel = "gemini-2.5-flash";
              if (!fallbackGoogleModel.equalsIgnoreCase(googleModel)
                && primaryGoogleErr.getMessage() != null
                && (primaryGoogleErr.getMessage().contains("HTTP 404")
                    || primaryGoogleErr.getMessage().contains("HTTP 429")
                    || primaryGoogleErr.getMessage().contains("NOT_FOUND")))
              {
                try
                {
                  String reply = callGoogleDirectGemini(
                    fallbackGoogleModel,
                    geminiKey,
                    googleOAuthToken,
                    cfg.getGoogleOAuthProjectId(),
                    SYSTEM_INSTRUCTIONS,
                    fullUserContent,
                    thinkingBudget,
                    cfg);
                  callback.onSuccess(reply, displayLabel + " [" + fallbackGoogleModel + "]");
                  return;
                }
                catch (Exception ignoreFallbackErr)
                {
                }
              }
              throw primaryGoogleErr;
            }
          }

          // Route 2: Company AI Platform / Model Farm Gateway — strictly requires Company Subscription Key or M365 SSO
          String domain = cfg.getModelFarmDomain();
          String apiKey = cfg.getModelFarmApiKey();
          String subscriptionId = cfg.getModelFarmSubscriptionId();

          if (apiKey.isEmpty() && cfg.isCopilotSignedIn())
          {
            apiKey = EddiesAiAuthManager.getValidCopilotAccessToken();
          }

          if (apiKey == null || apiKey.trim().isEmpty())
          {
            callback.onError(
              "No Company AI credentials or API key are configured for '" + selectedUiModel + "'.\n"
              + "Click 'Account' at the top-right of this panel to:\n"
              + "  1. Enter your Company Model Farm Subscription Key (https://aoai-farm.bosch-temp.com), OR\n"
              + "  2. Click 'Sign in with Microsoft 365 (Browser SSO)'.");
            return;
          }

          String targetModel = mapUiModelToDeployment(selectedUiModel, cfg);
          try
          {
            String reply = callCompanyGateway(
              domain, apiKey, subscriptionId, targetModel, SYSTEM_INSTRUCTIONS, fullUserContent, cfg);
            String label = (selectedUiModel != null && selectedUiModel.contains("Argon"))
              ? (selectedUiModel + " [" + targetModel + "]")
              : targetModel;
            callback.onSuccess(reply, label);
          }
          catch (Exception primaryErr)
          {
            String msg = primaryErr.getMessage() != null ? primaryErr.getMessage() : "";
            boolean isAuthOrNotFound = msg.contains("HTTP 401")
              || msg.contains("HTTP 403")
              || msg.contains("HTTP 404")
              || msg.contains("DeploymentNotFound");

            if (isAuthOrNotFound)
            {
              // If a Gemini model (e.g. gemini-2.5-pro) requires Enterprise tier, try gemini-2.5-flash-lite first
              String geminiLite = "gemini-2.5-flash-lite";
              if (targetModel.toLowerCase().startsWith("gemini-") && !geminiLite.equalsIgnoreCase(targetModel))
              {
                try
                {
                  String reply = callCompanyGateway(
                    domain, apiKey, subscriptionId, geminiLite, SYSTEM_INSTRUCTIONS, fullUserContent, cfg);
                  callback.onSuccess(reply, selectedUiModel + " [" + geminiLite + "]");
                  return;
                }
                catch (Exception ignoreGeminiLiteErr)
                {
                }
              }

              String fallbackModel = "gpt-5-nano-2025-08-07";
              if (!fallbackModel.equalsIgnoreCase(targetModel))
              {
                String reply = callCompanyGateway(
                  domain, apiKey, subscriptionId, fallbackModel, SYSTEM_INSTRUCTIONS, fullUserContent, cfg);
                callback.onSuccess(reply, selectedUiModel + " [" + fallbackModel + "]");
                return;
              }
            }
            throw primaryErr;
          }
        }
        catch (Exception e)
        {
          callback.onError(safeMessage(e));
        }
      }
    }, "EddiesWorkbenchAi-Request");
    worker.setDaemon(true);
    worker.start();
    return worker;
  }

  private static String mapUiModelToGoogleDirectModel(String uiModel, EddiesAiConfig cfg)
  {
    if (uiModel == null || uiModel.trim().isEmpty())
    {
      return "gemini-2.5-pro";
    }
    String m = uiModel.trim().toLowerCase();
    if (m.contains("gemini-2.5-flash-lite"))
    {
      return "gemini-2.5-flash-lite";
    }
    if (m.contains("gemini-2.0-flash-lite"))
    {
      return "gemini-2.0-flash-lite";
    }
    if (m.contains("gemini-2.5-flash"))
    {
      return "gemini-2.5-flash";
    }
    if (m.contains("gemini-2.0-flash"))
    {
      return "gemini-2.0-flash";
    }
    if (m.contains("gemini-2.5-pro"))
    {
      return "gemini-2.5-pro";
    }
    if (m.contains("argon"))
    {
      String custom = cfg != null ? cfg.getGeminiArgonModelId() : "gemini-2.5-pro";
      return (custom != null && !custom.trim().isEmpty()) ? custom.trim() : "gemini-2.5-pro";
    }
    return "gemini-2.5-pro";
  }

  private static String mapUiModelToDeployment(String uiModel, EddiesAiConfig cfg)
  {
    if (uiModel == null || uiModel.trim().isEmpty())
    {
      return "gpt-5-nano-2025-08-07";
    }
    String m = uiModel.trim();
    if (m.contains("gpt-5-nano"))
    {
      return "gpt-5-nano-2025-08-07";
    }
    if (m.contains("askbosch") || m.contains("gpt-4o-mini"))
    {
      return "askbosch-prod-farm-openai-gpt-4o-mini-2024-07-18";
    }
    if (m.contains("Argon"))
    {
      String custom = cfg.getGeminiArgonModelId();
      if (custom != null && !custom.isEmpty() && !"gemini-2.5-pro".equals(custom))
      {
        return custom;
      }
      return "gemini-2.5-flash-lite";
    }
    if (m.contains("gemini-2.5-flash-lite") || m.contains("gemini-2.0-flash-lite"))
    {
      return "gemini-2.5-flash-lite";
    }
    if (m.contains("gemini-3.5-flash"))
    {
      return "gemini-3.5-flash";
    }
    if (m.contains("gemini-2.5-pro") || m.equalsIgnoreCase("Gemini 2.5 Pro"))
    {
      return "gemini-2.5-pro";
    }
    if (m.contains("gemini-2.5-flash") || m.equalsIgnoreCase("Gemini 2.5 Flash"))
    {
      return "gemini-2.5-flash";
    }
    if (m.contains("gpt-5.6-sol"))
    {
      return "gpt-5.6-sol-2026-07-09";
    }
    if (m.contains("gpt-5-2025-08-07"))
    {
      return "gpt-5-2025-08-07";
    }
    if (m.contains("claude-sonnet-4-5") || m.contains("Claude Sonnet"))
    {
      return "claude-sonnet-4-5@20250929";
    }
    if (m.contains("claude-opus-4-8") || m.contains("Claude Opus"))
    {
      return "claude-opus-4-8";
    }
    return m;
  }

  /**
   * Calls Google's official Generative Language API directly:
   * https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
   *
   * Security note: The API key is passed exclusively via the x-goog-api-key HTTP header
   * (never appended as ?key= in the URL), preventing any leakage in exception messages or logs.
   */
  private static String callGoogleDirectGemini(
    String model,
    String geminiApiKey,
    String googleOAuthToken,
    String googleProjectId,
    String instructions,
    String prompt,
    EddiesAiConfig cfg) throws Exception
  {
    return callGoogleDirectGemini(model, geminiApiKey, googleOAuthToken, googleProjectId, instructions, prompt, 0, cfg);
  }

  private static String callGoogleDirectGemini(
    String model,
    String geminiApiKey,
    String googleOAuthToken,
    String googleProjectId,
    String instructions,
    String prompt,
    int thinkingBudget,
    EddiesAiConfig cfg) throws Exception
  {
    HttpURLConnection connection = null;
    String cleanModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.5-pro";
    if (cleanModel.startsWith("models/"))
    {
      cleanModel = cleanModel.substring("models/".length());
    }
    String endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
      + URLEncoder.encode(cleanModel, "UTF-8") + ":generateContent";

    try
    {
      String thinkingCfg = (thinkingBudget > 0)
        ? ",\"generationConfig\":{\"thinkingConfig\":{\"thinkingBudget\":" + thinkingBudget + "}}"
        : "";
      String requestBody = "{"
        + "\"systemInstruction\":{\"parts\":[{\"text\":" + jsonString(instructions) + "}]},"
        + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":" + jsonString(prompt) + "}]}]"
        + thinkingCfg
        + "}";
      byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);

      URL url = new URL(endpointUrl);
      if (cfg != null && cfg.isModelFarmUseProxy())
      {
        Proxy proxy = new Proxy(
          Proxy.Type.HTTP,
          new InetSocketAddress(cfg.getModelFarmProxyHost(), cfg.getModelFarmProxyPort()));
        connection = (HttpURLConnection) url.openConnection(proxy);
      }
      else
      {
        connection = (HttpURLConnection) url.openConnection();
      }

      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);

      if (geminiApiKey != null && !geminiApiKey.trim().isEmpty())
      {
        connection.setRequestProperty("x-goog-api-key", geminiApiKey.trim());
      }
      else if (googleOAuthToken != null && !googleOAuthToken.trim().isEmpty())
      {
        connection.setRequestProperty("Authorization", "Bearer " + googleOAuthToken.trim());
        if (googleProjectId != null && !googleProjectId.trim().isEmpty())
        {
          connection.setRequestProperty("x-goog-user-project", googleProjectId.trim());
        }
      }

      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      connection.setRequestProperty("Accept", "application/json");
      connection.setFixedLengthStreamingMode(requestBytes.length);

      OutputStream out = connection.getOutputStream();
      try
      {
        out.write(requestBytes);
        out.flush();
      }
      finally
      {
        try { out.close(); } catch (Exception ignore) {}
      }

      int code = connection.getResponseCode();
      String body = readFully(code >= 400 ? connection.getErrorStream() : connection.getInputStream());

      if (code >= 400)
      {
        if (code == 400 && thinkingBudget > 0)
        {
          return callGoogleDirectGemini(
            model, geminiApiKey, googleOAuthToken, googleProjectId, instructions, prompt, 0, cfg);
        }
        String detail = extractErrorMessage(body);
        throw new Exception("Google Gemini API HTTP " + code + " (" + cleanModel + "): " + detail);
      }

      String text = extractGeminiDirectContent(body);
      if (text.isEmpty())
      {
        throw new Exception("No text returned by Google Gemini API (" + cleanModel + "). Response: " + trimToLength(body));
      }
      return text;
    }
    finally
    {
      if (connection != null)
      {
        connection.disconnect();
      }
    }
  }

  private static String callCompanyGateway(
    String domain,
    String apiKey,
    String subscriptionId,
    String model,
    String instructions,
    String prompt,
    EddiesAiConfig cfg) throws Exception
  {
    HttpURLConnection connection = null;
    String endpointUrl = "";

    try
    {
      String d = (domain != null && !domain.trim().isEmpty())
        ? domain.trim()
        : "https://aoai-farm.bosch-temp.com";

      if (!d.startsWith("http://") && !d.startsWith("https://"))
      {
        d = "https://" + d;
      }
      while (d.endsWith("/"))
      {
        d = d.substring(0, d.length() - 1);
      }

      if ((d.contains("aoai-farm") || d.contains("ai-gateway")) && !d.contains("/api"))
      {
        d = d + "/api";
      }

      boolean isAnthropicVertex = model.startsWith("claude-");
      String requestBody;

      if (isAnthropicVertex)
      {
        endpointUrl = d + "/google/v1/publishers/anthropic/models/" + URLEncoder.encode(model, "UTF-8") + ":rawPredict";
        requestBody = "{"
          + "\"anthropic_version\":\"vertex-2023-10-16\","
          + "\"max_tokens\":4096,"
          + "\"system\":" + jsonString(instructions) + ","
          + "\"messages\":["
          + "{\"role\":\"user\",\"content\":" + jsonString(prompt) + "}"
          + "]"
          + "}";
      }
      else
      {
        String deploymentName = model;
        if ("gemini-2.0-flash-lite".equalsIgnoreCase(model))
        {
          deploymentName = "gemini-2.5-flash-lite";
        }
        else if ("gemini-2.0-flash".equalsIgnoreCase(model))
        {
          deploymentName = "gemini-2.5-flash";
        }

        if (d.contains("/deployments/") || d.contains("/generateContent") || d.contains("/v1/chat") || d.contains("/publishers/"))
        {
          endpointUrl = d;
        }
        else
        {
          endpointUrl = d + "/openai/deployments/" + URLEncoder.encode(deploymentName, "UTF-8") + "/chat/completions";
          if (deploymentName.startsWith("gpt-") || deploymentName.startsWith("askbosch-")
            || deploymentName.startsWith("company-") || deploymentName.startsWith("o1")
            || deploymentName.startsWith("o3") || deploymentName.startsWith("o4"))
          {
            if (!endpointUrl.contains("api-version="))
            {
              endpointUrl += "?api-version=2025-04-01-preview";
            }
          }
        }

        String bodyModel = deploymentName.toLowerCase().startsWith("gemini-")
          ? ("google/" + deploymentName)
          : model;

        requestBody = "{"
          + "\"model\":" + jsonString(bodyModel) + ","
          + "\"messages\":["
          + "{\"role\":\"system\",\"content\":" + jsonString(instructions) + "},"
          + "{\"role\":\"user\",\"content\":" + jsonString(prompt) + "}"
          + "]"
          + "}";
      }

      byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);

      URL url = new URL(endpointUrl);
      if (cfg != null && cfg.isModelFarmUseProxy())
      {
        Proxy proxy = new Proxy(
          Proxy.Type.HTTP,
          new InetSocketAddress(cfg.getModelFarmProxyHost(), cfg.getModelFarmProxyPort()));
        connection = (HttpURLConnection) url.openConnection(proxy);
      }
      else
      {
        connection = (HttpURLConnection) url.openConnection();
      }

      connection.setRequestMethod("POST");
      connection.setDoOutput(true);
      connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);

      // 1. Custom Subscription Key Header as required by the Company AI Platform
      connection.setRequestProperty("genaiplatform-farm-subscription-key", apiKey);

      // 2. Azure APIM & standard API gateway subscription headers
      connection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey);
      connection.setRequestProperty("api-key", apiKey);
      connection.setRequestProperty("Authorization", "Bearer " + apiKey);
      connection.setRequestProperty("x-goog-api-key", apiKey);

      // 3. Subscription ID headers for Token Tracking Dashboard
      if (subscriptionId != null && !subscriptionId.isEmpty())
      {
        connection.setRequestProperty("genaiplatform-farm-subscription-id", subscriptionId);
        connection.setRequestProperty("Subscription-Id", subscriptionId);
        connection.setRequestProperty("subscription-id", subscriptionId);
        connection.setRequestProperty("x-subscription-id", subscriptionId);
      }

      connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
      connection.setRequestProperty("Accept", "application/json");
      connection.setFixedLengthStreamingMode(requestBytes.length);

      OutputStream out = connection.getOutputStream();
      try
      {
        out.write(requestBytes);
        out.flush();
      }
      finally
      {
        try { out.close(); } catch (Exception ignore) {}
      }

      int code = connection.getResponseCode();
      String body = readFully(code >= 400 ? connection.getErrorStream() : connection.getInputStream());

      if (code >= 400)
      {
        String detail = extractErrorMessage(body);
        throw new Exception("HTTP " + code + " on [" + endpointUrl + "]: " + detail);
      }

      String text = extractAssistantContent(body);
      if (text.isEmpty())
      {
        throw new Exception("No assistant content found in AI response from [" + endpointUrl + "]. Response: " + trimToLength(body));
      }
      return text;
    }
    finally
    {
      if (connection != null)
      {
        connection.disconnect();
      }
    }
  }

  /**
   * Extracts the assistant response from Google's native generateContent JSON
   * ("candidates":[{"content":{"parts":[{"text":"..."}]}}]).
   * Handles multi-part responses (such as Gemini 2.5 thought parts followed by the response part).
   */
  private static String extractGeminiDirectContent(String json)
  {
    if (json == null || json.isEmpty()) return "";
    StringBuilder nonThoughtText = new StringBuilder();
    String lastTextPart = "";

    int searchFrom = 0;
    while (true)
    {
      int textKeyIdx = json.indexOf("\"text\"", searchFrom);
      if (textKeyIdx < 0) break;

      int colonIdx = json.indexOf(':', textKeyIdx + 6);
      if (colonIdx < 0) break;

      int quoteStart = json.indexOf('"', colonIdx + 1);
      if (quoteStart < 0) break;

      int quoteEnd = StationActionExecutor.findJsonStringEnd(json, quoteStart + 1);
      if (quoteEnd <= quoteStart) break;

      String extracted = StationActionExecutor.unescapeJson(json.substring(quoteStart + 1, quoteEnd));
      lastTextPart = extracted;

      // Check nearby in this part object if "thought": true is present
      int partWindowStart = Math.max(0, textKeyIdx - 80);
      int partWindowEnd = Math.min(json.length(), quoteEnd + 80);
      String window = json.substring(partWindowStart, partWindowEnd);
      boolean isThought = window.contains("\"thought\":true") || window.contains("\"thought\": true");

      if (!isThought)
      {
        if (nonThoughtText.length() > 0)
        {
          nonThoughtText.append("\n");
        }
        nonThoughtText.append(extracted);
      }

      searchFrom = quoteEnd + 1;
    }

    if (nonThoughtText.length() > 0)
    {
      return nonThoughtText.toString();
    }
    return lastTextPart;
  }

  private static String extractAssistantContent(String json)
  {
    if (json == null) return "";

    // 1. OpenAI / Azure OpenAI format: "message":{"content":"..."
    int messageIndex = json.indexOf("\"message\":");
    if (messageIndex >= 0)
    {
      int contentIndex = json.indexOf("\"content\":", messageIndex);
      if (contentIndex >= 0)
      {
        int quoteStart = json.indexOf('"', contentIndex + 10);
        if (quoteStart >= 0)
        {
          int quoteEnd = StationActionExecutor.findJsonStringEnd(json, quoteStart + 1);
          if (quoteEnd > quoteStart)
          {
            return StationActionExecutor.unescapeJson(json.substring(quoteStart + 1, quoteEnd));
          }
        }
      }
    }

    // 2. Anthropic / Vertex / Gemini format: "text":"..."
    return extractGeminiDirectContent(json);
  }

  private static String extractErrorMessage(String json)
  {
    if (json == null || json.isEmpty()) return "Empty error response";
    String msg = EddiesAiAuthManager.extractJsonString(json, "message");
    if (msg != null && !msg.isEmpty()) return msg;
    return trimToLength(json);
  }

  private static String jsonString(String value)
  {
    if (value == null) return "\"\"";
    StringBuilder out = new StringBuilder(value.length() + 16);
    out.append('"');
    for (int i = 0; i < value.length(); ++i)
    {
      char c = value.charAt(i);
      switch (c)
      {
        case '\\': out.append("\\\\"); break;
        case '"': out.append("\\\""); break;
        case '\b': out.append("\\b"); break;
        case '\f': out.append("\\f"); break;
        case '\n': out.append("\\n"); break;
        case '\r': out.append("\\r"); break;
        case '\t': out.append("\\t"); break;
        default:
          if (c < 0x20)
          {
            String hex = Integer.toHexString(c);
            out.append("\\u");
            for (int j = hex.length(); j < 4; ++j) out.append('0');
            out.append(hex);
          }
          else
          {
            out.append(c);
          }
      }
    }
    out.append('"');
    return out.toString();
  }

  private static String readFully(InputStream in) throws Exception
  {
    if (in == null) return "";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    try
    {
      while (true)
      {
        int count = in.read(buffer);
        if (count < 0) break;
        out.write(buffer, 0, count);
      }
    }
    finally
    {
      try { in.close(); } catch (Exception ignore) {}
    }
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String trimToLength(String value)
  {
    if (value == null) return "";
    String v = value.trim();
    if (v.length() <= 260) return v;
    return v.substring(0, 260) + "...";
  }

  private static String safeMessage(Throwable t)
  {
    String message = t.getMessage();
    if (message == null || message.isEmpty())
      return t.getClass().getName();
    return message;
  }
}
