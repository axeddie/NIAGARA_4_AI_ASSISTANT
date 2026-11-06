/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

/**
 * Self-contained Dark-Theme HTML5/CSS3/ES5-JavaScript UI template for
 * Eddie's Workbench AI Assistant right-side panel (rendered inside JavaFX WebView).
 */
final class EddiesAiWebUiTemplate
{
  private EddiesAiWebUiTemplate()
  {
  }

  static String buildHtml(String[] models, String selectedModel)
  {
    StringBuilder modelOptions = new StringBuilder();
    if (models != null)
    {
      for (int i = 0; i < models.length; i++)
      {
        String m = models[i];
        boolean sel = (selectedModel != null && (m.equalsIgnoreCase(selectedModel) || m.startsWith(selectedModel)));
        modelOptions.append("<option value=\"")
          .append(escapeHtmlAttr(m))
          .append("\"")
          .append(sel ? " selected=\"selected\"" : "")
          .append(">")
          .append(escapeHtml(m))
          .append("</option>\n");
      }
    }

    return "<!DOCTYPE html>\n" +
      "<html lang=\"en\">\n" +
      "<head>\n" +
      "<meta charset=\"UTF-8\">\n" +
      "<title>Eddie's AI Assistant</title>\n" +
      "<style type=\"text/css\">\n" +
      "  * {\n" +
      "    box-sizing: border-box;\n" +
      "  }\n" +
      "  html, body {\n" +
      "    margin: 0;\n" +
      "    padding: 0;\n" +
      "    width: 100%;\n" +
      "    height: 100%;\n" +
      "    overflow: hidden;\n" +
      "    background-color: #071122;\n" +
      "    color: #e6f0ff;\n" +
      "    font-family: 'Segoe UI', Tahoma, Arial, sans-serif;\n" +
      "    font-size: 12px;\n" +
      "    -webkit-font-smoothing: antialiased;\n" +
      "  }\n" +
      "  ::-webkit-scrollbar {\n" +
      "    width: 7px;\n" +
      "    height: 7px;\n" +
      "  }\n" +
      "  ::-webkit-scrollbar-track {\n" +
      "    background: #071122;\n" +
      "  }\n" +
      "  ::-webkit-scrollbar-thumb {\n" +
      "    background: #1d3b6b;\n" +
      "    border-radius: 4px;\n" +
      "  }\n" +
      "  ::-webkit-scrollbar-thumb:hover {\n" +
      "    background: #2f5b9e;\n" +
      "  }\n" +
      "  .app-shell {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-flex-direction: column;\n" +
      "    flex-direction: column;\n" +
      "    width: 100%;\n" +
      "    height: 100%;\n" +
      "    background: linear-gradient(180deg, #09172e 0%, #071122 100%);\n" +
      "  }\n" +
      "  /* 1. Header Bar */\n" +
      "  .top-header {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    -webkit-justify-content: space-between;\n" +
      "    justify-content: space-between;\n" +
      "    padding: 8px 10px;\n" +
      "    background: linear-gradient(90deg, #0b1d3a 0%, #102b54 100%);\n" +
      "    border-bottom: 1px solid rgba(92, 225, 230, 0.25);\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .brand-group {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    min-width: 0;\n" +
      "  }\n" +
      "  .brand-icon {\n" +
      "    width: 24px;\n" +
      "    height: 24px;\n" +
      "    margin-right: 8px;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .brand-title {\n" +
      "    font-size: 13px;\n" +
      "    font-weight: 700;\n" +
      "    color: #ffffff;\n" +
      "    letter-spacing: 0.2px;\n" +
      "    white-space: nowrap;\n" +
      "    overflow: hidden;\n" +
      "    text-overflow: ellipsis;\n" +
      "  }\n" +
      "  .brand-title span {\n" +
      "    color: #5ce1e6;\n" +
      "  }\n" +
      "  .header-actions {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "  }\n" +
      "  .hdr-btn {\n" +
      "    background: rgba(255, 255, 255, 0.06);\n" +
      "    color: #cde2ff;\n" +
      "    border: 1px solid rgba(114, 239, 221, 0.28);\n" +
      "    border-radius: 5px;\n" +
      "    padding: 4px 8px;\n" +
      "    margin-left: 5px;\n" +
      "    font-size: 11px;\n" +
      "    font-weight: 600;\n" +
      "    cursor: pointer;\n" +
      "    outline: none;\n" +
      "    transition: all 0.15s ease;\n" +
      "  }\n" +
      "  .hdr-btn:hover {\n" +
      "    background: rgba(92, 225, 230, 0.18);\n" +
      "    color: #ffffff;\n" +
      "    border-color: #5ce1e6;\n" +
      "  }\n" +
      "  .hdr-btn:active {\n" +
      "    transform: translateY(1px);\n" +
      "  }\n" +
      "  .hdr-btn-close {\n" +
      "    border-color: rgba(255, 120, 120, 0.3);\n" +
      "    color: #ffccd2;\n" +
      "  }\n" +
      "  .hdr-btn-close:hover {\n" +
      "    background: rgba(230, 57, 70, 0.32);\n" +
      "    border-color: #ff6b6b;\n" +
      "    color: #ffffff;\n" +
      "  }\n" +
      "  /* 2. Control & Context Deck */\n" +
      "  .control-deck {\n" +
      "    padding: 7px 10px;\n" +
      "    background: #0a1830;\n" +
      "    border-bottom: 1px solid #162e54;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .deck-row {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    margin-bottom: 5px;\n" +
      "  }\n" +
      "  .deck-row:last-child {\n" +
      "    margin-bottom: 0;\n" +
      "  }\n" +
      "  .deck-label {\n" +
      "    width: 48px;\n" +
      "    font-size: 11px;\n" +
      "    font-weight: 700;\n" +
      "    color: #8ab4f8;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .model-select {\n" +
      "    -webkit-flex: 1;\n" +
      "    flex: 1;\n" +
      "    min-width: 0;\n" +
      "    background: #0f2447;\n" +
      "    color: #f0f6ff;\n" +
      "    border: 1px solid #284f8a;\n" +
      "    border-radius: 5px;\n" +
      "    padding: 4px 6px;\n" +
      "    font-size: 11px;\n" +
      "    font-family: 'Segoe UI', Tahoma, sans-serif;\n" +
      "    outline: none;\n" +
      "  }\n" +
      "  .model-select:focus {\n" +
      "    border-color: #5ce1e6;\n" +
      "  }\n" +
      "  .auth-pill {\n" +
      "    -webkit-flex: 1;\n" +
      "    flex: 1;\n" +
      "    min-width: 0;\n" +
      "    display: inline-block;\n" +
      "    padding: 3px 8px;\n" +
      "    border-radius: 4px;\n" +
      "    font-size: 10.5px;\n" +
      "    font-weight: 600;\n" +
      "    white-space: nowrap;\n" +
      "    overflow: hidden;\n" +
      "    text-overflow: ellipsis;\n" +
      "    cursor: pointer;\n" +
      "  }\n" +
      "  .auth-ok {\n" +
      "    background: rgba(46, 196, 182, 0.14);\n" +
      "    color: #72efdd;\n" +
      "    border: 1px solid rgba(114, 239, 221, 0.35);\n" +
      "  }\n" +
      "  .auth-warn {\n" +
      "    background: rgba(255, 159, 28, 0.16);\n" +
      "    color: #ffbf69;\n" +
      "    border: 1px solid rgba(255, 159, 28, 0.45);\n" +
      "  }\n" +
      "  .target-box {\n" +
      "    -webkit-flex: 1;\n" +
      "    flex: 1;\n" +
      "    min-width: 0;\n" +
      "    background: #0d1f3d;\n" +
      "    border: 1px solid #1f3f70;\n" +
      "    border-radius: 4px;\n" +
      "    padding: 3px 7px;\n" +
      "    color: #90e0ef;\n" +
      "    font-size: 11px;\n" +
      "    font-family: Consolas, 'Courier New', monospace;\n" +
      "    white-space: nowrap;\n" +
      "    overflow: hidden;\n" +
      "    text-overflow: ellipsis;\n" +
      "    margin-right: 6px;\n" +
      "  }\n" +
      "  .sync-btn {\n" +
      "    background: #132d59;\n" +
      "    color: #90e0ef;\n" +
      "    border: 1px solid #2b5c9e;\n" +
      "    border-radius: 4px;\n" +
      "    padding: 3px 8px;\n" +
      "    font-size: 10.5px;\n" +
      "    font-weight: 600;\n" +
      "    cursor: pointer;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .sync-btn:hover {\n" +
      "    background: #1c4280;\n" +
      "    color: #ffffff;\n" +
      "    border-color: #5ce1e6;\n" +
      "  }\n" +
      "  .checks-row {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    -webkit-justify-content: space-between;\n" +
      "    justify-content: space-between;\n" +
      "    padding-top: 2px;\n" +
      "  }\n" +
      "  .chk-label {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    font-size: 10.5px;\n" +
      "    color: #b0c9e8;\n" +
      "    cursor: pointer;\n" +
      "    -webkit-user-select: none;\n" +
      "    user-select: none;\n" +
      "  }\n" +
      "  .chk-label input {\n" +
      "    margin: 0 5px 0 0;\n" +
      "    cursor: pointer;\n" +
      "  }\n" +
      "  /* 3. Quick Action Chips (Single location for Arrange WS & prompt templates) */\n" +
      "  .quick-bar {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-flex-wrap: wrap;\n" +
      "    flex-wrap: wrap;\n" +
      "    padding: 5px 8px;\n" +
      "    background: #081529;\n" +
      "    border-bottom: 1px solid #162e54;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .chip-btn {\n" +
      "    -webkit-flex: 1 1 auto;\n" +
      "    flex: 1 1 auto;\n" +
      "    background: linear-gradient(180deg, #12294d 0%, #0d1f3b 100%);\n" +
      "    color: #d6e8ff;\n" +
      "    border: 1px solid #23497d;\n" +
      "    border-radius: 12px;\n" +
      "    padding: 4px 7px;\n" +
      "    margin: 2px;\n" +
      "    font-size: 10.5px;\n" +
      "    font-weight: 600;\n" +
      "    text-align: center;\n" +
      "    cursor: pointer;\n" +
      "    outline: none;\n" +
      "    transition: all 0.15s ease;\n" +
      "  }\n" +
      "  .chip-btn:hover {\n" +
      "    background: linear-gradient(180deg, #1a3c6e 0%, #132c52 100%);\n" +
      "    border-color: #5ce1e6;\n" +
      "    color: #ffffff;\n" +
      "  }\n" +
      "  .chip-btn-accent {\n" +
      "    border-color: rgba(92, 225, 230, 0.45);\n" +
      "    color: #72efdd;\n" +
      "  }\n" +
      "  /* 4. Chat Stream */\n" +
      "  .chat-stream {\n" +
      "    -webkit-flex: 1;\n" +
      "    flex: 1;\n" +
      "    overflow-y: auto;\n" +
      "    overflow-x: hidden;\n" +
      "    padding: 10px;\n" +
      "    background: #071122;\n" +
      "  }\n" +
      "  .msg-card {\n" +
      "    margin-bottom: 10px;\n" +
      "    border-radius: 8px;\n" +
      "    padding: 9px 11px;\n" +
      "    line-height: 1.45;\n" +
      "    word-wrap: break-word;\n" +
      "    overflow-wrap: break-word;\n" +
      "    box-shadow: 0 3px 10px rgba(0, 0, 0, 0.28);\n" +
      "  }\n" +
      "  .msg-welcome {\n" +
      "    background: linear-gradient(145deg, #0c1f3f 0%, #102b56 100%);\n" +
      "    border: 1px solid rgba(92, 225, 230, 0.35);\n" +
      "  }\n" +
      "  .welcome-title {\n" +
      "    font-size: 13px;\n" +
      "    font-weight: 700;\n" +
      "    color: #5ce1e6;\n" +
      "    margin: 0 0 6px 0;\n" +
      "  }\n" +
      "  .welcome-sub {\n" +
      "    font-size: 11.5px;\n" +
      "    color: #cfe3ff;\n" +
      "    margin: 0 0 8px 0;\n" +
      "  }\n" +
      "  .welcome-list {\n" +
      "    margin: 0;\n" +
      "    padding-left: 17px;\n" +
      "    color: #dbeaff;\n" +
      "    font-size: 11.5px;\n" +
      "  }\n" +
      "  .welcome-list li {\n" +
      "    margin-bottom: 5px;\n" +
      "  }\n" +
      "  .msg-user {\n" +
      "    background: linear-gradient(135deg, #0d3b66 0%, #0a2b4e 100%);\n" +
      "    border: 1px solid #2b6cb0;\n" +
      "    margin-left: 18px;\n" +
      "  }\n" +
      "  .msg-ai {\n" +
      "    background: #0d1d38;\n" +
      "    border: 1px solid #1e3a68;\n" +
      "    border-left: 3px solid #5ce1e6;\n" +
      "    margin-right: 8px;\n" +
      "  }\n" +
      "  .msg-system {\n" +
      "    background: rgba(16, 42, 82, 0.75);\n" +
      "    border: 1px solid rgba(114, 239, 221, 0.35);\n" +
      "    border-left: 3px solid #72efdd;\n" +
      "    font-size: 11.5px;\n" +
      "  }\n" +
      "  .msg-error {\n" +
      "    background: rgba(65, 18, 32, 0.85);\n" +
      "    border: 1px solid rgba(255, 90, 95, 0.55);\n" +
      "    border-left: 3px solid #ff5a5f;\n" +
      "    color: #ffd6d8;\n" +
      "  }\n" +
      "  .msg-meta {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-justify-content: space-between;\n" +
      "    justify-content: space-between;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    font-size: 10px;\n" +
      "    color: #88a8d4;\n" +
      "    margin-bottom: 5px;\n" +
      "    padding-bottom: 4px;\n" +
      "    border-bottom: 1px solid rgba(255, 255, 255, 0.07);\n" +
      "  }\n" +
      "  .msg-author {\n" +
      "    font-weight: 700;\n" +
      "    color: #72efdd;\n" +
      "  }\n" +
      "  .msg-user .msg-author {\n" +
      "    color: #90e0ef;\n" +
      "  }\n" +
      "  .msg-target-tag {\n" +
      "    display: inline-block;\n" +
      "    background: rgba(0, 0, 0, 0.28);\n" +
      "    color: #90e0ef;\n" +
      "    border: 1px solid rgba(144, 224, 239, 0.25);\n" +
      "    border-radius: 3px;\n" +
      "    padding: 1px 5px;\n" +
      "    font-size: 10px;\n" +
      "    font-family: Consolas, monospace;\n" +
      "    margin-bottom: 5px;\n" +
      "  }\n" +
      "  .msg-body {\n" +
      "    font-size: 12px;\n" +
      "    color: #eaf2ff;\n" +
      "  }\n" +
      "  .msg-body p {\n" +
      "    margin: 0 0 7px 0;\n" +
      "  }\n" +
      "  .msg-body p:last-child {\n" +
      "    margin-bottom: 0;\n" +
      "  }\n" +
      "  .msg-body h1, .msg-body h2, .msg-body h3, .msg-body h4 {\n" +
      "    color: #5ce1e6;\n" +
      "    margin: 8px 0 4px 0;\n" +
      "    font-size: 12.5px;\n" +
      "  }\n" +
      "  .msg-body ul, .msg-body ol {\n" +
      "    margin: 4px 0 8px 0;\n" +
      "    padding-left: 18px;\n" +
      "  }\n" +
      "  .msg-body li {\n" +
      "    margin-bottom: 3px;\n" +
      "  }\n" +
      "  .inline-code {\n" +
      "    background: #061020;\n" +
      "    color: #72efdd;\n" +
      "    border: 1px solid #1c3b68;\n" +
      "    border-radius: 3px;\n" +
      "    padding: 1px 4px;\n" +
      "    font-family: Consolas, 'Courier New', monospace;\n" +
      "    font-size: 11px;\n" +
      "  }\n" +
      "  .code-block-wrap {\n" +
      "    margin: 7px 0;\n" +
      "    border-radius: 6px;\n" +
      "    overflow: hidden;\n" +
      "    border: 1px solid #224477;\n" +
      "    background: #040b17;\n" +
      "  }\n" +
      "  .code-block-hdr {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-justify-content: space-between;\n" +
      "    justify-content: space-between;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    background: #0b1b36;\n" +
      "    padding: 3px 8px;\n" +
      "    font-size: 10px;\n" +
      "    color: #8ab4f8;\n" +
      "    border-bottom: 1px solid #1b3661;\n" +
      "  }\n" +
      "  .copy-code-btn {\n" +
      "    background: rgba(92, 225, 230, 0.14);\n" +
      "    color: #72efdd;\n" +
      "    border: 1px solid rgba(92, 225, 230, 0.35);\n" +
      "    border-radius: 3px;\n" +
      "    padding: 1px 6px;\n" +
      "    font-size: 10px;\n" +
      "    cursor: pointer;\n" +
      "  }\n" +
      "  .copy-code-btn:hover {\n" +
      "    background: rgba(92, 225, 230, 0.28);\n" +
      "    color: #ffffff;\n" +
      "  }\n" +
      "  .code-block-pre {\n" +
      "    margin: 0;\n" +
      "    padding: 8px;\n" +
      "    overflow-x: auto;\n" +
      "    font-family: Consolas, 'Courier New', monospace;\n" +
      "    font-size: 11px;\n" +
      "    line-height: 1.4;\n" +
      "    color: #d8e8ff;\n" +
      "    white-space: pre;\n" +
      "  }\n" +
      "  .plan-ready-banner {\n" +
      "    margin-top: 8px;\n" +
      "    padding: 6px 9px;\n" +
      "    background: rgba(0, 180, 216, 0.14);\n" +
      "    border: 1px solid rgba(92, 225, 230, 0.45);\n" +
      "    border-radius: 5px;\n" +
      "    color: #72efdd;\n" +
      "    font-size: 11px;\n" +
      "    font-weight: 600;\n" +
      "  }\n" +
      "  /* Animated Working Bubble */\n" +
      "  .working-card {\n" +
      "    display: none;\n" +
      "    margin-bottom: 10px;\n" +
      "    border-radius: 8px;\n" +
      "    padding: 9px 12px;\n" +
      "    background: linear-gradient(90deg, #0b2144 0%, #102e5e 100%);\n" +
      "    border: 1px solid #00b4d8;\n" +
      "    box-shadow: 0 0 12px rgba(0, 180, 216, 0.22);\n" +
      "  }\n" +
      "  .working-row {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "  }\n" +
      "  .pulse-dots {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    margin-right: 9px;\n" +
      "  }\n" +
      "  .pulse-dot {\n" +
      "    width: 7px;\n" +
      "    height: 7px;\n" +
      "    margin-right: 4px;\n" +
      "    border-radius: 50%;\n" +
      "    background: #5ce1e6;\n" +
      "    -webkit-animation: pulseAnim 1.2s infinite ease-in-out;\n" +
      "    animation: pulseAnim 1.2s infinite ease-in-out;\n" +
      "  }\n" +
      "  .pulse-dot:nth-child(2) {\n" +
      "    -webkit-animation-delay: 0.2s;\n" +
      "    animation-delay: 0.2s;\n" +
      "  }\n" +
      "  .pulse-dot:nth-child(3) {\n" +
      "    -webkit-animation-delay: 0.4s;\n" +
      "    animation-delay: 0.4s;\n" +
      "  }\n" +
      "  @-webkit-keyframes pulseAnim {\n" +
      "    0%, 80%, 100% { transform: scale(0.65); opacity: 0.45; }\n" +
      "    40% { transform: scale(1.15); opacity: 1.0; }\n" +
      "  }\n" +
      "  @keyframes pulseAnim {\n" +
      "    0%, 80%, 100% { transform: scale(0.65); opacity: 0.45; }\n" +
      "    40% { transform: scale(1.15); opacity: 1.0; }\n" +
      "  }\n" +
      "  .working-text {\n" +
      "    font-size: 11.5px;\n" +
      "    font-weight: 600;\n" +
      "    color: #72efdd;\n" +
      "  }\n" +
      "  /* 5. Station Plan & Undo Bar (No duplicate buttons!) */\n" +
      "  .plan-bar {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    -webkit-justify-content: space-between;\n" +
      "    justify-content: space-between;\n" +
      "    padding: 6px 10px;\n" +
      "    background: #0b1b36;\n" +
      "    border-top: 1px solid #1b3968;\n" +
      "    border-bottom: 1px solid #1b3968;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .plan-status {\n" +
      "    font-size: 11px;\n" +
      "    font-weight: 700;\n" +
      "    color: #88a8d4;\n" +
      "    white-space: nowrap;\n" +
      "    overflow: hidden;\n" +
      "    text-overflow: ellipsis;\n" +
      "    margin-right: 8px;\n" +
      "  }\n" +
      "  .plan-status-active {\n" +
      "    color: #5ce1e6;\n" +
      "  }\n" +
      "  .plan-btns {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .action-btn {\n" +
      "    background: #15315c;\n" +
      "    color: #dbeaff;\n" +
      "    border: 1px solid #2b5c9e;\n" +
      "    border-radius: 5px;\n" +
      "    padding: 4px 10px;\n" +
      "    margin-left: 6px;\n" +
      "    font-size: 11px;\n" +
      "    font-weight: 600;\n" +
      "    cursor: pointer;\n" +
      "    outline: none;\n" +
      "  }\n" +
      "  .action-btn:hover:not(:disabled) {\n" +
      "    background: #1e4682;\n" +
      "    border-color: #5ce1e6;\n" +
      "    color: #ffffff;\n" +
      "  }\n" +
      "  .action-btn-primary:not(:disabled) {\n" +
      "    background: linear-gradient(90deg, #0077b6 0%, #0096c7 100%);\n" +
      "    border-color: #5ce1e6;\n" +
      "    color: #ffffff;\n" +
      "  }\n" +
      "  .action-btn:disabled {\n" +
      "    opacity: 0.42;\n" +
      "    cursor: default;\n" +
      "  }\n" +
      "  /* 6. Prompt Composer */\n" +
      "  .composer-pane {\n" +
      "    padding: 8px 10px;\n" +
      "    background: #09162b;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "  }\n" +
      "  .prompt-box {\n" +
      "    width: 100%;\n" +
      "    height: 68px;\n" +
      "    background: #050e1d;\n" +
      "    color: #f0f6ff;\n" +
      "    border: 1px solid #254b82;\n" +
      "    border-radius: 6px;\n" +
      "    padding: 7px 9px;\n" +
      "    font-family: 'Segoe UI', Tahoma, Arial, sans-serif;\n" +
      "    font-size: 12px;\n" +
      "    line-height: 1.4;\n" +
      "    resize: none;\n" +
      "    outline: none;\n" +
      "  }\n" +
      "  .prompt-box:focus {\n" +
      "    border-color: #5ce1e6;\n" +
      "    box-shadow: 0 0 0 1px rgba(92, 225, 230, 0.25);\n" +
      "  }\n" +
      "  .composer-footer {\n" +
      "    display: -webkit-flex;\n" +
      "    display: flex;\n" +
      "    -webkit-align-items: center;\n" +
      "    align-items: center;\n" +
      "    -webkit-justify-content: space-between;\n" +
      "    justify-content: space-between;\n" +
      "    margin-top: 6px;\n" +
      "  }\n" +
      "  .status-label {\n" +
      "    font-size: 10.5px;\n" +
      "    color: #7ea0cc;\n" +
      "    white-space: nowrap;\n" +
      "    overflow: hidden;\n" +
      "    text-overflow: ellipsis;\n" +
      "    margin-right: 8px;\n" +
      "  }\n" +
      "  .send-btn {\n" +
      "    background: linear-gradient(90deg, #00b4d8 0%, #0077b6 100%);\n" +
      "    color: #ffffff;\n" +
      "    border: 1px solid #5ce1e6;\n" +
      "    border-radius: 5px;\n" +
      "    padding: 5px 14px;\n" +
      "    font-size: 11.5px;\n" +
      "    font-weight: 700;\n" +
      "    cursor: pointer;\n" +
      "    outline: none;\n" +
      "    -webkit-flex-shrink: 0;\n" +
      "    flex-shrink: 0;\n" +
      "    box-shadow: 0 2px 8px rgba(0, 180, 216, 0.3);\n" +
      "  }\n" +
      "  .send-btn:hover {\n" +
      "    background: linear-gradient(90deg, #2ec4b6 0%, #0096c7 100%);\n" +
      "  }\n" +
      "  .send-btn:active {\n" +
      "    transform: translateY(1px);\n" +
      "  }\n" +
      "</style>\n" +
      "</head>\n" +
      "<body>\n" +
      "<div class=\"app-shell\">\n" +
      "  <!-- 1. Top Header Bar -->\n" +
      "  <div class=\"top-header\">\n" +
      "    <div class=\"brand-group\">\n" +
      "      <svg class=\"brand-icon\" viewBox=\"0 0 32 32\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
      "        <rect x=\"1\" y=\"1\" width=\"30\" height=\"30\" rx=\"7\" fill=\"#071329\" stroke=\"#5ce1e6\" stroke-width=\"1.5\"/>\n" +
      "        <circle cx=\"16\" cy=\"16\" r=\"6.5\" fill=\"#00b4d8\"/>\n" +
      "        <circle cx=\"6.5\" cy=\"7.5\" r=\"2\" fill=\"#72efdd\"/>\n" +
      "        <circle cx=\"25.5\" cy=\"7.5\" r=\"2\" fill=\"#72efdd\"/>\n" +
      "        <circle cx=\"6.5\" cy=\"24.5\" r=\"2\" fill=\"#72efdd\"/>\n" +
      "        <circle cx=\"25.5\" cy=\"24.5\" r=\"2\" fill=\"#72efdd\"/>\n" +
      "        <line x1=\"8\" y1=\"9\" x2=\"11.5\" y2=\"12\" stroke=\"#5ce1e6\" stroke-width=\"1.3\"/>\n" +
      "        <line x1=\"24\" y1=\"9\" x2=\"20.5\" y2=\"12\" stroke=\"#5ce1e6\" stroke-width=\"1.3\"/>\n" +
      "        <line x1=\"8\" y1=\"23\" x2=\"11.5\" y2=\"20\" stroke=\"#5ce1e6\" stroke-width=\"1.3\"/>\n" +
      "        <line x1=\"24\" y1=\"23\" x2=\"20.5\" y2=\"20\" stroke=\"#5ce1e6\" stroke-width=\"1.3\"/>\n" +
      "      </svg>\n" +
      "      <div class=\"brand-title\">Eddie's <span>AI Assistant</span></div>\n" +
      "    </div>\n" +
      "    <div class=\"header-actions\">\n" +
      "      <button type=\"button\" class=\"hdr-btn\" onclick=\"sendToJava('account', '')\" title=\"Configure Google Gemini or Company AI Credentials\">Account</button>\n" +
      "      <button type=\"button\" class=\"hdr-btn\" onclick=\"sendToJava('clearChat', '')\" title=\"Clear conversation history\">Clear Chat</button>\n" +
      "      <button type=\"button\" class=\"hdr-btn hdr-btn-close\" onclick=\"sendToJava('hidePanel', '')\" title=\"Hide Eddie's AI Panel\">Hide</button>\n" +
      "    </div>\n" +
      "  </div>\n" +
      "\n" +
      "  <!-- 2. Control & Context Deck -->\n" +
      "  <div class=\"control-deck\">\n" +
      "    <div class=\"deck-row\">\n" +
      "      <span class=\"deck-label\">Model:</span>\n" +
      "      <select id=\"modelSelect\" class=\"model-select\" onchange=\"onModelChanged(this.value)\">\n" +
      modelOptions.toString() +
      "      </select>\n" +
      "    </div>\n" +
      "    <div class=\"deck-row\">\n" +
      "      <span class=\"deck-label\">Auth:</span>\n" +
      "      <div id=\"authBadge\" class=\"auth-pill auth-ok\" onclick=\"sendToJava('account', '')\" title=\"Click to configure credentials\">Checking credentials...</div>\n" +
      "    </div>\n" +
      "    <div class=\"deck-row\">\n" +
      "      <span class=\"deck-label\">Target:</span>\n" +
      "      <div id=\"targetBadge\" class=\"target-box\" title=\"Active Niagara Workbench view target\">None</div>\n" +
      "      <button type=\"button\" class=\"sync-btn\" onclick=\"sendToJava('syncContext', '')\" title=\"Sync with active Workbench view\">&#8635; Sync</button>\n" +
      "    </div>\n" +
      "    <div class=\"checks-row\">\n" +
      "      <label class=\"chk-label\" title=\"Include active WireSheet, Component, BProgram, or PX file context\">\n" +
      "        <input type=\"checkbox\" id=\"includeCtxCheck\" checked=\"checked\" onchange=\"onOptionsChanged()\"> Include active view context\n" +
      "      </label>\n" +
      "      <label class=\"chk-label\" title=\"Automatically open the 3-option confirmation dialog when a plan is generated\">\n" +
      "        <input type=\"checkbox\" id=\"autoApplyCheck\" checked=\"checked\" onchange=\"onOptionsChanged()\"> Auto-confirm plan\n" +
      "      </label>\n" +
      "    </div>\n" +
      "  </div>\n" +
      "\n" +
      "  <!-- 3. Quick Actions Chip Bar (Each action appears ONLY once in the UI) -->\n" +
      "  <div class=\"quick-bar\">\n" +
      "    <button type=\"button\" class=\"chip-btn\" onclick=\"fillQuickPrompt('Explain the active station component/wiresheet, its properties, facets, and links.')\">Explain</button>\n" +
      "    <button type=\"button\" class=\"chip-btn\" onclick=\"fillQuickPrompt('Create and wire the following logic on the active WireSheet: ')\">Build Logic</button>\n" +
      "    <button type=\"button\" class=\"chip-btn chip-btn-accent\" onclick=\"sendToJava('arrangeWs', '')\" title=\"Arrange all blocks on the active WireSheet (left-to-right signal flow + 5-col grid)\">&#9889; Arrange WS</button>\n" +
      "    <button type=\"button\" class=\"chip-btn\" onclick=\"fillQuickPrompt('Write or update the Niagara 4 BProgram Java code on the active target for: ')\">Program Code</button>\n" +
      "    <button type=\"button\" class=\"chip-btn\" onclick=\"fillQuickPrompt('Check this station target and its child points/links for configuration or status issues.')\">Troubleshoot</button>\n" +
      "  </div>\n" +
      "\n" +
      "  <!-- 4. Conversation Stream -->\n" +
      "  <div id=\"chatStream\" class=\"chat-stream\">\n" +
      "    <div id=\"chatMessages\"></div>\n" +
      "    <div id=\"workingCard\" class=\"working-card\">\n" +
      "      <div class=\"working-row\">\n" +
      "        <div class=\"pulse-dots\">\n" +
      "          <div class=\"pulse-dot\"></div>\n" +
      "          <div class=\"pulse-dot\"></div>\n" +
      "          <div class=\"pulse-dot\"></div>\n" +
      "        </div>\n" +
      "        <div id=\"workingText\" class=\"working-text\">Working...</div>\n" +
      "      </div>\n" +
      "    </div>\n" +
      "  </div>\n" +
      "\n" +
      "  <!-- 5. Station Plan & Undo Bar -->\n" +
      "  <div class=\"plan-bar\">\n" +
      "    <div id=\"planStatusLabel\" class=\"plan-status\">Station Plan: None pending</div>\n" +
      "    <div class=\"plan-btns\">\n" +
      "      <button type=\"button\" id=\"reviewApplyBtn\" class=\"action-btn action-btn-primary\" disabled=\"disabled\" onclick=\"sendToJava('reviewApplyPlan', '')\" title=\"Review and apply pending station/file modifications\">Review &amp; Apply</button>\n" +
      "      <button type=\"button\" id=\"undoBtn\" class=\"action-btn\" disabled=\"disabled\" onclick=\"sendToJava('undoLast', '')\" title=\"Revert the most recent station/file modification snapshot\">Undo</button>\n" +
      "    </div>\n" +
      "  </div>\n" +
      "\n" +
      "  <!-- 6. Prompt Composer -->\n" +
      "  <div class=\"composer-pane\">\n" +
      "    <textarea id=\"promptInput\" class=\"prompt-box\" placeholder=\"Ask about the active view or describe station changes... (Enter to send, Shift+Enter for new line)\"></textarea>\n" +
      "    <div class=\"composer-footer\">\n" +
      "      <div id=\"statusText\" class=\"status-label\">Ready (Personal Google / Company AI)</div>\n" +
      "      <button type=\"button\" id=\"sendBtn\" class=\"send-btn\" onclick=\"submitPrompt()\">Send &#9654;</button>\n" +
      "    </div>\n" +
      "  </div>\n" +
      "</div>\n" +
      "\n" +
      "<script type=\"text/javascript\">\n" +
      "  var codeStore = [];\n" +
      "\n" +
      "  function sendToJava(cmd, payload) {\n" +
      "    try {\n" +
      "      window.alert('EDDIES_IPC:' + cmd + ':' + (payload !== undefined && payload !== null ? String(payload) : ''));\n" +
      "    } catch (e) {}\n" +
      "  }\n" +
      "\n" +
      "  function onModelChanged(val) {\n" +
      "    sendToJava('selectModel', val);\n" +
      "  }\n" +
      "\n" +
      "  function onOptionsChanged() {\n" +
      "    var inc = document.getElementById('includeCtxCheck').checked ? '1' : '0';\n" +
      "    var auto = document.getElementById('autoApplyCheck').checked ? '1' : '0';\n" +
      "    sendToJava('optionsChanged', inc + ',' + auto);\n" +
      "  }\n" +
      "\n" +
      "  function fillQuickPrompt(templateText) {\n" +
      "    sendToJava('syncContext', '');\n" +
      "    var box = document.getElementById('promptInput');\n" +
      "    box.value = templateText;\n" +
      "    box.focus();\n" +
      "    try {\n" +
      "      box.selectionStart = box.selectionEnd = box.value.length;\n" +
      "    } catch (e) {}\n" +
      "  }\n" +
      "\n" +
      "  function submitPrompt() {\n" +
      "    var box = document.getElementById('promptInput');\n" +
      "    var text = box.value;\n" +
      "    if (!text || !text.replace(/^\\s+|\\s+$/g, '')) {\n" +
      "      return;\n" +
      "    }\n" +
      "    var inc = document.getElementById('includeCtxCheck').checked ? '1' : '0';\n" +
      "    var auto = document.getElementById('autoApplyCheck').checked ? '1' : '0';\n" +
      "    var model = document.getElementById('modelSelect').value;\n" +
      "    box.value = '';\n" +
      "    sendToJava('sendPrompt', inc + '|' + auto + '|' + model + '|' + text);\n" +
      "  }\n" +
      "\n" +
      "  document.getElementById('promptInput').addEventListener('keydown', function(e) {\n" +
      "    var key = e.keyCode || e.which;\n" +
      "    if (key === 13 && !e.shiftKey) {\n" +
      "      e.preventDefault();\n" +
      "      submitPrompt();\n" +
      "    }\n" +
      "  });\n" +
      "\n" +
      "  function scrollToBottom() {\n" +
      "    var stream = document.getElementById('chatStream');\n" +
      "    if (stream) {\n" +
      "      stream.scrollTop = stream.scrollHeight;\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  function showWelcomeCard() {\n" +
      "    codeStore = [];\n" +
      "    var container = document.getElementById('chatMessages');\n" +
      "    container.innerHTML =\n" +
      "      '<div class=\"msg-card msg-welcome\">' +\n" +
      "        '<div class=\"welcome-title\">&#9889; Eddie\\'s Workbench AI Assistant</div>' +\n" +
      "        '<div class=\"welcome-sub\">Supports your <strong>Personal Google Account</strong> (Direct Gemini API, AES-GCM-256 encrypted key) and <strong>Company AI Gateway</strong>.</div>' +\n" +
      "        '<ol class=\"welcome-list\">' +\n" +
      "          '<li>Click <strong>Account</strong> in the top-right header to configure your Personal Google Gemini API Key or Company AI credentials.</li>' +\n" +
      "          '<li>Ask questions about the active WireSheet, component properties, links, BProgram code, or PX view.</li>' +\n" +
      "          '<li>Give instructions to build or modify logic (every change is protected by the 3-option Guardrail Dialog and 1-click Snapshot Undo).</li>' +\n" +
      "        '</ol>' +\n" +
      "      '</div>';\n" +
      "    setWorkingState(false, '');\n" +
      "    scrollToBottom();\n" +
      "  }\n" +
      "\n" +
      "  function setWorkingState(isWorking, message) {\n" +
      "    var card = document.getElementById('workingCard');\n" +
      "    var txt = document.getElementById('workingText');\n" +
      "    if (isWorking) {\n" +
      "      txt.textContent = message || 'Working...';\n" +
      "      card.style.display = 'block';\n" +
      "      setStatusText(message || 'Working...');\n" +
      "    } else {\n" +
      "      card.style.display = 'none';\n" +
      "    }\n" +
      "    scrollToBottom();\n" +
      "  }\n" +
      "\n" +
      "  function setStatusText(text) {\n" +
      "    var el = document.getElementById('statusText');\n" +
      "    if (el) {\n" +
      "      el.textContent = text || '';\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  function setModelList(models, selectedModel) {\n" +
      "    var sel = document.getElementById('modelSelect');\n" +
      "    if (!sel || !models || !models.length) return;\n" +
      "    sel.innerHTML = '';\n" +
      "    var matchedIdx = 0;\n" +
      "    for (var i = 0; i < models.length; i++) {\n" +
      "      var opt = document.createElement('option');\n" +
      "      opt.value = models[i];\n" +
      "      opt.textContent = models[i];\n" +
      "      if (selectedModel && (models[i] === selectedModel || models[i].indexOf(selectedModel) === 0)) {\n" +
      "        matchedIdx = i;\n" +
      "      }\n" +
      "      sel.appendChild(opt);\n" +
      "    }\n" +
      "    sel.selectedIndex = matchedIdx;\n" +
      "  }\n" +
      "\n" +
      "  function updateAuthBadge(badgeText, isNeedsKey, selectedModel) {\n" +
      "    var el = document.getElementById('authBadge');\n" +
      "    if (el) {\n" +
      "      el.textContent = (isNeedsKey ? '▲ ' : '● ') + badgeText;\n" +
      "      el.className = 'auth-pill ' + (isNeedsKey ? 'auth-warn' : 'auth-ok');\n" +
      "    }\n" +
      "    if (selectedModel) {\n" +
      "      var sel = document.getElementById('modelSelect');\n" +
      "      if (sel && sel.value !== selectedModel) {\n" +
      "        for (var i = 0; i < sel.options.length; i++) {\n" +
      "          if (sel.options[i].value === selectedModel || sel.options[i].value.indexOf(selectedModel) === 0) {\n" +
      "            sel.selectedIndex = i;\n" +
      "            break;\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  function updateTargetBadge(summaryText) {\n" +
      "    var el = document.getElementById('targetBadge');\n" +
      "    if (el) {\n" +
      "      el.textContent = summaryText || 'None';\n" +
      "      el.title = summaryText || 'None';\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  function updateActionButtons(pendingCount, hasUndo) {\n" +
      "    var lbl = document.getElementById('planStatusLabel');\n" +
      "    var revBtn = document.getElementById('reviewApplyBtn');\n" +
      "    var undoBtn = document.getElementById('undoBtn');\n" +
      "    if (pendingCount > 0) {\n" +
      "      lbl.textContent = '⚡ Pending Plan: ' + pendingCount + ' action(s) ready';\n" +
      "      lbl.className = 'plan-status plan-status-active';\n" +
      "      revBtn.disabled = false;\n" +
      "    } else {\n" +
      "      lbl.textContent = 'Station Plan: None pending';\n" +
      "      lbl.className = 'plan-status';\n" +
      "      revBtn.disabled = true;\n" +
      "    }\n" +
      "    undoBtn.disabled = !hasUndo;\n" +
      "  }\n" +
      "\n" +
      "  function appendUserMessage(timestamp, targetSummary, promptText) {\n" +
      "    var container = document.getElementById('chatMessages');\n" +
      "    var div = document.createElement('div');\n" +
      "    div.className = 'msg-card msg-user';\n" +
      "    var targetHtml = targetSummary ? '<div class=\"msg-target-tag\">Target: ' + escapeHtml(targetSummary) + '</div>' : '';\n" +
      "    div.innerHTML =\n" +
      "      '<div class=\"msg-meta\"><span class=\"msg-author\">You</span><span>' + escapeHtml(timestamp) + '</span></div>' +\n" +
      "      targetHtml +\n" +
      "      '<div class=\"msg-body\">' + formatInlineWithBreaks(promptText) + '</div>';\n" +
      "    container.appendChild(div);\n" +
      "    scrollToBottom();\n" +
      "  }\n" +
      "\n" +
      "  function appendAiMessage(timestamp, modelName, markdownText, planCount) {\n" +
      "    var container = document.getElementById('chatMessages');\n" +
      "    var div = document.createElement('div');\n" +
      "    div.className = 'msg-card msg-ai';\n" +
      "    var planBanner = '';\n" +
      "    if (planCount && planCount > 0) {\n" +
      "      planBanner = '<div class=\"plan-ready-banner\">&#9889; ' + planCount + ' station/file modification(s) prepared. Use <strong>Review &amp; Apply</strong> below or the confirmation dialog.</div>';\n" +
      "    }\n" +
      "    div.innerHTML =\n" +
      "      '<div class=\"msg-meta\"><span class=\"msg-author\">Eddie\\'s AI (' + escapeHtml(modelName) + ')</span><span>' + escapeHtml(timestamp) + '</span></div>' +\n" +
      "      '<div class=\"msg-body\">' + renderMarkdown(markdownText) + '</div>' +\n" +
      "      planBanner;\n" +
      "    container.appendChild(div);\n" +
      "    scrollToBottom();\n" +
      "  }\n" +
      "\n" +
      "  function appendSystemMessage(title, detailsText, isError) {\n" +
      "    var container = document.getElementById('chatMessages');\n" +
      "    var div = document.createElement('div');\n" +
      "    div.className = 'msg-card ' + (isError ? 'msg-error' : 'msg-system');\n" +
      "    div.innerHTML =\n" +
      "      '<div class=\"msg-meta\"><span class=\"msg-author\">' + escapeHtml(title) + '</span></div>' +\n" +
      "      '<div class=\"msg-body\">' + formatInlineWithBreaks(detailsText) + '</div>';\n" +
      "    container.appendChild(div);\n" +
      "    scrollToBottom();\n" +
      "  }\n" +
      "\n" +
      "  function copyCodeBlock(idx, btnEl) {\n" +
      "    var code = codeStore[idx] || '';\n" +
      "    sendToJava('copyClipboard', code);\n" +
      "    if (btnEl) {\n" +
      "      var old = btnEl.textContent;\n" +
      "      btnEl.textContent = '✓ Copied';\n" +
      "      setTimeout(function() { btnEl.textContent = old; }, 1600);\n" +
      "    }\n" +
      "  }\n" +
      "\n" +
      "  function escapeHtml(str) {\n" +
      "    if (!str) return '';\n" +
      "    return String(str)\n" +
      "      .replace(/&/g, '&amp;')\n" +
      "      .replace(/</g, '&lt;')\n" +
      "      .replace(/>/g, '&gt;')\n" +
      "      .replace(/\"/g, '&quot;');\n" +
      "  }\n" +
      "\n" +
      "  function formatInline(text) {\n" +
      "    var s = escapeHtml(text);\n" +
      "    s = s.replace(/`([^`]+)`/g, '<span class=\"inline-code\">$1</span>');\n" +
      "    s = s.replace(/\\*\\*([^*]+)\\*\\*/g, '<strong>$1</strong>');\n" +
      "    return s;\n" +
      "  }\n" +
      "\n" +
      "  function formatInlineWithBreaks(text) {\n" +
      "    if (!text) return '';\n" +
      "    return formatInline(text).replace(/\\r?\\n/g, '<br>');\n" +
      "  }\n" +
      "\n" +
      "  function renderMarkdown(md) {\n" +
      "    if (!md) return '';\n" +
      "    var parts = String(md).split(/```/);\n" +
      "    var html = '';\n" +
      "    for (var i = 0; i < parts.length; i++) {\n" +
      "      if (i % 2 === 1) {\n" +
      "        var block = parts[i];\n" +
      "        var lang = 'code';\n" +
      "        var firstNl = block.indexOf('\\n');\n" +
      "        if (firstNl >= 0) {\n" +
      "          var firstLine = block.substring(0, firstNl).replace(/^\\s+|\\s+$/g, '');\n" +
      "          if (firstLine && firstLine.length < 20 && firstLine.indexOf(' ') < 0) {\n" +
      "            lang = firstLine;\n" +
      "            block = block.substring(firstNl + 1);\n" +
      "          }\n" +
      "        }\n" +
      "        block = block.replace(/^\\r?\\n|\\r?\\n$/g, '');\n" +
      "        var idx = codeStore.length;\n" +
      "        codeStore.push(block);\n" +
      "        html += '<div class=\"code-block-wrap\">' +\n" +
      "          '<div class=\"code-block-hdr\"><span>' + escapeHtml(lang.toUpperCase()) + '</span>' +\n" +
      "          '<button type=\"button\" class=\"copy-code-btn\" onclick=\"copyCodeBlock(' + idx + ', this)\">Copy</button></div>' +\n" +
      "          '<pre class=\"code-block-pre\">' + escapeHtml(block) + '</pre></div>';\n" +
      "      } else {\n" +
      "        html += renderMarkdownProse(parts[i]);\n" +
      "      }\n" +
      "    }\n" +
      "    return html;\n" +
      "  }\n" +
      "\n" +
      "  function renderMarkdownProse(text) {\n" +
      "    var lines = String(text).split(/\\r?\\n/);\n" +
      "    var out = '';\n" +
      "    var inList = false;\n" +
      "    for (var i = 0; i < lines.length; i++) {\n" +
      "      var line = lines[i];\n" +
      "      var trimmed = line.replace(/^\\s+|\\s+$/g, '');\n" +
      "      if (!trimmed) {\n" +
      "        if (inList) { out += '</ul>'; inList = false; }\n" +
      "        continue;\n" +
      "      }\n" +
      "      if (trimmed.indexOf('### ') === 0) {\n" +
      "        if (inList) { out += '</ul>'; inList = false; }\n" +
      "        out += '<h3>' + formatInline(trimmed.substring(4)) + '</h3>';\n" +
      "      } else if (trimmed.indexOf('## ') === 0) {\n" +
      "        if (inList) { out += '</ul>'; inList = false; }\n" +
      "        out += '<h2>' + formatInline(trimmed.substring(3)) + '</h2>';\n" +
      "      } else if (trimmed.indexOf('# ') === 0) {\n" +
      "        if (inList) { out += '</ul>'; inList = false; }\n" +
      "        out += '<h1>' + formatInline(trimmed.substring(2)) + '</h1>';\n" +
      "      } else if (trimmed.indexOf('- ') === 0 || trimmed.indexOf('* ') === 0) {\n" +
      "        if (!inList) { out += '<ul>'; inList = true; }\n" +
      "        out += '<li>' + formatInline(trimmed.substring(2)) + '</li>';\n" +
      "      } else if (/^\\d+\\.\\s+/.test(trimmed)) {\n" +
      "        if (!inList) { out += '<ul>'; inList = true; }\n" +
      "        out += '<li>' + formatInline(trimmed.replace(/^\\d+\\.\\s+/, '')) + '</li>';\n" +
      "      } else {\n" +
      "        if (inList) { out += '</ul>'; inList = false; }\n" +
      "        out += '<p>' + formatInline(line) + '</p>';\n" +
      "      }\n" +
      "    }\n" +
      "    if (inList) { out += '</ul>'; }\n" +
      "    return out;\n" +
      "  }\n" +
      "\n" +
      "  function focusPromptBox() {\n" +
      "    var box = document.getElementById('promptInput');\n" +
      "    if (box) box.focus();\n" +
      "  }\n" +
      "\n" +
      "  showWelcomeCard();\n" +
      "</script>\n" +
      "</body>\n" +
      "</html>\n";
  }

  private static String escapeHtml(String s)
  {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
  }

  private static String escapeHtmlAttr(String s)
  {
    return escapeHtml(s).replace("\"", "&quot;");
  }
}
