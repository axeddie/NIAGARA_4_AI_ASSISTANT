/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.baja.file.BIFile;
import javax.baja.file.BSubSpaceFile;
import javax.baja.naming.BOrd;
import javax.baja.space.BComponentSpace;
import javax.baja.sys.BComponent;
import javax.baja.workbench.BWbShell;

/**
 * Manages the local persistent AI Brain Workspace on the user's machine
 * (defaulting to {@code <NiagaraUserHome>/eddiesWorkbenchAI}, or any custom folder chosen by the user),
 * plus the user-granted Local Niagara Reference & Knowledge Folders.
 *
 * Folder layout inside {@code <aiBrainDir>}:
 * <ul>
 *   <li>{@code rules/global_rules.md} — Permanent global rules & preferences learned from user feedback</li>
 *   <li>{@code stations/<StationName>.md} — Per-station learned context, architecture notes, and point conventions</li>
 *   <li>{@code skills/<skill_name>.md} — Reusable logic patterns, .bog templates, BProgram snippets, and PX patterns</li>
 *   <li>{@code chats/learned_log.md} — Chronological audit log of rules, memories, and skills learned</li>
 * </ul>
 */
public final class EddiesAiBrainManager
{
  private static final String DEFAULT_GLOBAL_RULES_HEADER =
    "# Eddie's Workbench AI — Global User Rules & Preferences\n"
    + "# The AI Assistant reads this file on every prompt and automatically appends new rules\n"
    + "# when you give it feedback, corrections, or use the /learn command.\n\n";

  private static final String DEFAULT_README =
    "# Eddie's Workbench AI — Local Brain & Rules Workspace\n\n"
    + "This folder stores persistent memory, rules, and reusable skills for **Eddie's Workbench AI Assistant**:\n\n"
    + "- `rules/global_rules.md` — Global rules, naming standards, and preferences learned from your feedback.\n"
    + "- `stations/<StationName>.md` — Per-station context and notes learned while working on specific stations.\n"
    + "- `skills/` — Reusable WireSheet logic patterns, `.bog` snippets, `BProgram` templates, and `.px` graphic templates.\n"
    + "- `chats/learned_log.md` — Audit log of learned rules and station notes.\n";

  private EddiesAiBrainManager()
  {
  }

  /**
   * Ensures the AI Brain directory structure and starter files exist on disk.
   */
  public static synchronized File ensureInitialized()
  {
    EddiesAiConfig cfg = EddiesAiConfig.get();
    cfg.load();
    File brainDir = cfg.getAiBrainDir();
    try
    {
      if (!brainDir.exists())
      {
        brainDir.mkdirs();
      }
      File rulesDir = new File(brainDir, "rules");
      File stationsDir = new File(brainDir, "stations");
      File skillsDir = new File(brainDir, "skills");
      File chatsDir = new File(brainDir, "chats");

      if (!rulesDir.exists()) rulesDir.mkdirs();
      if (!stationsDir.exists()) stationsDir.mkdirs();
      if (!skillsDir.exists()) skillsDir.mkdirs();
      if (!chatsDir.exists()) chatsDir.mkdirs();

      File readmeFile = new File(brainDir, "README.md");
      if (!readmeFile.exists())
      {
        writeUtf8File(readmeFile, DEFAULT_README);
      }

      File globalRulesFile = new File(rulesDir, "global_rules.md");
      if (!globalRulesFile.exists())
      {
        writeUtf8File(globalRulesFile, DEFAULT_GLOBAL_RULES_HEADER);
      }
    }
    catch (Throwable ignore)
    {
    }
    return brainDir;
  }

  public static File getGlobalRulesFile()
  {
    File brainDir = ensureInitialized();
    return new File(new File(brainDir, "rules"), "global_rules.md");
  }

  public static File getStationsDir()
  {
    File brainDir = ensureInitialized();
    return new File(brainDir, "stations");
  }

  public static File getSkillsDir()
  {
    File brainDir = ensureInitialized();
    return new File(brainDir, "skills");
  }

  public static File getStationMemoryFile(String stationKey)
  {
    String safeKey = sanitizeFileStem(stationKey);
    if (safeKey.isEmpty())
    {
      safeKey = "DefaultStation";
    }
    return new File(getStationsDir(), safeKey + ".md");
  }

  /**
   * Resolves a clean identifier for the currently active station or offline BOG file in Workbench.
   */
  public static String resolveActiveStationKey(BWbShell shell)
  {
    if (shell == null) return "Workbench";
    try
    {
      StationContextExtractor.StationTarget ctx = StationContextExtractor.extract(shell);
      if (ctx != null && ctx.targetComponent != null)
      {
        BComponent comp = ctx.targetComponent;
        BSubSpaceFile backingBog = StationContextExtractor.getBackingSubSpaceFile(comp);
        if (backingBog != null)
        {
          String bogName = backingBog.getFileName();
          if (bogName != null && !bogName.isEmpty())
          {
            int dot = bogName.lastIndexOf('.');
            return sanitizeFileStem(dot > 0 ? bogName.substring(0, dot) : bogName);
          }
        }
        BComponentSpace space = comp.getComponentSpace();
        if (space != null)
        {
          BComponent rootComp = space.getRootComponent();
          if (rootComp != null && rootComp.get("stationName") != null)
          {
            String stName = rootComp.get("stationName").toString();
            if (stName != null && !stName.trim().isEmpty())
            {
              return sanitizeFileStem(stName.trim());
            }
          }
          String navName = space.getNavName();
          if (navName != null && !navName.trim().isEmpty() && !"Station".equalsIgnoreCase(navName.trim()))
          {
            return sanitizeFileStem(navName.trim());
          }
        }
      }
      BOrd activeOrd = shell.getActiveOrd();
      if (activeOrd != null)
      {
        String ordStr = activeOrd.toString();
        // Check for ip/host or station name in ORD
        int foxIdx = ordStr.indexOf("fox:");
        if (foxIdx >= 0)
        {
          String sub = ordStr.substring(foxIdx);
          int pipe = sub.indexOf('|');
          if (pipe > 0) sub = sub.substring(0, pipe);
          return sanitizeFileStem(sub.replace("fox:", "").replace("/", "_"));
        }
      }
    }
    catch (Throwable ignore)
    {
    }
    return "ActiveStation";
  }

  public static String readGlobalRules()
  {
    return readUtf8File(getGlobalRulesFile());
  }

  public static void writeGlobalRules(String content)
  {
    writeUtf8File(getGlobalRulesFile(), content != null ? content : "");
  }

  public static String readStationMemory(String stationKey)
  {
    File f = getStationMemoryFile(stationKey);
    if (!f.exists())
    {
      return "# Station Memory & Notes: " + (stationKey != null ? stationKey : "ActiveStation") + "\n"
        + "# Learned architecture notes, point conventions, and equipment context for this station.\n\n";
    }
    return readUtf8File(f);
  }

  public static void writeStationMemory(String stationKey, String content)
  {
    writeUtf8File(getStationMemoryFile(stationKey), content != null ? content : "");
  }

  /**
   * Appends a new global rule to {@code rules/global_rules.md} if not already present.
   */
  public static synchronized String appendGlobalRule(String ruleText, String category)
  {
    if (ruleText == null || ruleText.trim().isEmpty()) return "";
    String cleanRule = ruleText.trim();
    File f = getGlobalRulesFile();
    String existing = readUtf8File(f);
    if (existing.isEmpty())
    {
      existing = DEFAULT_GLOBAL_RULES_HEADER;
    }

    if (existing.toLowerCase().contains(cleanRule.toLowerCase()))
    {
      return "Rule already in global_rules.md: " + cleanRule;
    }

    String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    String catPrefix = (category != null && !category.trim().isEmpty())
      ? "**[" + category.trim() + "]** "
      : "";
    String line = "- " + catPrefix + cleanRule + " *(learned " + dateStr + ")*\n";

    StringBuilder updated = new StringBuilder(existing);
    if (!existing.endsWith("\n")) updated.append("\n");
    updated.append(line);
    writeUtf8File(f, updated.toString());
    appendAuditLog("Global Rule", cleanRule, f.getAbsolutePath());
    return "Saved Global Rule to " + f.getName() + ": " + cleanRule;
  }

  /**
   * Appends a station-specific memory or note to {@code stations/<stationKey>.md}.
   */
  public static synchronized String appendStationMemory(String stationKey, String memoryText)
  {
    if (memoryText == null || memoryText.trim().isEmpty()) return "";
    String cleanMem = memoryText.trim();
    String key = (stationKey != null && !stationKey.trim().isEmpty()) ? stationKey.trim() : "ActiveStation";
    File f = getStationMemoryFile(key);
    String existing = f.exists() ? readUtf8File(f) : readStationMemory(key);

    if (existing.toLowerCase().contains(cleanMem.toLowerCase()))
    {
      return "Station memory already recorded in " + f.getName() + ": " + cleanMem;
    }

    String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    String line = "- " + cleanMem + " *(learned " + dateStr + ")*\n";

    StringBuilder updated = new StringBuilder(existing);
    if (!existing.endsWith("\n")) updated.append("\n");
    updated.append(line);
    writeUtf8File(f, updated.toString());
    appendAuditLog("Station Memory (" + key + ")", cleanMem, f.getAbsolutePath());
    return "Saved Station Memory to stations/" + f.getName() + ": " + cleanMem;
  }

  /**
   * Saves or updates a reusable skill/template file in {@code skills/<name>.md}.
   */
  public static synchronized String saveSkill(String name, String description, String content)
  {
    String stem = sanitizeFileStem(name);
    if (stem.isEmpty())
    {
      stem = "skill_" + System.currentTimeMillis();
    }
    File f = new File(getSkillsDir(), stem + ".md");
    String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
    StringBuilder sb = new StringBuilder();
    sb.append("# Skill: ").append(name != null && !name.trim().isEmpty() ? name.trim() : stem).append("\n");
    if (description != null && !description.trim().isEmpty())
    {
      sb.append("**Description:** ").append(description.trim()).append("\n");
    }
    sb.append("**Updated:** ").append(dateStr).append("\n\n");
    if (content != null && !content.trim().isEmpty())
    {
      sb.append(content.trim()).append("\n");
    }
    writeUtf8File(f, sb.toString());
    appendAuditLog("Skill (" + stem + ")", description != null ? description : name, f.getAbsolutePath());
    return "Saved reusable skill to skills/" + f.getName();
  }

  private static void appendAuditLog(String kind, String summary, String targetPath)
  {
    try
    {
      File brainDir = ensureInitialized();
      File logFile = new File(new File(brainDir, "chats"), "learned_log.md");
      String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
      String existing = logFile.exists() ? readUtf8File(logFile) : "# AI Learning Audit Log\n\n";
      StringBuilder sb = new StringBuilder(existing);
      if (!existing.endsWith("\n")) sb.append("\n");
      sb.append("- **[").append(ts).append("] ").append(kind).append(":** ")
        .append(summary != null ? summary.replace('\n', ' ') : "")
        .append(" (`").append(targetPath).append("`)\n");
      writeUtf8File(logFile, sb.toString());
    }
    catch (Throwable ignore)
    {
    }
  }

  /**
   * Extracts {@code <ai-learn>[ ... ]</ai-learn>} items from an AI response string.
   */
  public static List<Map<String, String>> extractLearnRequests(String responseText)
  {
    List<Map<String, String>> results = new ArrayList<Map<String, String>>();
    if (responseText == null || responseText.trim().isEmpty())
    {
      return results;
    }

    int tagStart = responseText.indexOf("<ai-learn>");
    int tagEnd = responseText.indexOf("</ai-learn>");
    if (tagStart >= 0 && tagEnd > tagStart)
    {
      String inside = responseText.substring(tagStart + "<ai-learn>".length(), tagEnd).trim();
      List<Map<String, String>> rawList = StationActionExecutor.parseActionPlanLenient(inside);
      for (int i = 0; i < rawList.size(); i++)
      {
        Map<String, String> item = rawList.get(i);
        if (!item.isEmpty())
        {
          results.add(item);
        }
      }
    }
    return results;
  }

  /**
   * Strips {@code <ai-learn>...</ai-learn>} tags from display text so chat bubbles stay clean.
   */
  public static String stripLearnTagsForDisplay(String responseText)
  {
    if (responseText == null) return "";
    String text = responseText;
    while (true)
    {
      int tagStart = text.indexOf("<ai-learn>");
      int tagEnd = text.indexOf("</ai-learn>");
      if (tagStart >= 0 && tagEnd > tagStart)
      {
        String before = text.substring(0, tagStart).trim();
        String after = text.substring(tagEnd + "</ai-learn>".length()).trim();
        text = (before + "\n" + after).trim();
      }
      else
      {
        break;
      }
    }
    return text.trim();
  }

  /**
   * Executes a list of parsed learning requests and returns a multi-line summary of what was saved.
   */
  public static String executeLearnRequests(BWbShell shell, List<Map<String, String>> learnItems)
  {
    if (learnItems == null || learnItems.isEmpty()) return "";
    String activeStationKey = resolveActiveStationKey(shell);
    StringBuilder log = new StringBuilder();
    int savedCount = 0;

    for (int i = 0; i < learnItems.size(); i++)
    {
      Map<String, String> item = learnItems.get(i);
      String op = firstNonEmpty(item.get("learn"), item.get("action"), item.get("type"), "saveRule");
      String scope = firstNonEmpty(item.get("scope"), "global");

      if ("saveStationMemory".equalsIgnoreCase(op)
        || "addStationNote".equalsIgnoreCase(op)
        || "station".equalsIgnoreCase(scope))
      {
        String stationName = firstNonEmpty(item.get("station"), activeStationKey);
        if ("activeStation".equalsIgnoreCase(stationName) || "current".equalsIgnoreCase(stationName))
        {
          stationName = activeStationKey;
        }
        String mem = firstNonEmpty(item.get("memory"), item.get("note"), item.get("rule"), item.get("content"), item.get("text"));
        if (!mem.isEmpty())
        {
          String msg = appendStationMemory(stationName, mem);
          if (!msg.isEmpty())
          {
            if (savedCount > 0) log.append("\n");
            log.append("• ").append(msg);
            savedCount++;
          }
        }
      }
      else if ("saveSkill".equalsIgnoreCase(op) || "saveTemplate".equalsIgnoreCase(op) || "skill".equalsIgnoreCase(scope))
      {
        String name = firstNonEmpty(item.get("name"), item.get("title"), "learned_skill");
        String desc = firstNonEmpty(item.get("description"), item.get("summary"));
        String content = firstNonEmpty(item.get("content"), item.get("template"), item.get("rule"), item.get("code"));
        if (!content.isEmpty() || !desc.isEmpty())
        {
          String msg = saveSkill(name, desc, content);
          if (savedCount > 0) log.append("\n");
          log.append("• ").append(msg);
          savedCount++;
        }
      }
      else
      {
        String rule = firstNonEmpty(item.get("rule"), item.get("content"), item.get("text"), item.get("memory"), item.get("note"));
        String category = firstNonEmpty(item.get("category"), item.get("topic"));
        if (!rule.isEmpty())
        {
          String msg = appendGlobalRule(rule, category);
          if (!msg.isEmpty())
          {
            if (savedCount > 0) log.append("\n");
            log.append("• ").append(msg);
            savedCount++;
          }
        }
      }
    }

    if (savedCount > 0)
    {
      log.append("\n\nBrain Workspace: ").append(EddiesAiConfig.get().getAiBrainDirPath());
    }
    return log.toString();
  }

  /**
   * Builds the persistent AI Brain & Reference Knowledge prompt block to inject into every AI turn.
   */
  public static String buildBrainPromptBlock(String activeStationKey)
  {
    File brainDir = ensureInitialized();
    EddiesAiConfig cfg = EddiesAiConfig.get();
    StringBuilder sb = new StringBuilder();

    sb.append("=== USER'S PERSISTENT AI BRAIN & LEARNED KNOWLEDGE ===\n");
    sb.append("AI Brain Workspace Path: ").append(brainDir.getAbsolutePath()).append("\n");

    // 1. Global Rules
    File globalRulesFile = getGlobalRulesFile();
    if (globalRulesFile.exists())
    {
      String rules = readUtf8File(globalRulesFile).trim();
      String stripped = stripCommentLines(rules);
      if (!stripped.isEmpty())
      {
        sb.append("\n--- GLOBAL USER RULES (MUST FOLLOW) ---\n");
        sb.append(truncateText(stripped, 6000)).append("\n");
      }
    }

    // 2. Active Station Memory
    String stKey = (activeStationKey != null && !activeStationKey.trim().isEmpty())
      ? activeStationKey.trim()
      : "ActiveStation";
    File stFile = getStationMemoryFile(stKey);
    if (stFile.exists())
    {
      String stMem = stripCommentLines(readUtf8File(stFile).trim());
      if (!stMem.isEmpty())
      {
        sb.append("\n--- LEARNED MEMORY FOR STATION [").append(stKey).append("] ---\n");
        sb.append(truncateText(stMem, 4000)).append("\n");
      }
    }

    // 3. Learned Skills Index
    File skillsDir = getSkillsDir();
    File[] skillFiles = skillsDir.listFiles();
    if (skillFiles != null && skillFiles.length > 0)
    {
      sb.append("\n--- SAVED SKILLS & TEMPLATES IN BRAIN (Read via <station-inspect> readFile if relevant) ---\n");
      int count = 0;
      for (int i = 0; i < skillFiles.length && count < 40; i++)
      {
        File sf = skillFiles[i];
        if (sf.isFile())
        {
          sb.append("  * ").append(sf.getName()).append(" -> ").append(sf.getAbsolutePath());
          String firstSummary = extractFirstDescriptionLine(sf);
          if (!firstSummary.isEmpty())
          {
            sb.append(" (").append(firstSummary).append(")");
          }
          sb.append("\n");
          count++;
        }
      }
    }

    // 4. User-Granted Local Reference & Knowledge Folders
    List<String> refFolders = cfg.getReferenceFolders();
    if (refFolders != null && !refFolders.isEmpty())
    {
      sb.append("\n--- USER-GRANTED LOCAL NIAGARA REFERENCE FOLDERS (Accessible via <station-inspect> searchKnowledge, listFiles, readFile) ---\n");
      for (int i = 0; i < refFolders.size(); i++)
      {
        String folderPath = refFolders.get(i);
        File dir = new File(folderPath);
        if (dir.exists() && dir.isDirectory())
        {
          sb.append("  [Reference Folder ").append(i + 1).append("]: ").append(dir.getAbsolutePath()).append("\n");
          appendCompactDirectoryPreview(dir, sb, 45);
        }
        else
        {
          sb.append("  [Reference Folder ").append(i + 1).append(" (Not Found)]: ").append(folderPath).append("\n");
        }
      }
    }

    sb.append("=== END AI BRAIN & LEARNED KNOWLEDGE ===\n");
    return sb.toString();
  }

  private static void appendCompactDirectoryPreview(File rootDir, StringBuilder sb, int maxEntries)
  {
    File[] children = rootDir.listFiles();
    if (children == null || children.length == 0) return;
    Arrays.sort(children, new Comparator<File>()
    {
      @Override
      public int compare(File a, File b)
      {
        if (a.isDirectory() != b.isDirectory())
        {
          return a.isDirectory() ? -1 : 1;
        }
        return a.getName().compareToIgnoreCase(b.getName());
      }
    });

    int shown = 0;
    for (int i = 0; i < children.length && shown < maxEntries; i++)
    {
      File ch = children[i];
      String name = ch.getName();
      if (name.startsWith(".")) continue;
      if (ch.isDirectory())
      {
        sb.append("    - [DIR]  ").append(name).append("/");
        File[] subKids = ch.listFiles();
        if (subKids != null && subKids.length > 0)
        {
          sb.append(" (");
          int sc = 0;
          for (int j = 0; j < subKids.length && sc < 6; j++)
          {
            if (subKids[j].getName().startsWith(".")) continue;
            if (sc > 0) sb.append(", ");
            sb.append(subKids[j].getName());
            sc++;
          }
          if (subKids.length > 6) sb.append(", ...");
          sb.append(")");
        }
        sb.append("\n");
        shown++;
      }
      else
      {
        sb.append("    - [FILE] ").append(name).append("\n");
        shown++;
      }
    }
    if (children.length > shown)
    {
      sb.append("    ... (use <station-inspect> listFiles or searchKnowledge to explore more)\n");
    }
  }

  /**
   * Searches across the AI Brain workspace and all user-granted Reference Folders for matching
   * file names or text contents. Called by {@link StationInspector} for {@code searchKnowledge}.
   */
  public static void searchBrainAndReferenceFolders(String query, StringBuilder sb)
  {
    if (query == null || query.trim().isEmpty())
    {
      sb.append("Empty search query.\n");
      return;
    }
    String qTrim = query.trim();
    String[] terms = qTrim.toLowerCase().split("\\s+");

    List<File> searchRoots = new ArrayList<File>();
    File brainDir = ensureInitialized();
    if (brainDir.isDirectory())
    {
      searchRoots.add(brainDir);
    }

    List<String> refFolders = EddiesAiConfig.get().getReferenceFolders();
    for (int i = 0; i < refFolders.size(); i++)
    {
      File rf = new File(refFolders.get(i));
      if (rf.isDirectory())
      {
        searchRoots.add(rf);
      }
    }

    sb.append("Knowledge Search Query: \"").append(qTrim).append("\"\n");
    sb.append("Searched Roots (").append(searchRoots.size()).append("):\n");
    for (int i = 0; i < searchRoots.size(); i++)
    {
      sb.append("  - ").append(searchRoots.get(i).getAbsolutePath()).append("\n");
    }

    int[] matchCounter = new int[] { 0 };
    for (int i = 0; i < searchRoots.size(); i++)
    {
      searchDirectoryRecursive(searchRoots.get(i), terms, 0, 5, matchCounter, sb);
      if (matchCounter[0] >= 60) break;
    }

    if (matchCounter[0] == 0)
    {
      sb.append("No matching files or content snippets found for \"").append(qTrim).append("\".\n");
    }
    else
    {
      sb.append("Total matches shown: ").append(matchCounter[0])
        .append(" (use readFile with the full path above to read any file in detail).\n");
    }
  }

  private static void searchDirectoryRecursive(
    File dir,
    String[] terms,
    int depth,
    int maxDepth,
    int[] matchCounter,
    StringBuilder sb)
  {
    if (dir == null || !dir.isDirectory() || depth > maxDepth || matchCounter[0] >= 60)
    {
      return;
    }
    File[] files = dir.listFiles();
    if (files == null) return;

    for (int i = 0; i < files.length && matchCounter[0] < 60; i++)
    {
      File f = files[i];
      String name = f.getName();
      if (name.startsWith(".")) continue;

      if (f.isDirectory())
      {
        if (matchesAllTerms(name.toLowerCase(), terms))
        {
          matchCounter[0]++;
          sb.append("  [DIR MATCH] ").append(f.getAbsolutePath()).append("\n");
        }
        searchDirectoryRecursive(f, terms, depth + 1, maxDepth, matchCounter, sb);
      }
      else
      {
        String nameLower = name.toLowerCase();
        boolean nameMatched = matchesAllTerms(nameLower, terms);
        if (nameMatched)
        {
          matchCounter[0]++;
          sb.append("  [FILE NAME MATCH] ").append(f.getAbsolutePath())
            .append(" (").append(f.length()).append(" bytes)\n");
        }

        if (isSearchableTextFile(nameLower) && f.length() > 0L && f.length() <= 512 * 1024L)
        {
          try
          {
            String text = readUtf8File(f);
            String textLower = text.toLowerCase();
            if (matchesAllTerms(textLower, terms))
            {
              if (!nameMatched)
              {
                matchCounter[0]++;
                sb.append("  [CONTENT MATCH] ").append(f.getAbsolutePath()).append("\n");
              }
              String[] lines = text.split("\n", -1);
              int snippetsForFile = 0;
              for (int ln = 0; ln < lines.length && snippetsForFile < 4; ln++)
              {
                String line = lines[ln].trim();
                if (line.isEmpty()) continue;
                String lineLower = line.toLowerCase();
                boolean anyTermOnLine = false;
                for (int t = 0; t < terms.length; t++)
                {
                  if (!terms[t].isEmpty() && lineLower.contains(terms[t]))
                  {
                    anyTermOnLine = true;
                    break;
                  }
                }
                if (anyTermOnLine)
                {
                  if (line.length() > 200) line = line.substring(0, 200) + "...";
                  sb.append("      Line ").append(ln + 1).append(": ").append(line).append("\n");
                  snippetsForFile++;
                }
              }
            }
          }
          catch (Throwable ignore)
          {
          }
        }
      }
    }
  }

  private static boolean matchesAllTerms(String haystackLower, String[] terms)
  {
    if (haystackLower == null || terms == null || terms.length == 0) return false;
    for (int i = 0; i < terms.length; i++)
    {
      if (!terms[i].isEmpty() && !haystackLower.contains(terms[i]))
      {
        return false;
      }
    }
    return true;
  }

  private static boolean isSearchableTextFile(String nameLower)
  {
    return nameLower.endsWith(".md")
      || nameLower.endsWith(".txt")
      || nameLower.endsWith(".px")
      || nameLower.endsWith(".bog")
      || nameLower.endsWith(".java")
      || nameLower.endsWith(".xml")
      || nameLower.endsWith(".json")
      || nameLower.endsWith(".csv")
      || nameLower.endsWith(".properties")
      || nameLower.endsWith(".lexicon")
      || nameLower.endsWith(".html")
      || nameLower.endsWith(".htm")
      || nameLower.endsWith(".kts")
      || nameLower.endsWith(".gradle");
  }

  public static void openFolderInExplorer(File folder)
  {
    if (folder == null) return;
    try
    {
      if (!folder.exists())
      {
        folder.mkdirs();
      }
      if (Desktop.isDesktopSupported())
      {
        Desktop.getDesktop().open(folder);
        return;
      }
    }
    catch (Throwable ignore)
    {
    }
    try
    {
      Runtime.getRuntime().exec(new String[] { "explorer.exe", folder.getAbsolutePath() });
    }
    catch (Throwable ignore)
    {
    }
  }

  private static String extractFirstDescriptionLine(File f)
  {
    try
    {
      String content = readUtf8File(f);
      String[] lines = content.split("\n");
      for (int i = 0; i < lines.length && i < 10; i++)
      {
        String l = lines[i].trim();
        if (l.startsWith("**Description:**"))
        {
          return l.substring("**Description:**".length()).trim();
        }
        if (!l.isEmpty() && !l.startsWith("#") && !l.startsWith("**Updated:**"))
        {
          return l.length() > 90 ? l.substring(0, 90) + "..." : l;
        }
      }
    }
    catch (Throwable ignore)
    {
    }
    return "";
  }

  private static String stripCommentLines(String md)
  {
    if (md == null || md.isEmpty()) return "";
    String[] lines = md.split("\n");
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < lines.length; i++)
    {
      String raw = lines[i];
      String t = raw.trim();
      if (t.startsWith("# The AI Assistant reads")
        || t.startsWith("# when you give it feedback")
        || t.startsWith("# Learned architecture notes")
        || t.startsWith("# Eddie's Workbench AI"))
      {
        continue;
      }
      if (!t.isEmpty())
      {
        out.append(raw).append("\n");
      }
    }
    return out.toString().trim();
  }

  private static String truncateText(String s, int maxChars)
  {
    if (s == null) return "";
    if (s.length() <= maxChars) return s;
    return s.substring(0, maxChars) + "\n... [truncated]";
  }

  public static String readUtf8File(File f)
  {
    if (f == null || !f.exists() || !f.isFile()) return "";
    FileInputStream fis = null;
    try
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream((int) Math.max(256L, f.length()));
      fis = new FileInputStream(f);
      byte[] buf = new byte[4096];
      int n;
      while ((n = fis.read(buf)) >= 0)
      {
        baos.write(buf, 0, n);
      }
      return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
    catch (Throwable t)
    {
      return "";
    }
    finally
    {
      if (fis != null)
      {
        try { fis.close(); } catch (Throwable ignore) {}
      }
    }
  }

  public static boolean writeUtf8File(File f, String content)
  {
    if (f == null) return false;
    FileOutputStream fos = null;
    try
    {
      File parent = f.getParentFile();
      if (parent != null && !parent.exists())
      {
        parent.mkdirs();
      }
      fos = new FileOutputStream(f);
      byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
      fos.write(bytes);
      fos.flush();
      return true;
    }
    catch (Throwable t)
    {
      return false;
    }
    finally
    {
      if (fos != null)
      {
        try { fos.close(); } catch (Throwable ignore) {}
      }
    }
  }

  public static String sanitizeFileStem(String raw)
  {
    if (raw == null) return "";
    String s = raw.trim();
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if ((c >= 'a' && c <= 'z')
        || (c >= 'A' && c <= 'Z')
        || (c >= '0' && c <= '9')
        || c == '_'
        || c == '-')
      {
        sb.append(c);
      }
      else if (c == ' ' || c == '.' || c == ':' || c == '/' || c == '\\')
      {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_')
        {
          sb.append('_');
        }
      }
    }
    String res = sb.toString();
    while (res.endsWith("_"))
    {
      res = res.substring(0, res.length() - 1);
    }
    return res;
  }

  private static String firstNonEmpty(String... vals)
  {
    if (vals == null) return "";
    for (int i = 0; i < vals.length; i++)
    {
      if (vals[i] != null && !vals[i].trim().isEmpty())
      {
        return vals[i].trim();
      }
    }
    return "";
  }
}
