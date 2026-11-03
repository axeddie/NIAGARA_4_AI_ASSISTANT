/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles browser-based OAuth 2.0 + PKCE authentication for:
 *  1. Microsoft 365 / Entra ID Work Accounts (Company Copilot SSO + MFA in system browser)
 *  2. Google Accounts (Google OAuth 2.0 loopback flow + local Google CLI credential import)
 *  3. GitHub Copilot / Local IDE token discovery
 *
 * Users never type corporate or Google passwords into Niagara Workbench; authentication
 * happens in the system browser where corporate SSO, Windows Hello, and MFA are active.
 */
public final class EddiesAiAuthManager
{
  public interface AuthCallback
  {
    void onStatus(String statusMessage);
    void onSuccess(String summaryMessage);
    void onError(String errorMessage);
  }

  private EddiesAiAuthManager()
  {
  }

  // ============================================================================
  // 1. MICROSOFT 365 WORK ACCOUNT (COMPANY COPILOT / ENTRA ID SSO)
  // ============================================================================

  /**
   * Starts an interactive browser OAuth 2.0 + PKCE sign-in flow against Microsoft Entra ID
   * (login.microsoftonline.com). Because it launches the system browser (Edge/Chrome),
   * existing corporate Microsoft 365 SSO, Windows sign-in, Conditional Access, and MFA
   * work automatically without the user typing a password into Workbench.
   */
  public static void signInWithMicrosoft365Async(final AuthCallback callback)
  {
    Thread worker = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        ServerSocket server = null;
        try
        {
          EddiesAiConfig cfg = EddiesAiConfig.get();
          String tenant = cfg.getMicrosoftTenantId();
          if (tenant == null || tenant.trim().isEmpty())
          {
            tenant = "organizations";
          }
          String clientId = cfg.getMicrosoftClientId();
          if (clientId == null || clientId.trim().isEmpty())
          {
            clientId = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";
          }
          String scope = cfg.getMicrosoftOAuthScope();
          if (scope == null || scope.trim().isEmpty())
          {
            scope = "openid profile email offline_access https://cognitiveservices.azure.com/.default";
          }

          String codeVerifier = generatePkceVerifier();
          String codeChallenge = generatePkceChallenge(codeVerifier);
          String state = generateRandomState();

          server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
          server.setSoTimeout(180000); // 3 minute timeout for SSO + MFA approval
          int port = server.getLocalPort();
          // Microsoft Entra public desktop clients register http://localhost:<port> for loopback
          String redirectUri = "http://localhost:" + port;

          String authUrl = "https://login.microsoftonline.com/" + URLEncoder.encode(tenant, "UTF-8")
            + "/oauth2/v2.0/authorize"
            + "?client_id=" + URLEncoder.encode(clientId, "UTF-8")
            + "&response_type=code"
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
            + "&response_mode=query"
            + "&scope=" + URLEncoder.encode(scope, "UTF-8")
            + "&state=" + URLEncoder.encode(state, "UTF-8")
            + "&code_challenge=" + URLEncoder.encode(codeChallenge, "UTF-8")
            + "&code_challenge_method=S256"
            + "&prompt=select_account";

          callback.onStatus("Opening browser for Microsoft 365 Company SSO sign-in...");
          openBrowser(authUrl);

          Map<String, String> params = waitForLoopbackCallback(
            server,
            "Microsoft 365 Copilot SSO Sign-In Complete",
            "Your company Microsoft 365 account is now linked to Eddie's Workbench AI. You may close this tab and return to Niagara Workbench.");

          if (params.containsKey("error"))
          {
            String errDesc = params.get("error_description");
            callback.onError("Microsoft SSO Error: " + params.get("error")
              + (errDesc != null ? " (" + errDesc + ")" : ""));
            return;
          }

          String code = params.get("code");
          if (code == null || code.isEmpty())
          {
            callback.onError("No authorization code received from Microsoft SSO.");
            return;
          }

          callback.onStatus("Exchanging Microsoft 365 SSO code for token...");
          String tokenUrl = "https://login.microsoftonline.com/" + URLEncoder.encode(tenant, "UTF-8")
            + "/oauth2/v2.0/token";

          String formBody = "client_id=" + URLEncoder.encode(clientId, "UTF-8")
            + "&grant_type=authorization_code"
            + "&code=" + URLEncoder.encode(code, "UTF-8")
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
            + "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")
            + "&scope=" + URLEncoder.encode(scope, "UTF-8");

          String tokenJson = httpPostForm(tokenUrl, formBody);
          String accessToken = extractJsonString(tokenJson, "access_token");
          String refreshToken = extractJsonString(tokenJson, "refresh_token");
          int expiresIn = extractJsonInt(tokenJson, "expires_in", 3600);
          String idToken = extractJsonString(tokenJson, "id_token");

          if (accessToken == null || accessToken.isEmpty())
          {
            callback.onError("Failed to obtain Microsoft 365 access token: " + abbreviate(tokenJson, 160));
            return;
          }

          String userEmail = extractEmailFromJwt(idToken);
          if (userEmail == null || userEmail.isEmpty())
          {
            userEmail = extractEmailFromJwt(accessToken);
          }
          if (userEmail == null || userEmail.isEmpty())
          {
            userEmail = "Microsoft 365 SSO User";
          }

          cfg.setCopilotAccessToken(accessToken);
          if (refreshToken != null && !refreshToken.isEmpty())
          {
            cfg.setCopilotRefreshToken(refreshToken);
          }
          cfg.setCopilotTokenExpiryMs(System.currentTimeMillis() + (expiresIn * 1000L));
          cfg.setCopilotUserDisplay(userEmail);
          cfg.save();

          callback.onSuccess("Signed in with Microsoft 365 (" + userEmail + ")");
        }
        catch (Exception e)
        {
          callback.onError("Microsoft 365 SSO sign-in failed: " + e.getMessage());
        }
        finally
        {
          if (server != null)
          {
            try { server.close(); } catch (Exception ignore) {}
          }
        }
      }
    }, "EddiesAi-M365-OAuth");
    worker.setDaemon(true);
    worker.start();
  }

  /**
   * Refreshes the Microsoft 365 / Entra ID OAuth access token if expired and a refresh token is stored.
   */
  public static String getValidCopilotAccessToken() throws Exception
  {
    EddiesAiConfig cfg = EddiesAiConfig.get();
    String access = cfg.getCopilotAccessToken();
    String refresh = cfg.getCopilotRefreshToken();
    long expiry = cfg.getCopilotTokenExpiryMs();

    if (!access.isEmpty() && (expiry == 0L || System.currentTimeMillis() < expiry - 60000L))
    {
      return access;
    }

    if (!refresh.isEmpty())
    {
      String tenant = cfg.getMicrosoftTenantId();
      if (tenant == null || tenant.isEmpty()) tenant = "organizations";
      String clientId = cfg.getMicrosoftClientId();
      String scope = cfg.getMicrosoftOAuthScope();

      String tokenUrl = "https://login.microsoftonline.com/" + URLEncoder.encode(tenant, "UTF-8")
        + "/oauth2/v2.0/token";
      String formBody = "client_id=" + URLEncoder.encode(clientId, "UTF-8")
        + "&grant_type=refresh_token"
        + "&refresh_token=" + URLEncoder.encode(refresh, "UTF-8")
        + "&scope=" + URLEncoder.encode(scope, "UTF-8");

      String tokenJson = httpPostForm(tokenUrl, formBody);
      String newAccess = extractJsonString(tokenJson, "access_token");
      String newRefresh = extractJsonString(tokenJson, "refresh_token");
      int expiresIn = extractJsonInt(tokenJson, "expires_in", 3600);

      if (newAccess != null && !newAccess.isEmpty())
      {
        cfg.setCopilotAccessToken(newAccess);
        if (newRefresh != null && !newRefresh.isEmpty())
        {
          cfg.setCopilotRefreshToken(newRefresh);
        }
        cfg.setCopilotTokenExpiryMs(System.currentTimeMillis() + (expiresIn * 1000L));
        cfg.save();
        return newAccess;
      }
    }
    return access;
  }

  /**
   * Imports an existing local Copilot token if the user is already signed into Copilot
   * in VS Code / JetBrains / GitHub CLI on this Windows machine.
   */
  public static boolean importLocalCopilotToken(AuthCallback callback)
  {
    try
    {
      String localAppData = EddiesAiConfig.safeGetenv("LOCALAPPDATA");
      String userHome = EddiesAiConfig.safeGetProperty("user.home", ".");
      File[] candidates = new File[] {
        localAppData != null ? new File(localAppData, "github-copilot/hosts.json") : null,
        localAppData != null ? new File(localAppData, "github-copilot/apps.json") : null,
        new File(userHome, ".config/github-copilot/hosts.json"),
        new File(userHome, ".config/github-copilot/apps.json")
      };

      for (int i = 0; i < candidates.length; i++)
      {
        File f = candidates[i];
        if (f != null && f.exists() && f.isFile())
        {
          String json = readFileText(f);
          String oauthToken = extractJsonString(json, "oauth_token");
          String user = extractJsonString(json, "user");
          if (oauthToken != null && !oauthToken.isEmpty())
          {
            EddiesAiConfig cfg = EddiesAiConfig.get();
            cfg.setCopilotAccessToken(oauthToken);
            cfg.setCopilotTokenExpiryMs(0L);
            cfg.setCopilotUserDisplay(user != null && !user.isEmpty() ? user : "Local Copilot User");
            cfg.save();
            if (callback != null)
            {
              callback.onSuccess("Imported local Copilot login (" + cfg.getCopilotUserDisplay() + ")");
            }
            return true;
          }
        }
      }
      if (callback != null)
      {
        callback.onError("No local GitHub Copilot token found in %LOCALAPPDATA%\\github-copilot. Use 'Sign in with Microsoft 365 (Work SSO)' instead.");
      }
      return false;
    }
    catch (Exception e)
    {
      if (callback != null)
      {
        callback.onError("Error importing local Copilot credentials: " + e.getMessage());
      }
      return false;
    }
  }

  // ============================================================================
  // 2. GOOGLE ACCOUNT (BROWSER OAUTH 2.0 + LOCAL CLI IMPORT)
  // ============================================================================

  /**
   * Starts an interactive browser OAuth 2.0 + PKCE sign-in flow for a Google account,
   * or imports local Google credentials if no custom OAuth Client ID is configured yet.
   */
  public static void signInWithGoogleAsync(final AuthCallback callback)
  {
    Thread worker = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        EddiesAiConfig cfg = EddiesAiConfig.get();
        String clientId = cfg.getGoogleOAuthClientId();
        String clientSecret = cfg.getGoogleOAuthClientSecret();

        // First check if user hasn't entered a custom Google Cloud OAuth Client ID;
        // try importing existing local Google credentials (gemini-cli / gcloud ADC) automatically!
        if (clientId == null || clientId.trim().isEmpty())
        {
          if (importLocalGoogleCredentials(null))
          {
            callback.onSuccess("Signed in using local Google Account credentials (" + cfg.getGoogleUserEmail() + ")");
            return;
          }
          callback.onError("To use Browser OAuth with Google directly, enter a Google OAuth Desktop Client ID in Settings, OR paste a Gemini API Key (from aistudio.google.com), OR sign in via local Gemini/gcloud CLI first.");
          return;
        }

        ServerSocket server = null;
        try
        {
          String codeVerifier = generatePkceVerifier();
          String codeChallenge = generatePkceChallenge(codeVerifier);
          String state = generateRandomState();

          server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
          server.setSoTimeout(180000);
          int port = server.getLocalPort();
          String redirectUri = "http://127.0.0.1:" + port + "/callback";

          String scope = "openid email https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/generative-language.retriever";
          String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
            + "?client_id=" + URLEncoder.encode(clientId, "UTF-8")
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
            + "&response_type=code"
            + "&scope=" + URLEncoder.encode(scope, "UTF-8")
            + "&access_type=offline"
            + "&prompt=consent"
            + "&state=" + URLEncoder.encode(state, "UTF-8")
            + "&code_challenge=" + URLEncoder.encode(codeChallenge, "UTF-8")
            + "&code_challenge_method=S256";

          callback.onStatus("Opening browser for Google Account sign-in...");
          openBrowser(authUrl);

          Map<String, String> params = waitForLoopbackCallback(
            server,
            "Google Account Sign-In Complete",
            "Your Google account is now linked to Eddie's Workbench AI. You may close this tab and return to Niagara Workbench.");

          if (params.containsKey("error"))
          {
            callback.onError("Google OAuth Error: " + params.get("error"));
            return;
          }

          String code = params.get("code");
          if (code == null || code.isEmpty())
          {
            callback.onError("No authorization code received from Google.");
            return;
          }

          callback.onStatus("Exchanging Google authorization code for token...");
          StringBuilder form = new StringBuilder();
          form.append("client_id=").append(URLEncoder.encode(clientId, "UTF-8"));
          if (clientSecret != null && !clientSecret.isEmpty())
          {
            form.append("&client_secret=").append(URLEncoder.encode(clientSecret, "UTF-8"));
          }
          form.append("&code=").append(URLEncoder.encode(code, "UTF-8"));
          form.append("&code_verifier=").append(URLEncoder.encode(codeVerifier, "UTF-8"));
          form.append("&grant_type=authorization_code");
          form.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"));

          String tokenJson = httpPostForm("https://oauth2.googleapis.com/token", form.toString());
          String accessToken = extractJsonString(tokenJson, "access_token");
          String refreshToken = extractJsonString(tokenJson, "refresh_token");
          String idToken = extractJsonString(tokenJson, "id_token");
          int expiresIn = extractJsonInt(tokenJson, "expires_in", 3600);

          if (accessToken == null || accessToken.isEmpty())
          {
            callback.onError("Failed to get Google access token: " + abbreviate(tokenJson, 160));
            return;
          }

          String email = extractEmailFromJwt(idToken);
          if (email == null || email.isEmpty())
          {
            email = "Google Account User";
          }

          cfg.setGoogleAccessToken(accessToken);
          if (refreshToken != null && !refreshToken.isEmpty())
          {
            cfg.setGoogleRefreshToken(refreshToken);
          }
          cfg.setGoogleTokenExpiryMs(System.currentTimeMillis() + (expiresIn * 1000L));
          cfg.setGoogleUserEmail(email);
          cfg.save();

          callback.onSuccess("Signed in with Google (" + email + ")");
        }
        catch (Exception e)
        {
          callback.onError("Google sign-in failed: " + e.getMessage());
        }
        finally
        {
          if (server != null)
          {
            try { server.close(); } catch (Exception ignore) {}
          }
        }
      }
    }, "EddiesAi-Google-OAuth");
    worker.setDaemon(true);
    worker.start();
  }

  /**
   * Imports existing local Google OAuth credentials from ~/.gemini/oauth_creds.json
   * or Google Cloud Application Default Credentials (application_default_credentials.json)
   * if the user has already signed in with their Google Ultra / Google account locally.
   */
  public static boolean importLocalGoogleCredentials(AuthCallback callback)
  {
    try
    {
      String userHome = EddiesAiConfig.safeGetProperty("user.home", ".");
      String appData = EddiesAiConfig.safeGetenv("APPDATA");
      File[] candidates = new File[] {
        new File(userHome, ".gemini/oauth_creds.json"),
        appData != null ? new File(appData, "gcloud/application_default_credentials.json") : null,
        new File(userHome, ".config/gcloud/application_default_credentials.json")
      };

      for (int i = 0; i < candidates.length; i++)
      {
        File f = candidates[i];
        if (f != null && f.exists() && f.isFile())
        {
          String json = readFileText(f);
          String access = extractJsonString(json, "access_token");
          String refresh = extractJsonString(json, "refresh_token");
          String clientId = extractJsonString(json, "client_id");
          String clientSecret = extractJsonString(json, "client_secret");
          String idToken = extractJsonString(json, "id_token");

          if ((access != null && !access.isEmpty()) || (refresh != null && !refresh.isEmpty()))
          {
            EddiesAiConfig cfg = EddiesAiConfig.get();
            if (access != null && !access.isEmpty()) cfg.setGoogleAccessToken(access);
            if (refresh != null && !refresh.isEmpty()) cfg.setGoogleRefreshToken(refresh);
            if (clientId != null && !clientId.isEmpty()) cfg.setGoogleOAuthClientId(clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) cfg.setGoogleOAuthClientSecret(clientSecret);

            String email = extractEmailFromJwt(idToken);
            if (email == null || email.isEmpty())
            {
              email = "Local Google Account (" + f.getParentFile().getName() + ")";
            }
            cfg.setGoogleUserEmail(email);
            cfg.save();

            if (callback != null)
            {
              callback.onSuccess("Imported local Google login (" + email + ")");
            }
            return true;
          }
        }
      }
      if (callback != null)
      {
        callback.onError("No local ~/.gemini/oauth_creds.json or gcloud credentials found.");
      }
      return false;
    }
    catch (Exception e)
    {
      if (callback != null)
      {
        callback.onError("Failed to import local Google credentials: " + e.getMessage());
      }
      return false;
    }
  }

  public static String getValidGoogleAccessToken() throws Exception
  {
    EddiesAiConfig cfg = EddiesAiConfig.get();
    String access = cfg.getGoogleAccessToken();
    String refresh = cfg.getGoogleRefreshToken();
    long expiry = cfg.getGoogleTokenExpiryMs();

    if (!access.isEmpty() && (expiry == 0L || System.currentTimeMillis() < expiry - 60000L))
    {
      return access;
    }

    String clientId = cfg.getGoogleOAuthClientId();
    String clientSecret = cfg.getGoogleOAuthClientSecret();
    if (!refresh.isEmpty() && !clientId.isEmpty())
    {
      StringBuilder form = new StringBuilder();
      form.append("client_id=").append(URLEncoder.encode(clientId, "UTF-8"));
      if (!clientSecret.isEmpty())
      {
        form.append("&client_secret=").append(URLEncoder.encode(clientSecret, "UTF-8"));
      }
      form.append("&refresh_token=").append(URLEncoder.encode(refresh, "UTF-8"));
      form.append("&grant_type=refresh_token");

      String tokenJson = httpPostForm("https://oauth2.googleapis.com/token", form.toString());
      String newAccess = extractJsonString(tokenJson, "access_token");
      int expiresIn = extractJsonInt(tokenJson, "expires_in", 3600);
      if (newAccess != null && !newAccess.isEmpty())
      {
        cfg.setGoogleAccessToken(newAccess);
        cfg.setGoogleTokenExpiryMs(System.currentTimeMillis() + (expiresIn * 1000L));
        cfg.save();
        return newAccess;
      }
    }
    return access;
  }

  // ============================================================================
  // INTERNAL OAUTH & HTTP HELPERS
  // ============================================================================

  private static Map<String, String> waitForLoopbackCallback(
    ServerSocket server,
    String htmlTitle,
    String htmlBody) throws IOException
  {
    Socket client = server.accept();
    try
    {
      BufferedReader reader = new BufferedReader(
        new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
      String requestLine = reader.readLine();
      Map<String, String> queryParams = new HashMap<String, String>();

      if (requestLine != null)
      {
        String[] parts = requestLine.split(" ");
        if (parts.length >= 2)
        {
          String pathAndQuery = parts[1];
          int qIdx = pathAndQuery.indexOf('?');
          if (qIdx >= 0 && qIdx + 1 < pathAndQuery.length())
          {
            String query = pathAndQuery.substring(qIdx + 1);
            String[] pairs = query.split("&");
            for (int i = 0; i < pairs.length; i++)
            {
              int eq = pairs[i].indexOf('=');
              if (eq > 0)
              {
                String k = URLDecoder.decode(pairs[i].substring(0, eq), "UTF-8");
                String v = URLDecoder.decode(pairs[i].substring(eq + 1), "UTF-8");
                queryParams.put(k, v);
              }
            }
          }
        }
      }

      String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><title>" + htmlTitle + "</title>"
        + "<style>body{font-family:Segoe UI,Tahoma,sans-serif;background:#f4f7fb;color:#1e293b;padding:48px;text-align:center;}"
        + ".card{max-width:480px;margin:0 auto;background:#fff;padding:28px;border-radius:10px;box-shadow:0 4px 14px rgba(0,0,0,0.08);}"
        + "h2{color:#1d4ed8;margin-top:0;}</style></head><body><div class='card'><h2>" + htmlTitle + "</h2>"
        + "<p>" + htmlBody + "</p></div></body></html>";
      byte[] bodyBytes = html.getBytes(StandardCharsets.UTF_8);

      OutputStream out = client.getOutputStream();
      String headers = "HTTP/1.1 200 OK\r\n"
        + "Content-Type: text/html; charset=UTF-8\r\n"
        + "Content-Length: " + bodyBytes.length + "\r\n"
        + "Connection: close\r\n\r\n";
      out.write(headers.getBytes(StandardCharsets.UTF_8));
      out.write(bodyBytes);
      out.flush();

      return queryParams;
    }
    finally
    {
      try { client.close(); } catch (Exception ignore) {}
    }
  }

  private static void openBrowser(String url) throws Exception
  {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
    {
      Desktop.getDesktop().browse(new URI(url));
    }
    else
    {
      Runtime.getRuntime().exec(new String[] { "rundll32", "url.dll,FileProtocolHandler", url });
    }
  }

  private static String generatePkceVerifier()
  {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String generatePkceChallenge(String verifier) throws Exception
  {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private static String generateRandomState()
  {
    byte[] bytes = new byte[16];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String httpPostForm(String urlStr, String formBody) throws IOException
  {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(15000);
    conn.setReadTimeout(30000);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    conn.setRequestProperty("Accept", "application/json");

    byte[] payload = formBody.getBytes(StandardCharsets.UTF_8);
    OutputStream os = conn.getOutputStream();
    os.write(payload);
    os.flush();
    os.close();

    int status = conn.getResponseCode();
    InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
    return readStreamText(is);
  }

  private static String readFileText(File f) throws IOException
  {
    FileInputStream in = new FileInputStream(f);
    try
    {
      return readStreamText(in);
    }
    finally
    {
      try { in.close(); } catch (Exception ignore) {}
    }
  }

  static String readStreamText(InputStream is) throws IOException
  {
    if (is == null) return "";
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = is.read(buf)) >= 0)
    {
      bos.write(buf, 0, n);
    }
    return new String(bos.toByteArray(), StandardCharsets.UTF_8);
  }

  static String extractJsonString(String json, String key)
  {
    if (json == null || key == null) return null;
    String pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) return null;
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) return null;
    int firstQuote = json.indexOf('"', colon + 1);
    if (firstQuote < 0) return null;

    StringBuilder sb = new StringBuilder();
    boolean escaped = false;
    for (int i = firstQuote + 1; i < json.length(); i++)
    {
      char c = json.charAt(i);
      if (escaped)
      {
        if (c == 'n') sb.append('\n');
        else if (c == 'r') sb.append('\r');
        else if (c == 't') sb.append('\t');
        else sb.append(c);
        escaped = false;
      }
      else if (c == '\\')
      {
        escaped = true;
      }
      else if (c == '"')
      {
        return sb.toString();
      }
      else
      {
        sb.append(c);
      }
    }
    return null;
  }

  static int extractJsonInt(String json, String key, int defaultVal)
  {
    if (json == null || key == null) return defaultVal;
    String pattern = "\"" + key + "\"";
    int idx = json.indexOf(pattern);
    if (idx < 0) return defaultVal;
    int colon = json.indexOf(':', idx + pattern.length());
    if (colon < 0) return defaultVal;
    int i = colon + 1;
    while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == '"'))
    {
      i++;
    }
    int start = i;
    while (i < json.length() && Character.isDigit(json.charAt(i)))
    {
      i++;
    }
    if (i > start)
    {
      try
      {
        return Integer.parseInt(json.substring(start, i));
      }
      catch (NumberFormatException ignore)
      {
      }
    }
    return defaultVal;
  }

  private static String extractEmailFromJwt(String jwt)
  {
    if (jwt == null || jwt.isEmpty()) return null;
    try
    {
      String[] parts = jwt.split("\\.");
      if (parts.length < 2) return null;
      byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
      String payload = new String(decoded, StandardCharsets.UTF_8);
      String email = extractJsonString(payload, "preferred_username");
      if (email == null || email.isEmpty())
      {
        email = extractJsonString(payload, "upn");
      }
      if (email == null || email.isEmpty())
      {
        email = extractJsonString(payload, "email");
      }
      if (email == null || email.isEmpty())
      {
        email = extractJsonString(payload, "name");
      }
      return email;
    }
    catch (Exception e)
    {
      return null;
    }
  }

  private static String abbreviate(String s, int max)
  {
    if (s == null) return "";
    String clean = s.replace('\r', ' ').replace('\n', ' ').trim();
    if (clean.length() <= max) return clean;
    return clean.substring(0, max - 3) + "...";
  }
}
