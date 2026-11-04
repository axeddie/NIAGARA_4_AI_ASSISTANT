/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.nio.charset.StandardCharsets;
import javax.baja.file.BIFile;
import javax.baja.naming.BOrd;
import javax.baja.naming.OrdTarget;
import javax.baja.sys.BObject;
import javax.baja.workbench.BWbShell;
import javax.baja.workbench.view.BWbView;

/**
 * Customizes the Niagara Workbench Home / About page (workbench:/help/about.html)
 * in-memory with custom AI-Powered Workbench vector graphics, neural-wiresheet SVG artwork,
 * and Company Copilot / Model Farm capability cards while preserving all official
 * Distech EC-Net Facilities and Niagara 4 version/license information.
 */
public final class EddiesAiHomePageCustomizer
{
  private static final String MARKER = "<!-- EDDIES_AI_WORKBENCH_HOME -->";
  private static volatile boolean customized = false;

  private EddiesAiHomePageCustomizer()
  {
  }

  public static void installAndRefresh(BWbShell shell)
  {
    try
    {
      BIFile aboutFile = customizeAboutHtml();
      if (aboutFile != null && shell != null)
      {
        BOrd activeOrd = shell.getActiveOrd();
        if (activeOrd != null && activeOrd.toString().contains("about.html"))
        {
          BWbView view = shell.getActiveView();
          if (view != null && aboutFile instanceof BObject)
          {
            view.loadValue((BObject) aboutFile, null);
          }
        }
      }
    }
    catch (Throwable ignore)
    {
    }
  }

  public static synchronized BIFile customizeAboutHtml()
  {
    try
    {
      OrdTarget target = BOrd.make("workbench:/help/about.html").resolve();
      BObject obj = (target != null) ? target.get() : null;
      if (!(obj instanceof BIFile))
      {
        return null;
      }

      BIFile aboutFile = (BIFile) obj;
      byte[] rawBytes = aboutFile.read();
      if (rawBytes == null || rawBytes.length == 0)
      {
        return aboutFile;
      }

      String html = new String(rawBytes, StandardCharsets.UTF_8);
      if (html.contains(MARKER))
      {
        customized = true;
        return aboutFile;
      }

      String enhanced = buildEnhancedHtml(html);
      aboutFile.write(enhanced.getBytes(StandardCharsets.UTF_8));
      customized = true;
      return aboutFile;
    }
    catch (Throwable t)
    {
      return null;
    }
  }

  private static String buildEnhancedHtml(String originalHtml)
  {
    String customCss =
      "\n" + MARKER + "\n" +
      "<style type=\"text/css\" media=\"all\">\n" +
      "  .wrapper {\n" +
      "    width: 920px !important;\n" +
      "    margin: 0 auto !important;\n" +
      "    position: relative !important;\n" +
      "    padding-top: 26px !important;\n" +
      "    font-family: 'Segoe UI', Tahoma, Arial, sans-serif !important;\n" +
      "  }\n" +
      "  .ai-splash-frame {\n" +
      "    position: relative;\n" +
      "    width: 330px;\n" +
      "    height: 430px;\n" +
      "    border-radius: 12px;\n" +
      "    overflow: hidden;\n" +
      "    box-shadow: 0 12px 28px rgba(8, 30, 75, 0.28);\n" +
      "  }\n" +
      "  .ai-splash-badge {\n" +
      "    position: absolute;\n" +
      "    top: 14px;\n" +
      "    left: 14px;\n" +
      "    right: 14px;\n" +
      "    background: linear-gradient(90deg, rgba(10,25,60,0.88) 0%, rgba(0,120,212,0.88) 100%);\n" +
      "    border: 1px solid rgba(100, 210, 255, 0.65);\n" +
      "    border-radius: 20px;\n" +
      "    padding: 6px 12px;\n" +
      "    color: #e8f7ff;\n" +
      "    font-size: 11px;\n" +
      "    font-weight: 700;\n" +
      "    letter-spacing: 0.8px;\n" +
      "    text-align: center;\n" +
      "    text-transform: uppercase;\n" +
      "    box-shadow: 0 4px 12px rgba(0, 180, 255, 0.35);\n" +
      "  }\n" +
      "  .ai-splash-badge span {\n" +
      "    color: #5ce1e6;\n" +
      "  }\n" +
      "  .ai-workbench-card {\n" +
      "    position: absolute;\n" +
      "    top: 26px;\n" +
      "    left: 350px;\n" +
      "    width: 400px;\n" +
      "    height: 430px;\n" +
      "    box-sizing: border-box;\n" +
      "    background: linear-gradient(145deg, #0b1933 0%, #102a56 52%, #0d3b78 100%);\n" +
      "    border: 1px solid #2b6cb0;\n" +
      "    border-radius: 12px;\n" +
      "    padding: 20px 22px;\n" +
      "    color: #f0f6ff;\n" +
      "    box-shadow: 0 12px 28px rgba(8, 30, 75, 0.26);\n" +
      "  }\n" +
      "  .ai-card-header {\n" +
      "    display: flex;\n" +
      "    align-items: center;\n" +
      "    border-bottom: 1px solid rgba(100, 180, 255, 0.25);\n" +
      "    padding-bottom: 12px;\n" +
      "    margin-bottom: 12px;\n" +
      "  }\n" +
      "  .ai-title-group {\n" +
      "    margin-left: 14px;\n" +
      "  }\n" +
      "  .ai-eyebrow {\n" +
      "    font-size: 10px;\n" +
      "    font-weight: 700;\n" +
      "    letter-spacing: 1.1px;\n" +
      "    text-transform: uppercase;\n" +
      "    color: #5ce1e6;\n" +
      "    margin: 0 0 3px 0;\n" +
      "  }\n" +
      "  .ai-main-title {\n" +
      "    font-size: 19px;\n" +
      "    font-weight: 700;\n" +
      "    color: #ffffff;\n" +
      "    margin: 0 0 3px 0;\n" +
      "  }\n" +
      "  .ai-subtitle {\n" +
      "    font-size: 11px;\n" +
      "    color: #b5d3f8;\n" +
      "    margin: 0;\n" +
      "  }\n" +
      "  .ai-diagram-box {\n" +
      "    background: rgba(6, 17, 38, 0.65);\n" +
      "    border: 1px solid rgba(92, 225, 230, 0.32);\n" +
      "    border-radius: 8px;\n" +
      "    padding: 8px 10px;\n" +
      "    margin-bottom: 12px;\n" +
      "    text-align: center;\n" +
      "  }\n" +
      "  .ai-feature-list {\n" +
      "    margin: 0;\n" +
      "    padding: 0;\n" +
      "    list-style: none;\n" +
      "  }\n" +
      "  .ai-feature-item {\n" +
      "    background: rgba(255, 255, 255, 0.06);\n" +
      "    border-left: 3px solid #5ce1e6;\n" +
      "    border-radius: 4px;\n" +
      "    padding: 7px 10px;\n" +
      "    margin-bottom: 8px;\n" +
      "    font-size: 11.5px;\n" +
      "    line-height: 1.38;\n" +
      "    color: #e2eeff;\n" +
      "  }\n" +
      "  .ai-feature-item strong {\n" +
      "    color: #ffffff;\n" +
      "    display: block;\n" +
      "    font-size: 12px;\n" +
      "    margin-bottom: 1px;\n" +
      "  }\n" +
      "  .ai-card-footer {\n" +
      "    margin-top: 12px;\n" +
      "    padding-top: 10px;\n" +
      "    border-top: 1px solid rgba(100, 180, 255, 0.22);\n" +
      "    display: flex;\n" +
      "    justify-content: space-between;\n" +
      "    align-items: center;\n" +
      "    font-size: 11px;\n" +
      "  }\n" +
      "  .ai-help-btn {\n" +
      "    background: linear-gradient(90deg, #00b4d8 0%, #0077b6 100%);\n" +
      "    color: #ffffff !important;\n" +
      "    text-decoration: none;\n" +
      "    padding: 5px 12px;\n" +
      "    border-radius: 4px;\n" +
      "    font-weight: 600;\n" +
      "    font-size: 11px;\n" +
      "    display: inline-block;\n" +
      "  }\n" +
      "  .ai-status-pill {\n" +
      "    color: #72efdd;\n" +
      "    font-weight: 600;\n" +
      "    font-size: 11px;\n" +
      "  }\n" +
      "  .info {\n" +
      "    width: 150px !important;\n" +
      "    position: absolute !important;\n" +
      "    top: 26px !important;\n" +
      "    right: 0 !important;\n" +
      "    margin-top: 8px !important;\n" +
      "  }\n" +
      "</style>\n";

    String aiCardHtml =
      "<div class=\"ai-workbench-card\">\n" +
      "  <div class=\"ai-card-header\">\n" +
      "    <svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
      "      <defs>\n" +
      "        <linearGradient id=\"aiCoreGrad\" x1=\"0%\" y1=\"0%\" x2=\"100%\" y2=\"100%\">\n" +
      "          <stop offset=\"0%\" stop-color=\"#5ce1e6\"/>\n" +
      "          <stop offset=\"100%\" stop-color=\"#0077b6\"/>\n" +
      "        </linearGradient>\n" +
      "      </defs>\n" +
      "      <rect x=\"2\" y=\"2\" width=\"44\" height=\"44\" rx=\"10\" fill=\"#071329\" stroke=\"#5ce1e6\" stroke-width=\"1.5\"/>\n" +
      "      <circle cx=\"24\" cy=\"24\" r=\"9\" fill=\"url(#aiCoreGrad)\"/>\n" +
      "      <circle cx=\"10\" cy=\"12\" r=\"3\" fill=\"#72efdd\"/>\n" +
      "      <circle cx=\"38\" cy=\"12\" r=\"3\" fill=\"#72efdd\"/>\n" +
      "      <circle cx=\"10\" cy=\"36\" r=\"3\" fill=\"#72efdd\"/>\n" +
      "      <circle cx=\"38\" cy=\"36\" r=\"3\" fill=\"#72efdd\"/>\n" +
      "      <line x1=\"12.5\" y1=\"14\" x2=\"17.5\" y2=\"18.5\" stroke=\"#5ce1e6\" stroke-width=\"1.6\"/>\n" +
      "      <line x1=\"35.5\" y1=\"14\" x2=\"30.5\" y2=\"18.5\" stroke=\"#5ce1e6\" stroke-width=\"1.6\"/>\n" +
      "      <line x1=\"12.5\" y1=\"34\" x2=\"17.5\" y2=\"29.5\" stroke=\"#5ce1e6\" stroke-width=\"1.6\"/>\n" +
      "      <line x1=\"35.5\" y1=\"34\" x2=\"30.5\" y2=\"29.5\" stroke=\"#5ce1e6\" stroke-width=\"1.6\"/>\n" +
      "      <text x=\"24\" y=\"27.5\" text-anchor=\"middle\" fill=\"#06152d\" font-size=\"10\" font-weight=\"bold\" font-family=\"Segoe UI, Arial\">AI</text>\n" +
      "    </svg>\n" +
      "    <div class=\"ai-title-group\">\n" +
      "      <p class=\"ai-eyebrow\">AI-Powered Engineering Edition</p>\n" +
      "      <h2 class=\"ai-main-title\">Eddie's Workbench AI</h2>\n" +
      "      <p class=\"ai-subtitle\">Company Copilot &amp; Model Farm Integration</p>\n" +
      "    </div>\n" +
      "  </div>\n" +
      "  <div class=\"ai-diagram-box\">\n" +
      "    <svg width=\"336\" height=\"58\" viewBox=\"0 0 336 58\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
      "      <!-- Niagara WireSheet Block 1 -->\n" +
      "      <rect x=\"6\" y=\"8\" width=\"86\" height=\"42\" rx=\"5\" fill=\"#12315c\" stroke=\"#4ea8de\" stroke-width=\"1.2\"/>\n" +
      "      <rect x=\"6\" y=\"8\" width=\"86\" height=\"13\" rx=\"4\" fill=\"#0077b6\"/>\n" +
      "      <text x=\"49\" y=\"17.5\" text-anchor=\"middle\" fill=\"#ffffff\" font-size=\"8.5\" font-weight=\"bold\" font-family=\"Segoe UI, Arial\">Station View</text>\n" +
      "      <text x=\"49\" y=\"32\" text-anchor=\"middle\" fill=\"#b5d3f8\" font-size=\"8\" font-family=\"Segoe UI, Arial\">WireSheet / PX</text>\n" +
      "      <text x=\"49\" y=\"43\" text-anchor=\"middle\" fill=\"#72efdd\" font-size=\"7.5\" font-family=\"Segoe UI, Arial\">Live Context</text>\n" +
      "      <!-- Wire 1 -->\n" +
      "      <path d=\"M 92 29 L 122 29\" stroke=\"#5ce1e6\" stroke-width=\"2\" stroke-dasharray=\"3,2\"/>\n" +
      "      <polygon points=\"122,25 129,29 122,33\" fill=\"#5ce1e6\"/>\n" +
      "      <!-- AI Copilot Core Block -->\n" +
      "      <rect x=\"130\" y=\"6\" width=\"82\" height=\"46\" rx=\"7\" fill=\"#0a2246\" stroke=\"#5ce1e6\" stroke-width=\"1.6\"/>\n" +
      "      <text x=\"171\" y=\"23\" text-anchor=\"middle\" fill=\"#5ce1e6\" font-size=\"9\" font-weight=\"bold\" font-family=\"Segoe UI, Arial\">COPILOT AI</text>\n" +
      "      <text x=\"171\" y=\"35\" text-anchor=\"middle\" fill=\"#ffffff\" font-size=\"7.5\" font-family=\"Segoe UI, Arial\">GPT-5 • Gemini</text>\n" +
      "      <text x=\"171\" y=\"45\" text-anchor=\"middle\" fill=\"#90e0ef\" font-size=\"7.5\" font-family=\"Segoe UI, Arial\">Claude Sonnet</text>\n" +
      "      <!-- Wire 2 -->\n" +
      "      <path d=\"M 212 29 L 238 29\" stroke=\"#72efdd\" stroke-width=\"2\"/>\n" +
      "      <polygon points=\"238,25 245,29 238,33\" fill=\"#72efdd\"/>\n" +
      "      <!-- Niagara WireSheet Block 2 -->\n" +
      "      <rect x=\"246\" y=\"8\" width=\"84\" height=\"42\" rx=\"5\" fill=\"#12315c\" stroke=\"#72efdd\" stroke-width=\"1.2\"/>\n" +
      "      <rect x=\"246\" y=\"8\" width=\"84\" height=\"13\" rx=\"4\" fill=\"#0096c7\"/>\n" +
      "      <text x=\"288\" y=\"17.5\" text-anchor=\"middle\" fill=\"#ffffff\" font-size=\"8.5\" font-weight=\"bold\" font-family=\"Segoe UI, Arial\">Auto-Builder</text>\n" +
      "      <text x=\"288\" y=\"32\" text-anchor=\"middle\" fill=\"#b5d3f8\" font-size=\"8\" font-family=\"Segoe UI, Arial\">Blocks &amp; Links</text>\n" +
      "      <text x=\"288\" y=\"43\" text-anchor=\"middle\" fill=\"#72efdd\" font-size=\"7.5\" font-family=\"Segoe UI, Arial\">BOG &amp; BProgram</text>\n" +
      "    </svg>\n" +
      "  </div>\n" +
      "  <ul class=\"ai-feature-list\">\n" +
      "    <li class=\"ai-feature-item\">\n" +
      "      <strong>&#9889; Live Station Context Extraction</strong>\n" +
      "      Automatically inspects the active WireSheet, slots, facets, links, BProgram Java code, and PX graphics.\n" +
      "    </li>\n" +
      "    <li class=\"ai-feature-item\">\n" +
      "      <strong>&#128295; Prompt-to-Action Station Engineering</strong>\n" +
      "      Creates blocks with clean Wixel layout, wires links, sets facets, injects BOG XML, and supports 1-click Undo.\n" +
      "    </li>\n" +
      "    <li class=\"ai-feature-item\">\n" +
      "      <strong>&#128274; Enterprise Model Farm &amp; M365 SSO</strong>\n" +
      "      Powered by Eddie's Company AI Platform subscription (aoai-farm.bosch-temp.com).\n" +
      "    </li>\n" +
      "  </ul>\n" +
      "  <div class=\"ai-card-footer\">\n" +
      "    <a class=\"ai-help-btn\" href=\"workbench:/help/contents.html\">Help Documents</a>\n" +
      "    <span class=\"ai-status-pill\">&#9679; AI Assistant Ready in Right Sidebar &rarr;</span>\n" +
      "  </div>\n" +
      "</div>\n";

    String updated = originalHtml;
    int headClose = updated.indexOf("</head>");
    if (headClose >= 0)
    {
      updated = updated.substring(0, headClose) + customCss + updated.substring(headClose);
    }
    else
    {
      updated = customCss + updated;
    }

    // Wrap the splash image in .ai-splash-frame with the glowing AI-Powered badge and insert the AI card
    int imgStart = updated.indexOf("<img src=");
    if (imgStart >= 0)
    {
      int imgEnd = updated.indexOf(">", imgStart);
      if (imgEnd > imgStart)
      {
        String imgTag = updated.substring(imgStart, imgEnd + 1);
        String wrappedSplash =
          "<div class=\"ai-splash-frame\">\n" +
          "  " + imgTag + "\n" +
          "  <div class=\"ai-splash-badge\">&#9889; <span>AI-Powered</span> Niagara Workbench</div>\n" +
          "</div>\n" +
          aiCardHtml;
        updated = updated.substring(0, imgStart) + wrappedSplash + updated.substring(imgEnd + 1);
        return updated;
      }
    }

    // Fallback: inject inside <div class="wrapper">
    int wrapperIdx = updated.indexOf("<div class=\"wrapper\">");
    if (wrapperIdx >= 0)
    {
      int insertPos = wrapperIdx + "<div class=\"wrapper\">".length();
      updated = updated.substring(0, insertPos) + "\n" + aiCardHtml + updated.substring(insertPos);
    }
    return updated;
  }
}
