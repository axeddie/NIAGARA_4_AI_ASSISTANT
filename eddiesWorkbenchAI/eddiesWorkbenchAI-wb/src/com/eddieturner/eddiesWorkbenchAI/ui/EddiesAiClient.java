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
 * Asynchronous client for Eddie's Company Copilot / AI Platform Gateway
 * (https://aoai-farm.bosch-temp.com) and Microsoft 365 Work SSO.
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
    + "You are operating inside Niagara Workbench connected to the active station view shown in the context block.\n\n"
    + "WHEN THE USER ASKS A QUESTION OR REQUESTS EXPLANATION / BQL / TROUBLESHOOTING:\n"
    + "- Provide a clear, concise, technical response referencing the exact components, properties, facets, and links in the active view context.\n\n"
    + "WHEN THE USER ASKS TO CREATE, MODIFY, SET, LINK, RENAME, OR DELETE ANYTHING IN THE STATION:\n"
    + "- Briefly explain what you are doing, and then output a <station-actions>...</station-actions> block containing a valid JSON array of action objects.\n"
    + "- RULES FOR STATION ACTIONS:\n"
    + "  1. Component Names: Valid alphanumeric Niagara identifiers (no spaces or special characters like -, /, #).\n"
    + "  2. Wiresheet Layout (Strict Left-to-Right Signal Flow in Wixels; 1 wixel = 10 pixels / 1 grid cell):\n"
    + "     - Stage 0 (Left, x=4): Inputs, Sensors, Setpoints, Constants (spaced vertically: y=4, y=14, y=24...).\n"
    + "     - Stage 1 (Center, x=24): Math, Logic, Processing, PID (spaced vertically: y=8, y=18...).\n"
    + "     - Stage 2+ (Right, x=44, 64...): Downstream Logic, Results, Outputs, Commands, Actuators.\n"
    + "     - Note: The Workbench executor also runs an automatic topological left-to-right signal-flow and 5->4->3 column grid layout pass with dynamic block widths (min 10 wixels).\n"
    + "  3. Common Niagara TypeSpecs:\n"
    + "     - Points (control): control:NumericWritable, control:BooleanWritable, control:EnumWritable, control:StringWritable, control:NumericPoint, control:BooleanPoint\n"
    + "     - Math (kitControl): kitControl:Add, kitControl:Subtract, kitControl:Multiply, kitControl:Divide, kitControl:Minimum, kitControl:Maximum, kitControl:Average, kitControl:AbsValue, kitControl:SquareRoot, kitControl:MinMaxAvg, kitControl:Reset\n"
    + "     - Logic (kitControl): kitControl:And, kitControl:Or, kitControl:Not, kitControl:Xor, kitControl:Equal, kitControl:GreaterThan, kitControl:LessThan, kitControl:Hysteresis, kitControl:LoopPoint, kitControl:LeadLagRuntime\n"
    + "     - Containers & Programs: baja:Folder, program:Program\n"
    + "  4. Common Slot Names:\n"
    + "     - NumericWritable/BooleanWritable/StringWritable: output is 'out'; default writable input is 'fallback' (or in1..in16).\n"
    + "     - Math & Logic blocks: inputs are 'inA', 'inB', 'inC', 'inD'; output is 'out'.\n"
    + "     - LoopPoint (PID): inputs are 'controlledVariable', 'setpoint'; output is 'out'.\n"
    + "  5. NiagaraProxyExt Rule: For Niagara Network proxy points (nd:NiagaraProxyExt), pointId MUST always include 'station:|' before 'slot:/...'.\n"
    + "  6. Supported JSON Action Objects inside <station-actions>[ ... ]</station-actions>:\n"
    + "     - {\"action\": \"createComponent\", \"type\": \"control:NumericWritable\", \"name\": \"ZoneTemp\", \"x\": 4, \"y\": 4, \"value\": \"72.5\"}\n"
    + "     - {\"action\": \"setSlotValue\", \"component\": \"ZoneTemp\", \"slot\": \"fallback\", \"value\": \"72.5\"}\n"
    + "     - {\"action\": \"setFacets\", \"component\": \"ZoneTemp\", \"slot\": \"facets\", \"facets\": \"units=u:fahrenheit;°F;(K)|precision=i:1\"}\n"
    + "     - {\"action\": \"link\", \"source\": \"Input1\", \"sourceSlot\": \"out\", \"target\": \"AddBlock\", \"targetSlot\": \"inA\"}\n"
    + "     - {\"action\": \"unlink\", \"source\": \"Input1\", \"target\": \"AddBlock\", \"targetSlot\": \"inA\"}\n"
    + "     - {\"action\": \"renameComponent\", \"oldName\": \"Input1\", \"newName\": \"SupplyAirTemp\"}\n"
    + "     - {\"action\": \"deleteComponent\", \"component\": \"OldBlock\"}\n"
    + "     - {\"action\": \"invokeAction\", \"component\": \"PointName\", \"actionName\": \"auto\"}\n"
    + "     - {\"action\": \"updateProgram\", \"component\": \"activeTarget\", \"source\": \"public void onExecute() throws Exception { ... }\"}\n"
    + "     - {\"action\": \"updatePxFile\", \"xml\": \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><px version=\\\"1.0\\\" media=\\\"workbench:WbPxMedia\\\">...</px>\"}\n"
    + "     - {\"action\": \"importBog\", \"bog\": \"<bajaObjectGraph version=\\\"4.0\\\">...</bajaObjectGraph>\"}\n"
    + "     - {\"action\": \"arrangeWiresheet\", \"parent\": \"activeTarget\"}\n";

  private EddiesAiClient()
  {
  }

  public static Thread sendPromptAsync(
    final String selectedUiModel,
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
          String domain = cfg.getModelFarmDomain();
          String apiKey = cfg.getModelFarmApiKey();
          String subscriptionId = cfg.getModelFarmSubscriptionId();

          // If no Model Farm Subscription Key is set yet, check if signed in with Microsoft 365 SSO token
          if (apiKey.isEmpty() && cfg.isCopilotSignedIn())
          {
            apiKey = EddiesAiAuthManager.getValidCopilotAccessToken();
          }

          if (apiKey == null || apiKey.trim().isEmpty())
          {
            callback.onError(
              "Company Copilot / Model Farm key is not configured yet.\n"
              + "Click 'Account / Login' at the top of this sidebar to:\n"
              + "  1. Enter your Company Subscription Key (for https://aoai-farm.bosch-temp.com), OR\n"
              + "  2. Click 'Import from Active Station Program' if a StationBuilder Program block is on your WireSheet, OR\n"
              + "  3. Sign in with your Microsoft 365 Work Account (SSO).");
            return;
          }

          String fullUserContent = "";
          if (stationContextBlock != null && !stationContextBlock.trim().isEmpty())
          {
            fullUserContent = stationContextBlock + "\n\nUSER PROMPT:\n" + userPrompt;
          }
          else
          {
            fullUserContent = userPrompt;
          }

          String targetModel = mapUiModelToDeployment(selectedUiModel, cfg);
          try
          {
            String reply = callCompanyGateway(
              domain, apiKey, subscriptionId, targetModel, SYSTEM_INSTRUCTIONS, fullUserContent, cfg);
            callback.onSuccess(reply, targetModel);
          }
          catch (Exception primaryErr)
          {
            // If user selected Google Gemini 4 Argon or an Enterprise model not enabled on a Developer tier key,
            // automatically try fallback to the verified working gpt-5-nano-2025-08-07 deployment!
            String fallbackModel = "gpt-5-nano-2025-08-07";
            if (!fallbackModel.equalsIgnoreCase(targetModel)
              && (primaryErr.getMessage() != null)
              && (primaryErr.getMessage().contains("HTTP 404")
                  || primaryErr.getMessage().contains("HTTP 403")
                  || primaryErr.getMessage().contains("DeploymentNotFound")))
            {
              String reply = callCompanyGateway(
                domain, apiKey, subscriptionId, fallbackModel, SYSTEM_INSTRUCTIONS, fullUserContent, cfg);
              callback.onSuccess(reply, selectedUiModel + " -> " + fallbackModel);
            }
            else
            {
              throw primaryErr;
            }
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
      return "gpt-5-nano-2025-08-07";
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
        if ("gemini-2.0-flash-lite".equalsIgnoreCase(model) || "gemini-2.5-flash-lite".equalsIgnoreCase(model))
        {
          deploymentName = "google-gemini-2-0-flash-lite";
        }
        else if ("gemini-2.5-flash".equalsIgnoreCase(model) || "gemini-2.0-flash".equalsIgnoreCase(model))
        {
          deploymentName = "google-gemini-2-5-flash";
        }
        else if ("gemini-2.5-pro".equalsIgnoreCase(model))
        {
          deploymentName = "google-gemini-2-5-pro";
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

        requestBody = "{"
          + "\"model\":" + jsonString(model) + ","
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

    // 2. Anthropic / Vertex format: "text":"..."
    int textIndex = json.indexOf("\"text\":");
    if (textIndex >= 0)
    {
      int quoteStart = json.indexOf('"', textIndex + 7);
      if (quoteStart >= 0)
      {
        int quoteEnd = StationActionExecutor.findJsonStringEnd(json, quoteStart + 1);
        if (quoteEnd > quoteStart)
        {
          return StationActionExecutor.unescapeJson(json.substring(quoteStart + 1, quoteEnd));
        }
      }
    }

    return "";
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
