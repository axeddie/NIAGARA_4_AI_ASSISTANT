/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import javax.baja.sys.Sys;

/**
 * Persistent configuration and credential store for Eddie's Workbench AI Assistant.
 * Saves user preferences, Company Model Farm keys/settings, OAuth tokens
 * (Google & Microsoft 365 Copilot SSO), and API keys inside the Niagara user home directory.
 */
public final class EddiesAiConfig
{
  private static final EddiesAiConfig INSTANCE = new EddiesAiConfig();

  private final Properties props = new Properties();
  private final File configFile;

  public static EddiesAiConfig get()
  {
    return INSTANCE;
  }

  private EddiesAiConfig()
  {
    File baseDir = null;
    try
    {
      baseDir = Sys.getNiagaraUserHome();
    }
    catch (Throwable ignore)
    {
    }
    try
    {
      if (baseDir == null)
      {
        baseDir = new File(safeGetProperty("user.home", "."), ".eddiesWorkbenchAI");
      }
      if (!baseDir.exists())
      {
        baseDir.mkdirs();
      }
    }
    catch (Throwable ignore)
    {
    }
    this.configFile = (baseDir != null)
      ? new File(baseDir, "eddiesWorkbenchAI.properties")
      : new File("eddiesWorkbenchAI.properties");
    load();
  }

  public static String safeGetenv(String name)
  {
    if (name == null || name.isEmpty()) return null;
    try
    {
      return System.getenv(name);
    }
    catch (Throwable ignore)
    {
      return null;
    }
  }

  public static String safeGetProperty(String key, String def)
  {
    if (key == null || key.isEmpty()) return def;
    try
    {
      return System.getProperty(key, def);
    }
    catch (Throwable ignore)
    {
      return def;
    }
  }

  public synchronized void load()
  {
    FileInputStream in = null;
    try
    {
      if (configFile == null || !configFile.exists()) return;
      in = new FileInputStream(configFile);
      props.load(in);
    }
    catch (Throwable ignore)
    {
    }
    finally
    {
      if (in != null)
      {
        try { in.close(); } catch (Throwable ignore) {}
      }
    }
  }

  public synchronized void save()
  {
    FileOutputStream out = null;
    try
    {
      if (configFile == null) return;
      File parent = configFile.getParentFile();
      if (parent != null && !parent.exists())
      {
        parent.mkdirs();
      }
      out = new FileOutputStream(configFile);
      props.store(out, "Eddie's Workbench AI Assistant Configuration");
    }
    catch (Throwable ignore)
    {
    }
    finally
    {
      if (out != null)
      {
        try { out.close(); } catch (Throwable ignore) {}
      }
    }
  }

  public File getConfigFile()
  {
    return configFile;
  }

  private synchronized String getStr(String key, String def)
  {
    String val = props.getProperty(key);
    return (val != null) ? val.trim() : def;
  }

  private synchronized void setStr(String key, String val)
  {
    if (val == null)
    {
      props.remove(key);
    }
    else
    {
      props.setProperty(key, val.trim());
    }
  }

  // ---- General UI & Execution Preferences ----

  public String getSelectedModel()
  {
    return getStr("selectedModel", "gpt-5-nano-2025-08-07 (Company Farm ⭐)");
  }

  public void setSelectedModel(String model)
  {
    setStr("selectedModel", model);
  }

  public boolean isAutoApplyChanges()
  {
    return Boolean.parseBoolean(getStr("autoApplyChanges", "false"));
  }

  public void setAutoApplyChanges(boolean autoApply)
  {
    setStr("autoApplyChanges", Boolean.toString(autoApply));
  }

  // ---- Company AI Gateway / Model Farm (https://aoai-farm.bosch-temp.com) ----

  public String getModelFarmDomain()
  {
    return getStr("modelFarmDomain", "https://aoai-farm.bosch-temp.com");
  }

  public void setModelFarmDomain(String domain)
  {
    setStr("modelFarmDomain", domain);
  }

  public String getModelFarmApiKey()
  {
    String key = getStr("modelFarmApiKey", "");
    if (key.isEmpty())
    {
      String[] envVars = {
        "GENAIPLATFORM_FARM_SUBSCRIPTION_KEY",
        "MODEL_FARM_API_KEY",
        "MODEL_FARM_SUBSCRIPTION_KEY",
        "FARM_API_KEY",
        "COMPANY_API_KEY",
        "LLMFARM_API_KEY"
      };
      for (int i = 0; i < envVars.length; i++)
      {
        String env = safeGetenv(envVars[i]);
        if (env != null && !env.trim().isEmpty())
        {
          return sanitizeKey(env);
        }
      }
    }
    return sanitizeKey(key);
  }

  public void setModelFarmApiKey(String key)
  {
    setStr("modelFarmApiKey", sanitizeKey(key));
  }

  public String getModelFarmSubscriptionId()
  {
    String subId = getStr("modelFarmSubscriptionId", "");
    if (subId.isEmpty())
    {
      String env = safeGetenv("GENAIPLATFORM_FARM_SUBSCRIPTION_ID");
      if (env != null && !env.trim().isEmpty()) return env.trim();
    }
    return subId;
  }

  public void setModelFarmSubscriptionId(String subId)
  {
    setStr("modelFarmSubscriptionId", subId);
  }

  public boolean isModelFarmUseProxy()
  {
    return Boolean.parseBoolean(getStr("modelFarmUseProxy", "false"));
  }

  public void setModelFarmUseProxy(boolean useProxy)
  {
    setStr("modelFarmUseProxy", Boolean.toString(useProxy));
  }

  public String getModelFarmProxyHost()
  {
    return getStr("modelFarmProxyHost", "127.0.0.1");
  }

  public void setModelFarmProxyHost(String host)
  {
    setStr("modelFarmProxyHost", host);
  }

  public int getModelFarmProxyPort()
  {
    try
    {
      return Integer.parseInt(getStr("modelFarmProxyPort", "3128"));
    }
    catch (NumberFormatException e)
    {
      return 3128;
    }
  }

  public void setModelFarmProxyPort(int port)
  {
    setStr("modelFarmProxyPort", Integer.toString(port));
  }

  // ---- Google Gemini API & Google Account OAuth 2.0 ----

  public String getGeminiApiKey()
  {
    String key = getStr("geminiApiKey", "");
    if (key.isEmpty())
    {
      String env = safeGetenv("GEMINI_API_KEY");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
      env = safeGetenv("GOOGLE_API_KEY");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(key);
  }

  public void setGeminiApiKey(String key)
  {
    setStr("geminiApiKey", sanitizeKey(key));
  }

  public String getGeminiArgonModelId()
  {
    return getStr("geminiArgonModelId", "gemini-2.5-pro");
  }

  public void setGeminiArgonModelId(String modelId)
  {
    setStr("geminiArgonModelId", modelId);
  }

  public String getGoogleOAuthClientId()
  {
    return getStr("googleOAuthClientId", "");
  }

  public void setGoogleOAuthClientId(String clientId)
  {
    setStr("googleOAuthClientId", clientId);
  }

  public String getGoogleOAuthClientSecret()
  {
    return getStr("googleOAuthClientSecret", "");
  }

  public void setGoogleOAuthClientSecret(String secret)
  {
    setStr("googleOAuthClientSecret", secret);
  }

  public String getGoogleOAuthProjectId()
  {
    return getStr("googleOAuthProjectId", "");
  }

  public void setGoogleOAuthProjectId(String projectId)
  {
    setStr("googleOAuthProjectId", projectId);
  }

  public String getGoogleAccessToken()
  {
    return getStr("googleAccessToken", "");
  }

  public void setGoogleAccessToken(String token)
  {
    setStr("googleAccessToken", token);
  }

  public String getGoogleRefreshToken()
  {
    return getStr("googleRefreshToken", "");
  }

  public void setGoogleRefreshToken(String token)
  {
    setStr("googleRefreshToken", token);
  }

  public long getGoogleTokenExpiryMs()
  {
    try
    {
      return Long.parseLong(getStr("googleTokenExpiryMs", "0"));
    }
    catch (NumberFormatException e)
    {
      return 0L;
    }
  }

  public void setGoogleTokenExpiryMs(long expiryMs)
  {
    setStr("googleTokenExpiryMs", Long.toString(expiryMs));
  }

  public String getGoogleUserEmail()
  {
    return getStr("googleUserEmail", "");
  }

  public void setGoogleUserEmail(String email)
  {
    setStr("googleUserEmail", email);
  }

  public boolean isGoogleSignedIn()
  {
    return !getGoogleAccessToken().isEmpty() || !getGoogleRefreshToken().isEmpty();
  }

  public void clearGoogleAuth()
  {
    setGoogleAccessToken("");
    setGoogleRefreshToken("");
    setGoogleTokenExpiryMs(0L);
    setGoogleUserEmail("");
    save();
  }

  // ---- Company Copilot Account (Microsoft 365 Work SSO / Entra ID & GitHub Copilot) ----

  public String getCopilotEndpointUrl()
  {
    return getStr("copilotEndpointUrl", "https://aoai-farm.bosch-temp.com");
  }

  public void setCopilotEndpointUrl(String url)
  {
    setStr("copilotEndpointUrl", url);
  }

  public String getCopilotModelId()
  {
    return getStr("copilotModelId", "gpt-5-nano-2025-08-07");
  }

  public void setCopilotModelId(String modelId)
  {
    setStr("copilotModelId", modelId);
  }

  public String getMicrosoftTenantId()
  {
    return getStr("microsoftTenantId", "organizations");
  }

  public void setMicrosoftTenantId(String tenantId)
  {
    setStr("microsoftTenantId", tenantId);
  }

  public String getMicrosoftClientId()
  {
    return getStr("microsoftClientId", "04b07795-8ddb-461a-bbee-02f9e1bf7b46");
  }

  public void setMicrosoftClientId(String clientId)
  {
    setStr("microsoftClientId", clientId);
  }

  public String getMicrosoftOAuthScope()
  {
    return getStr("microsoftOAuthScope", "openid profile email offline_access https://cognitiveservices.azure.com/.default");
  }

  public void setMicrosoftOAuthScope(String scope)
  {
    setStr("microsoftOAuthScope", scope);
  }

  public String getCopilotAccessToken()
  {
    String token = getStr("copilotAccessToken", "");
    if (token.isEmpty())
    {
      String env = safeGetenv("GITHUB_TOKEN");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(token);
  }

  public void setCopilotAccessToken(String token)
  {
    setStr("copilotAccessToken", sanitizeKey(token));
  }

  public String getCopilotRefreshToken()
  {
    return getStr("copilotRefreshToken", "");
  }

  public void setCopilotRefreshToken(String token)
  {
    setStr("copilotRefreshToken", token);
  }

  public long getCopilotTokenExpiryMs()
  {
    try
    {
      return Long.parseLong(getStr("copilotTokenExpiryMs", "0"));
    }
    catch (NumberFormatException e)
    {
      return 0L;
    }
  }

  public void setCopilotTokenExpiryMs(long expiryMs)
  {
    setStr("copilotTokenExpiryMs", Long.toString(expiryMs));
  }

  public String getCopilotUserDisplay()
  {
    return getStr("copilotUserDisplay", "");
  }

  public void setCopilotUserDisplay(String display)
  {
    setStr("copilotUserDisplay", display);
  }

  public boolean isCopilotSignedIn()
  {
    return !getCopilotAccessToken().isEmpty() || !getCopilotRefreshToken().isEmpty();
  }

  public void clearCopilotAuth()
  {
    setCopilotAccessToken("");
    setCopilotRefreshToken("");
    setCopilotTokenExpiryMs(0L);
    setCopilotUserDisplay("");
    save();
  }

  // ---- Other Providers (OpenAI, Anthropic, Local Ollama) ----

  public String getOpenAiApiKey()
  {
    String key = getStr("openAiApiKey", "");
    if (key.isEmpty())
    {
      String env = safeGetenv("OPENAI_API_KEY");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(key);
  }

  public void setOpenAiApiKey(String key)
  {
    setStr("openAiApiKey", sanitizeKey(key));
  }

  public String getAnthropicApiKey()
  {
    String key = getStr("anthropicApiKey", "");
    if (key.isEmpty())
    {
      String env = safeGetenv("ANTHROPIC_API_KEY");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(key);
  }

  public void setAnthropicApiKey(String key)
  {
    setStr("anthropicApiKey", sanitizeKey(key));
  }

  public String getOllamaUrl()
  {
    return getStr("ollamaUrl", "http://127.0.0.1:11434");
  }

  public void setOllamaUrl(String url)
  {
    setStr("ollamaUrl", url);
  }

  public String getOllamaModel()
  {
    return getStr("ollamaModel", "llama3");
  }

  public void setOllamaModel(String model)
  {
    setStr("ollamaModel", model);
  }

  public static String sanitizeKey(String rawKey)
  {
    if (rawKey == null) return "";
    String key = rawKey.trim();
    if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'")))
    {
      if (key.length() >= 2)
      {
        key = key.substring(1, key.length() - 1).trim();
      }
    }
    if (key.toLowerCase().startsWith("bearer "))
    {
      key = key.substring(7).trim();
    }
    return key;
  }

  public String getAuthSummaryBadge()
  {
    String model = getSelectedModel();
    if (model.contains("Model Farm") || model.contains("gpt-5-nano") || model.contains("askbosch"))
    {
      if (!getModelFarmApiKey().isEmpty())
      {
        return "Model Farm: Key Ready";
      }
      if (isCopilotSignedIn())
      {
        return "Model Farm / SSO: " + getCopilotUserDisplay();
      }
      return "Model Farm: Needs Key";
    }
    if (model.contains("Copilot"))
    {
      if (isCopilotSignedIn())
      {
        String user = getCopilotUserDisplay();
        return "M365 SSO: " + (user.isEmpty() ? "Signed In" : user);
      }
      if (!getModelFarmApiKey().isEmpty())
      {
        return "Copilot / Farm: Key Ready";
      }
      return "M365 Copilot: Not Signed In";
    }
    if (model.contains("Gemini"))
    {
      if (isGoogleSignedIn())
      {
        String email = getGoogleUserEmail();
        return "Google: " + (email.isEmpty() ? "Signed In" : email);
      }
      if (!getGeminiApiKey().isEmpty())
      {
        return "Gemini: API Key Ready";
      }
      if (!getModelFarmApiKey().isEmpty())
      {
        return "Gemini (via Model Farm)";
      }
      return "Google: Not Signed In";
    }
    if (model.contains("Claude"))
    {
      if (!getAnthropicApiKey().isEmpty()) return "Claude: Key Ready";
      if (!getModelFarmApiKey().isEmpty()) return "Claude (via Model Farm)";
      return "Claude: No Key";
    }
    if (model.contains("GPT"))
    {
      if (!getOpenAiApiKey().isEmpty()) return "OpenAI: Key Ready";
      if (!getModelFarmApiKey().isEmpty()) return "OpenAI (via Model Farm)";
      return "OpenAI: No Key";
    }
    return "Local Ollama";
  }
}
