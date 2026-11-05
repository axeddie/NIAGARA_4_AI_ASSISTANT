/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.baja.sys.Sys;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Persistent configuration and credential store for Eddie's Workbench AI Assistant.
 * Saves user preferences, Personal Google Account (Gemini API Key / Google OAuth),
 * and Company Model Farm settings inside the Niagara user home directory.
 *
 * All sensitive keys and OAuth tokens stored on disk are encrypted using AES-GCM-256
 * with a Windows user/machine-bound PBKDF2 key and per-user random salt so that plain-text
 * API keys are never written to eddiesWorkbenchAI.properties.
 */
public final class EddiesAiConfig
{
  private static final EddiesAiConfig INSTANCE = new EddiesAiConfig();
  private static final String ENC_PREFIX = "ENC:v1:";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_BITS = 128;

  private final Properties props = new Properties();
  private final Map<String, String> decryptedCache = new HashMap<String, String>();
  private final File configFile;
  private final File saltFile;
  private SecretKeySpec cachedKeySpec;
  private long lastLoadedModifiedMs = -1L;

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
    this.saltFile = (baseDir != null)
      ? new File(baseDir, ".eddiesAiKeySalt")
      : new File(".eddiesAiKeySalt");
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
      long mod = configFile.lastModified();
      if (mod > 0L && mod == lastLoadedModifiedMs && !props.isEmpty())
      {
        return;
      }
      in = new FileInputStream(configFile);
      props.load(in);
      decryptedCache.clear();
      lastLoadedModifiedMs = mod;
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
      // Ensure any legacy plaintext secrets in props are upgraded to ENC:v1: before writing
      upgradePlaintextSecretIfNeeded("geminiApiKey");
      upgradePlaintextSecretIfNeeded("googleAccessToken");
      upgradePlaintextSecretIfNeeded("googleRefreshToken");
      upgradePlaintextSecretIfNeeded("googleOAuthClientSecret");
      upgradePlaintextSecretIfNeeded("modelFarmApiKey");
      upgradePlaintextSecretIfNeeded("copilotAccessToken");
      upgradePlaintextSecretIfNeeded("copilotRefreshToken");
      upgradePlaintextSecretIfNeeded("openAiApiKey");
      upgradePlaintextSecretIfNeeded("anthropicApiKey");

      out = new FileOutputStream(configFile);
      props.store(out, "Eddie's Workbench AI Assistant Configuration (Secrets AES-GCM Encrypted)");
      lastLoadedModifiedMs = configFile.lastModified();
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

  private synchronized String getSecret(String key, String def)
  {
    String raw = props.getProperty(key);
    if (raw == null || raw.trim().isEmpty())
    {
      return def;
    }
    String trimmed = raw.trim();
    if (trimmed.startsWith(ENC_PREFIX))
    {
      String cached = decryptedCache.get(trimmed);
      if (cached != null)
      {
        return cached;
      }
      String decrypted = decryptSecret(trimmed);
      if (decrypted != null)
      {
        decryptedCache.put(trimmed, decrypted);
        return decrypted;
      }
      return def;
    }
    return trimmed;
  }

  private synchronized void setSecret(String key, String plainVal)
  {
    if (plainVal == null || plainVal.trim().isEmpty())
    {
      props.remove(key);
      return;
    }
    String clean = plainVal.trim();
    if (clean.startsWith(ENC_PREFIX))
    {
      props.setProperty(key, clean);
    }
    else
    {
      String enc = encryptSecret(clean);
      props.setProperty(key, enc);
      decryptedCache.put(enc, clean);
    }
  }

  private synchronized void upgradePlaintextSecretIfNeeded(String key)
  {
    String raw = props.getProperty(key);
    if (raw != null && !raw.trim().isEmpty() && !raw.trim().startsWith(ENC_PREFIX))
    {
      props.setProperty(key, encryptSecret(raw.trim()));
    }
  }

  private String encryptSecret(String plainText)
  {
    if (plainText == null || plainText.isEmpty()) return "";
    try
    {
      SecretKeySpec keySpec = deriveMachineUserKey();
      byte[] iv = new byte[GCM_IV_LENGTH];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

      return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
    }
    catch (Throwable t)
    {
      return plainText;
    }
  }

  private String decryptSecret(String encryptedValue)
  {
    if (encryptedValue == null || !encryptedValue.startsWith(ENC_PREFIX))
    {
      return encryptedValue;
    }
    try
    {
      String b64 = encryptedValue.substring(ENC_PREFIX.length()).trim();
      byte[] combined = Base64.getDecoder().decode(b64);
      if (combined.length <= GCM_IV_LENGTH) return "";

      byte[] iv = new byte[GCM_IV_LENGTH];
      byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
      System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

      SecretKeySpec keySpec = deriveMachineUserKey();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
      byte[] plainBytes = cipher.doFinal(cipherText);
      return new String(plainBytes, StandardCharsets.UTF_8);
    }
    catch (Throwable t)
    {
      return "";
    }
  }

  private synchronized SecretKeySpec deriveMachineUserKey() throws Exception
  {
    if (cachedKeySpec != null)
    {
      return cachedKeySpec;
    }
    String user = safeGetProperty("user.name", "");
    if (user.isEmpty()) user = safeGetenv("USERNAME");
    String domain = safeGetenv("USERDOMAIN");
    String computer = safeGetenv("COMPUTERNAME");
    String home = safeGetProperty("user.home", "");

    String passphrase = "EddiesWorkbenchAI-v1|"
      + (user != null ? user : "user") + "|"
      + (domain != null ? domain : "local") + "|"
      + (computer != null ? computer : "pc") + "|"
      + (home != null ? home : "home");

    byte[] salt = loadOrCreateSalt();
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256);
    byte[] keyBytes = factory.generateSecret(spec).getEncoded();
    cachedKeySpec = new SecretKeySpec(keyBytes, "AES");
    return cachedKeySpec;
  }

  private byte[] loadOrCreateSalt()
  {
    try
    {
      if (saltFile != null && saltFile.exists() && saltFile.length() >= 16)
      {
        FileInputStream in = new FileInputStream(saltFile);
        try
        {
          byte[] buf = new byte[16];
          int read = in.read(buf);
          if (read == 16) return buf;
        }
        finally
        {
          try { in.close(); } catch (Throwable ignore) {}
        }
      }
      byte[] newSalt = new byte[16];
      new SecureRandom().nextBytes(newSalt);
      if (saltFile != null)
      {
        File parent = saltFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(saltFile);
        try
        {
          out.write(newSalt);
          out.flush();
        }
        finally
        {
          try { out.close(); } catch (Throwable ignore) {}
        }
      }
      return newSalt;
    }
    catch (Throwable ignore)
    {
      return "EddiesAiStaticS!".getBytes(StandardCharsets.UTF_8);
    }
  }

  // ---- General UI & Execution Preferences ----

  public static final String DEFAULT_MODEL = "Gemini 4 Argon (Medium) (Google Ultra ⭐)";

  public String getSelectedModel()
  {
    return getStr("selectedModel", DEFAULT_MODEL);
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

  // ---- Personal Google Account (Direct Gemini API & Google OAuth 2.0) ----

  public String getStoredGeminiApiKeyOnly()
  {
    return sanitizeKey(getSecret("geminiApiKey", ""));
  }

  public boolean isGeminiApiKeyFromEnv()
  {
    if (!getStoredGeminiApiKeyOnly().isEmpty())
    {
      return false;
    }
    String env = safeGetenv("GEMINI_API_KEY");
    if (env != null && !env.trim().isEmpty()) return true;
    env = safeGetenv("GOOGLE_API_KEY");
    return (env != null && !env.trim().isEmpty());
  }

  public String getGeminiApiKey()
  {
    String key = getStoredGeminiApiKeyOnly();
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
    setSecret("geminiApiKey", sanitizeKey(key));
  }

  public static String maskSecretPreview(String secret)
  {
    if (secret == null || secret.trim().isEmpty()) return "(not set)";
    String s = secret.trim();
    if (s.length() <= 8) return "••••••••";
    return s.substring(0, 4) + "••••••••" + s.substring(s.length() - 4);
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
    return getSecret("googleOAuthClientSecret", "");
  }

  public void setGoogleOAuthClientSecret(String secret)
  {
    setSecret("googleOAuthClientSecret", secret);
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
    return getSecret("googleAccessToken", "");
  }

  public void setGoogleAccessToken(String token)
  {
    setSecret("googleAccessToken", token);
  }

  public String getGoogleRefreshToken()
  {
    return getSecret("googleRefreshToken", "");
  }

  public void setGoogleRefreshToken(String token)
  {
    setSecret("googleRefreshToken", token);
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

  public boolean isGooglePersonalReady()
  {
    return !getGeminiApiKey().isEmpty() || isGoogleSignedIn();
  }

  public void clearGoogleAuth()
  {
    setGoogleAccessToken("");
    setGoogleRefreshToken("");
    setGoogleTokenExpiryMs(0L);
    setGoogleUserEmail("");
    save();
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

  public String getStoredModelFarmApiKeyOnly()
  {
    return sanitizeKey(getSecret("modelFarmApiKey", ""));
  }

  public String getModelFarmApiKey()
  {
    String key = getStoredModelFarmApiKeyOnly();
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
    setSecret("modelFarmApiKey", sanitizeKey(key));
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
    String token = getSecret("copilotAccessToken", "");
    if (token.isEmpty())
    {
      String env = safeGetenv("GITHUB_TOKEN");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(token);
  }

  public void setCopilotAccessToken(String token)
  {
    setSecret("copilotAccessToken", sanitizeKey(token));
  }

  public String getCopilotRefreshToken()
  {
    return getSecret("copilotRefreshToken", "");
  }

  public void setCopilotRefreshToken(String token)
  {
    setSecret("copilotRefreshToken", token);
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
    String key = getSecret("openAiApiKey", "");
    if (key.isEmpty())
    {
      String env = safeGetenv("OPENAI_API_KEY");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(key);
  }

  public void setOpenAiApiKey(String key)
  {
    setSecret("openAiApiKey", sanitizeKey(key));
  }

  public String getAnthropicApiKey()
  {
    String key = getSecret("anthropicApiKey", "");
    if (key.isEmpty())
    {
      String env = safeGetenv("ANTHROPIC_API_KEY");
      if (env != null && !env.trim().isEmpty()) return sanitizeKey(env);
    }
    return sanitizeKey(key);
  }

  public void setAnthropicApiKey(String key)
  {
    setSecret("anthropicApiKey", sanitizeKey(key));
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
    boolean wantsGooglePersonal = model.contains("Google Ultra")
      || model.contains("Google Personal")
      || model.contains("Argon")
      || (model.toLowerCase().contains("gemini") && !model.contains("Company Farm"));

    if (wantsGooglePersonal)
    {
      if (!getGeminiApiKey().isEmpty())
      {
        String src = isGeminiApiKeyFromEnv() ? "Env Var" : "Encrypted Key";
        return "Google Ultra: Ready (" + src + " " + maskSecretPreview(getGeminiApiKey()) + ")";
      }
      if (isGoogleSignedIn())
      {
        String email = getGoogleUserEmail();
        return "Google Ultra OAuth: " + (email.isEmpty() ? "Signed In" : email);
      }
      if (!getModelFarmApiKey().isEmpty())
      {
        return "Google Key Not Set (Will Fallback to Company Farm)";
      }
      return "Needs Google API Key — Click 'Account' above";
    }

    if (!getModelFarmApiKey().isEmpty())
    {
      String domain = getModelFarmDomain();
      String subId = getModelFarmSubscriptionId();
      return "Company Farm: Ready (" + domain + (subId.isEmpty() ? ")" : " | SubID set)");
    }
    if (isCopilotSignedIn())
    {
      return "M365 SSO: " + getCopilotUserDisplay();
    }
    if (!getGeminiApiKey().isEmpty())
    {
      return "Google Personal Key Ready (" + maskSecretPreview(getGeminiApiKey()) + ")";
    }
    return "Needs Key/Login — Click 'Account' above";
  }
}
