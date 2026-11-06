/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BFont;
import javax.baja.gx.BInsets;
import javax.baja.sys.Sys;
import javax.baja.ui.BBorder;
import javax.baja.ui.BButton;
import javax.baja.ui.BCheckBox;
import javax.baja.ui.BDialog;
import javax.baja.ui.BLabel;
import javax.baja.ui.BTextField;
import javax.baja.ui.BWidget;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.enums.BHalign;
import javax.baja.ui.enums.BValign;
import javax.baja.ui.event.BWidgetEvent;
import javax.baja.ui.list.BList;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.ui.pane.BScrollPane;
import javax.baja.ui.text.BTextEditor;
import javax.baja.workbench.BWbShell;

/**
 * Modal dialog for configuring and managing Eddie's AI Assistant Brain Workspace:
 * <ol>
 *   <li>AI Brain Workspace Location (defaults to {@code <NiagaraUserHome>/eddiesWorkbenchAI}, or any user-chosen folder).</li>
 *   <li>User-Granted Local Niagara Reference &amp; Knowledge Folders on the user's machine.</li>
 *   <li>Direct viewing and editing of learned {@code global_rules.md} and per-station {@code stations/<Station>.md} memory files.</li>
 * </ol>
 */
public final class EddiesAiBrainDialog
{
  private EddiesAiBrainDialog()
  {
  }

  public static void open(
    final BWidget owner,
    final BWbShell shell,
    final Runnable onSavedCallback)
  {
    final EddiesAiConfig cfg = EddiesAiConfig.get();
    cfg.load();
    EddiesAiBrainManager.ensureInitialized();

    final String activeStationKey = EddiesAiBrainManager.resolveActiveStationKey(shell);

    BFont headerFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont labelFont = BFont.make("Tahoma", 11.0);
    BFont smallFont = BFont.make("Tahoma", 10.0);

    final BLabel statusBanner = new BLabel(
      "AI Brain active at: " + cfg.getAiBrainDirPath(),
      smallFont);
    statusBanner.setForeground(BBrush.makeSolid(BColor.make(25, 95, 65)));

    // ============================================================================
    // Section 1: AI Brain Workspace Location (Default in User Home or Custom Path)
    // ============================================================================
    BLabel sec1Title = new BLabel(
      "1. AI Brain Workspace Location (Stores Learned Rules, Station Memories & Skills)",
      headerFont);
    sec1Title.setForeground(BBrush.makeSolid(BColor.make(20, 65, 130)));

    BLabel sec1Sub = new BLabel(
      "Default location is in your Niagara User Home (" + EddiesAiConfig.getDefaultAiBrainDirPath() + "), or you can choose any folder on your computer.",
      smallFont);
    sec1Sub.setForeground(BBrush.makeSolid(BColor.make(80, 90, 105)));

    final BTextField brainDirField = new BTextField(cfg.getAiBrainDirPath(), 46, true);
    final BCheckBox autoLearnCheck = new BCheckBox(
      "Automatically learn & save rules/station notes when I give feedback or corrections in chat",
      cfg.isAutoLearnEnabled());

    Command browseBrainDirCmd = new Command(owner, "Choose Folder...")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        File initial = new File(brainDirField.getText().trim());
        File chosen = promptSelectLocalDirectory(
          owner,
          "Choose AI Brain Workspace Folder",
          initial.exists() ? initial : cfg.getAiBrainDir());
        if (chosen != null)
        {
          brainDirField.setText(chosen.getAbsolutePath());
          cfg.setAiBrainDirPath(chosen.getAbsolutePath());
          cfg.save();
          EddiesAiBrainManager.ensureInitialized();
          statusBanner.setText("Updated AI Brain Workspace folder: " + chosen.getAbsolutePath());
          statusBanner.relayout();
          statusBanner.repaint();
        }
        return null;
      }
    };
    BButton browseBrainBtn = new BButton(browseBrainDirCmd, true, false);

    Command defaultBrainDirCmd = new Command(owner, "Reset to Default (User Home)")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        String defPath = EddiesAiConfig.getDefaultAiBrainDirPath();
        brainDirField.setText(defPath);
        cfg.setAiBrainDirPath("");
        cfg.save();
        EddiesAiBrainManager.ensureInitialized();
        statusBanner.setText("Reset AI Brain Workspace to default: " + defPath);
        statusBanner.relayout();
        statusBanner.repaint();
        return null;
      }
    };
    BButton defaultBrainBtn = new BButton(defaultBrainDirCmd, true, false);

    Command openBrainExplorerCmd = new Command(owner, "Open Brain Folder")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        String path = brainDirField.getText().trim();
        if (!path.isEmpty())
        {
          cfg.setAiBrainDirPath(path);
          cfg.save();
        }
        File dir = EddiesAiBrainManager.ensureInitialized();
        EddiesAiBrainManager.openFolderInExplorer(dir);
        statusBanner.setText("Opened AI Brain folder in Windows Explorer: " + dir.getAbsolutePath());
        statusBanner.relayout();
        statusBanner.repaint();
        return null;
      }
    };
    BButton openBrainExplorerBtn = new BButton(openBrainExplorerCmd, true, false);

    BGridPane brainPathRow = new BGridPane(2);
    brainPathRow.setColumnGap(8.0);
    brainPathRow.setStretchColumn(1);
    brainPathRow.setColumnAlign(BHalign.left);
    brainPathRow.add("bpl", new BLabel("Brain Folder:", labelFont));
    brainPathRow.add("bpf", brainDirField);

    BGridPane brainBtnsRow = new BGridPane(3);
    brainBtnsRow.setColumnGap(6.0);
    brainBtnsRow.add("bb1", browseBrainBtn);
    brainBtnsRow.add("bb2", defaultBrainBtn);
    brainBtnsRow.add("bb3", openBrainExplorerBtn);

    BGridPane sec1Stack = new BGridPane(1);
    sec1Stack.setRowGap(6.0);
    sec1Stack.setStretchColumn(0);
    sec1Stack.setColumnAlign(BHalign.left);
    sec1Stack.add("s1t", sec1Title);
    sec1Stack.add("s1s", sec1Sub);
    sec1Stack.add("s1p", brainPathRow);
    sec1Stack.add("s1b", brainBtnsRow);
    sec1Stack.add("s1c", autoLearnCheck);

    BBorderPane sec1Box = new BBorderPane(sec1Stack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    sec1Box.setFill(BBrush.makeSolid(BColor.make(244, 248, 253)));
    sec1Box.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(198, 212, 232))));

    // ============================================================================
    // Section 2: User-Granted Local Niagara Reference & Knowledge Folders
    // ============================================================================
    BLabel sec2Title = new BLabel(
      "2. Local Niagara Reference & Knowledge Folders (Granted Read & Search Access)",
      headerFont);
    sec2Title.setForeground(BBrush.makeSolid(BColor.make(20, 90, 55)));

    BLabel sec2Sub = new BLabel(
      "Point the assistant to folders on your machine containing Niagara docs, PX graphics, BOG templates, or controller references:",
      smallFont);
    sec2Sub.setForeground(BBrush.makeSolid(BColor.make(80, 90, 105)));

    final List<String> workingRefFolders = new ArrayList<String>(cfg.getReferenceFolders());
    final BList refFolderListWidget = new BList();
    refreshRefListWidget(refFolderListWidget, workingRefFolders);

    BScrollPane refScroll = new BScrollPane(refFolderListWidget);
    refScroll.setPreferredSize(540.0, 95.0);
    BBorderPane refListBorder = new BBorderPane(refScroll, BInsets.make(2.0));
    refListBorder.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(185, 205, 195))));

    Command addRefFolderCmd = new Command(owner, "+ Add Reference Folder...")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        File startDir = new File("C:\\");
        if (!workingRefFolders.isEmpty())
        {
          File last = new File(workingRefFolders.get(workingRefFolders.size() - 1));
          if (last.getParentFile() != null && last.getParentFile().isDirectory())
          {
            startDir = last.getParentFile();
          }
          else if (last.isDirectory())
          {
            startDir = last;
          }
        }
        File chosen = promptSelectLocalDirectory(
          owner,
          "Add Local Niagara Reference / Knowledge Folder",
          startDir);
        if (chosen != null)
        {
          String abs = chosen.getAbsolutePath();
          boolean exists = false;
          for (int i = 0; i < workingRefFolders.size(); i++)
          {
            if (workingRefFolders.get(i).equalsIgnoreCase(abs))
            {
              exists = true;
              break;
            }
          }
          if (!exists)
          {
            workingRefFolders.add(abs);
            cfg.setReferenceFolders(workingRefFolders);
            cfg.save();
            refreshRefListWidget(refFolderListWidget, workingRefFolders);
            statusBanner.setText("Granted AI read & search access to folder: " + abs);
            statusBanner.relayout();
            statusBanner.repaint();
          }
        }
        return null;
      }
    };
    BButton addRefFolderBtn = new BButton(addRefFolderCmd, true, false);

    Command removeRefFolderCmd = new Command(owner, "Remove Selected")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        Object sel = refFolderListWidget.getSelectedItem();
        if (sel == null || workingRefFolders.isEmpty())
        {
          statusBanner.setText("Select a reference folder in the list to remove.");
          statusBanner.relayout();
          statusBanner.repaint();
          return null;
        }
        String selStr = sel.toString();
        for (int i = workingRefFolders.size() - 1; i >= 0; i--)
        {
          if (workingRefFolders.get(i).equalsIgnoreCase(selStr))
          {
            workingRefFolders.remove(i);
          }
        }
        cfg.setReferenceFolders(workingRefFolders);
        cfg.save();
        refreshRefListWidget(refFolderListWidget, workingRefFolders);
        statusBanner.setText("Removed folder from AI reference list: " + selStr);
        statusBanner.relayout();
        statusBanner.repaint();
        return null;
      }
    };
    BButton removeRefFolderBtn = new BButton(removeRefFolderCmd, true, false);

    Command openRefFolderCmd = new Command(owner, "Open Selected Folder")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        Object sel = refFolderListWidget.getSelectedItem();
        if (sel != null && !workingRefFolders.isEmpty())
        {
          File f = new File(sel.toString());
          if (f.isDirectory())
          {
            EddiesAiBrainManager.openFolderInExplorer(f);
          }
        }
        return null;
      }
    };
    BButton openRefFolderBtn = new BButton(openRefFolderCmd, true, false);

    BGridPane refBtnsRow = new BGridPane(3);
    refBtnsRow.setColumnGap(6.0);
    refBtnsRow.add("rb1", addRefFolderBtn);
    refBtnsRow.add("rb2", removeRefFolderBtn);
    refBtnsRow.add("rb3", openRefFolderBtn);

    BGridPane sec2Stack = new BGridPane(1);
    sec2Stack.setRowGap(6.0);
    sec2Stack.setStretchColumn(0);
    sec2Stack.setColumnAlign(BHalign.left);
    sec2Stack.add("s2t", sec2Title);
    sec2Stack.add("s2s", sec2Sub);
    sec2Stack.add("s2l", refListBorder);
    sec2Stack.add("s2b", refBtnsRow);

    BBorderPane sec2Box = new BBorderPane(sec2Stack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    sec2Box.setFill(BBrush.makeSolid(BColor.make(243, 250, 246)));
    sec2Box.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(180, 215, 195))));

    // ============================================================================
    // Section 3: Learned Rules, Station Memory & Skills Editor
    // ============================================================================
    BLabel sec3Title = new BLabel(
      "3. Learned Rules & Active Station Memory (" + activeStationKey + ")",
      headerFont);
    sec3Title.setForeground(BBrush.makeSolid(BColor.make(95, 50, 125)));

    final BTextField quickRuleField = new BTextField("", 38, true);
    Command addQuickRuleCmd = new Command(owner, "+ Save Rule Now")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        String text = quickRuleField.getText();
        if (text == null || text.trim().isEmpty())
        {
          statusBanner.setText("Type a rule in the field first, then click '+ Save Rule Now'.");
          statusBanner.relayout();
          statusBanner.repaint();
          return null;
        }
        String res = EddiesAiBrainManager.appendGlobalRule(text.trim(), "User Rule");
        quickRuleField.setText("");
        statusBanner.setText(res);
        statusBanner.relayout();
        statusBanner.repaint();
        return null;
      }
    };
    BButton addQuickRuleBtn = new BButton(addQuickRuleCmd, true, false);

    BGridPane quickRuleRow = new BGridPane(3);
    quickRuleRow.setColumnGap(6.0);
    quickRuleRow.setStretchColumn(1);
    quickRuleRow.setColumnAlign(BHalign.left);
    quickRuleRow.add("qrl", new BLabel("Quick Add Rule:", labelFont));
    quickRuleRow.add("qrf", quickRuleField);
    quickRuleRow.add("qrb", addQuickRuleBtn);

    Command editGlobalRulesCmd = new Command(owner, "View / Edit Global Rules (global_rules.md)...")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        File f = EddiesAiBrainManager.getGlobalRulesFile();
        openMarkdownFileEditor(
          owner,
          "Edit Global User Rules (" + f.getAbsolutePath() + ")",
          f,
          EddiesAiBrainManager.readGlobalRules(),
          statusBanner);
        return null;
      }
    };
    BButton editGlobalRulesBtn = new BButton(editGlobalRulesCmd, true, false);

    Command editStationMemCmd = new Command(owner, "View / Edit Station Memory (" + activeStationKey + ".md)...")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        File f = EddiesAiBrainManager.getStationMemoryFile(activeStationKey);
        openMarkdownFileEditor(
          owner,
          "Edit Station Memory (" + f.getAbsolutePath() + ")",
          f,
          EddiesAiBrainManager.readStationMemory(activeStationKey),
          statusBanner);
        return null;
      }
    };
    BButton editStationMemBtn = new BButton(editStationMemCmd, true, false);

    Command openSkillsFolderCmd = new Command(owner, "Open Skills Folder")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        EddiesAiBrainManager.openFolderInExplorer(EddiesAiBrainManager.getSkillsDir());
        return null;
      }
    };
    BButton openSkillsBtn = new BButton(openSkillsFolderCmd, true, false);

    BGridPane editFilesRow = new BGridPane(3);
    editFilesRow.setColumnGap(6.0);
    editFilesRow.add("ef1", editGlobalRulesBtn);
    editFilesRow.add("ef2", editStationMemBtn);
    editFilesRow.add("ef3", openSkillsBtn);

    BGridPane sec3Stack = new BGridPane(1);
    sec3Stack.setRowGap(6.0);
    sec3Stack.setStretchColumn(0);
    sec3Stack.setColumnAlign(BHalign.left);
    sec3Stack.add("s3t", sec3Title);
    sec3Stack.add("s3q", quickRuleRow);
    sec3Stack.add("s3e", editFilesRow);

    BBorderPane sec3Box = new BBorderPane(sec3Stack, BInsets.make(8.0, 10.0, 8.0, 10.0));
    sec3Box.setFill(BBrush.makeSolid(BColor.make(249, 245, 253)));
    sec3Box.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(210, 195, 228))));

    // ---- Main Layout & Footer ----
    BGridPane mainStack = new BGridPane(1);
    mainStack.setRowGap(10.0);
    mainStack.setStretchColumn(0);
    mainStack.setColumnAlign(BHalign.left);
    mainStack.add("sec1", sec1Box);
    mainStack.add("sec2", sec2Box);
    mainStack.add("sec3", sec3Box);
    mainStack.add("status", new BBorderPane(statusBanner, BInsets.make(2.0, 4.0, 2.0, 4.0)));

    final BDialog[] dlgRef = new BDialog[1];

    Command saveAndCloseCmd = new Command(owner, "Save & Close")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        String enteredBrainPath = brainDirField.getText().trim();
        if (enteredBrainPath.isEmpty()
          || enteredBrainPath.equalsIgnoreCase(EddiesAiConfig.getDefaultAiBrainDirPath()))
        {
          cfg.setAiBrainDirPath("");
        }
        else
        {
          cfg.setAiBrainDirPath(enteredBrainPath);
        }
        cfg.setAutoLearnEnabled(autoLearnCheck.isSelected());
        cfg.setReferenceFolders(workingRefFolders);

        String pendingQuickRule = quickRuleField.getText();
        if (pendingQuickRule != null && !pendingQuickRule.trim().isEmpty())
        {
          EddiesAiBrainManager.appendGlobalRule(pendingQuickRule.trim(), "User Rule");
        }

        cfg.save();
        EddiesAiBrainManager.ensureInitialized();

        if (onSavedCallback != null)
        {
          onSavedCallback.run();
        }
        if (dlgRef[0] != null)
        {
          dlgRef[0].close();
        }
        return null;
      }
    };
    BButton saveBtn = new BButton(saveAndCloseCmd, true, true);

    Command cancelCmd = new Command(owner, "Close")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (dlgRef[0] != null)
        {
          dlgRef[0].close();
        }
        return null;
      }
    };
    BButton cancelBtn = new BButton(cancelCmd, true, false);

    BGridPane footerBtns = new BGridPane(2);
    footerBtns.setColumnGap(8.0);
    footerBtns.add("save", saveBtn);
    footerBtns.add("cancel", cancelBtn);

    BEdgePane footerBar = new BEdgePane();
    footerBar.setRight(footerBtns);

    BEdgePane contentPane = new BEdgePane();
    contentPane.setCenter(mainStack);
    contentPane.setBottom(new BBorderPane(footerBar, BInsets.make(10.0, 0.0, 0.0, 0.0)));

    BBorderPane padded = new BBorderPane(contentPane, BInsets.make(12.0));
    BDialog dlg = new BDialog(
      owner,
      "Eddie's AI Assistant — AI Brain, Learned Rules & Local Reference Folders",
      true,
      padded);
    dlgRef[0] = dlg;
    dlg.setResizable(true);
    dlg.computePreferredSize();
    dlg.setBoundsCenteredOnOwner();
    dlg.open();
  }

  private static void refreshRefListWidget(BList listWidget, List<String> folders)
  {
    listWidget.removeAllItems();
    if (folders == null || folders.isEmpty())
    {
      listWidget.addItem("(No local reference folders added yet — click '+ Add Reference Folder...' below)");
      return;
    }
    for (int i = 0; i < folders.size(); i++)
    {
      listWidget.addItem(folders.get(i));
    }
    listWidget.setSelectedIndex(0);
  }

  /**
   * Opens a modal built-in Markdown text editor dialog for viewing and editing
   * {@code global_rules.md} or {@code stations/<Station>.md}.
   */
  private static void openMarkdownFileEditor(
    BWidget owner,
    String title,
    final File targetFile,
    String initialText,
    final BLabel parentStatusBanner)
  {
    final BTextEditor editor = new BTextEditor(initialText != null ? initialText : "", true);
    editor.setAllowCopying(true);

    BScrollPane scroll = new BScrollPane(editor);
    scroll.setPreferredSize(620.0, 340.0);
    BBorderPane border = new BBorderPane(scroll, BInsets.make(2.0));
    border.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(175, 190, 210))));

    BLabel hint = new BLabel(
      "File: " + targetFile.getAbsolutePath() + " (Changes are saved directly to disk and injected into every AI prompt)",
      BFont.make("Tahoma", 10.0));
    hint.setForeground(BBrush.makeSolid(BColor.make(70, 85, 105)));

    final BDialog[] editorDlgRef = new BDialog[1];

    Command saveFileCmd = new Command(owner, "Save File")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        EddiesAiBrainManager.writeUtf8File(targetFile, editor.getText());
        if (parentStatusBanner != null)
        {
          parentStatusBanner.setText("Saved changes to " + targetFile.getAbsolutePath());
          parentStatusBanner.relayout();
          parentStatusBanner.repaint();
        }
        if (editorDlgRef[0] != null)
        {
          editorDlgRef[0].close();
        }
        return null;
      }
    };
    BButton saveFileBtn = new BButton(saveFileCmd, true, true);

    Command cancelEditCmd = new Command(owner, "Cancel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        if (editorDlgRef[0] != null)
        {
          editorDlgRef[0].close();
        }
        return null;
      }
    };
    BButton cancelEditBtn = new BButton(cancelEditCmd, true, false);

    BGridPane btnRow = new BGridPane(2);
    btnRow.setColumnGap(8.0);
    btnRow.add("sv", saveFileBtn);
    btnRow.add("cn", cancelEditBtn);

    BEdgePane bottom = new BEdgePane();
    bottom.setLeft(hint);
    bottom.setRight(btnRow);

    BEdgePane root = new BEdgePane();
    root.setCenter(border);
    root.setBottom(new BBorderPane(bottom, BInsets.make(8.0, 0.0, 0.0, 0.0)));

    BDialog dlg = new BDialog(owner, title, true, new BBorderPane(root, BInsets.make(10.0)));
    editorDlgRef[0] = dlg;
    dlg.setResizable(true);
    dlg.computePreferredSize();
    dlg.setBoundsCenteredOnOwner();
    dlg.open();
  }

  /**
   * Opens a clean local directory browser dialog allowing the user to navigate drives/folders,
   * paste/type a directory path, or create a new folder (`+ New Folder`).
   */
  public static File promptSelectLocalDirectory(
    BWidget owner,
    String dialogTitle,
    File initialDirectory)
  {
    LocalFolderPickerDialog picker = new LocalFolderPickerDialog(owner, dialogTitle, initialDirectory);
    picker.setBoundsCenteredOnOwner();
    picker.open();
    return picker.selectedFolder;
  }

  private static final class LocalFolderPickerDialog extends BDialog
  {
    private File currentDir;
    private File selectedFolder = null;

    private BTextField pathField;
    private BList folderList;
    private BLabel statusLabel;
    private final List<File> listedDirs = new ArrayList<File>();

    LocalFolderPickerDialog(BWidget owner, String title, File startDir)
    {
      super(owner, title, true);
      if (startDir != null && startDir.isDirectory())
      {
        this.currentDir = startDir;
      }
      else
      {
        try
        {
          File nuh = Sys.getNiagaraUserHome();
          this.currentDir = (nuh != null && nuh.isDirectory()) ? nuh : new File("C:\\");
        }
        catch (Throwable t)
        {
          this.currentDir = new File("C:\\");
        }
      }
      buildUi();
      refreshFolderList();
    }

    private void buildUi()
    {
      BFont boldFont = BFont.make("Tahoma", 11.0, BFont.BOLD);
      BFont smallFont = BFont.make("Tahoma", 10.5);

      pathField = new BTextField(currentDir != null ? currentDir.getAbsolutePath() : "C:\\", 44, true);

      Command goCmd = new Command(this, "Go")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          File candidate = new File(pathField.getText().trim());
          if (candidate.exists() && candidate.isDirectory())
          {
            currentDir = candidate;
            refreshFolderList();
          }
          else
          {
            statusLabel.setText("Folder does not exist yet — click 'Select This Folder' to create it, or '+ New Folder'.");
            statusLabel.relayout();
            statusLabel.repaint();
          }
          return null;
        }
      };
      BButton goBtn = new BButton(goCmd, true, false);

      Command upCmd = new Command(this, "Up")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          if (currentDir != null)
          {
            currentDir = currentDir.getParentFile();
            refreshFolderList();
          }
          return null;
        }
      };
      BButton upBtn = new BButton(upCmd, true, false);

      Command rootsCmd = new Command(this, "Drives (C:\\)")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          currentDir = null;
          refreshFolderList();
          return null;
        }
      };
      BButton rootsBtn = new BButton(rootsCmd, true, false);

      Command newFolderCmd = new Command(this, "+ New Folder")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          if (currentDir == null) return null;
          String name = BDialog.prompt(LocalFolderPickerDialog.this, "Create New Folder", "NewFolder", 26);
          if (name != null && !name.trim().isEmpty())
          {
            File created = new File(currentDir, name.trim());
            if (created.exists() || created.mkdirs())
            {
              currentDir = created;
              refreshFolderList();
            }
          }
          return null;
        }
      };
      BButton newFolderBtn = new BButton(newFolderCmd, true, false);

      BGridPane navBtns = new BGridPane(4);
      navBtns.setColumnGap(5.0);
      navBtns.add("nb1", goBtn);
      navBtns.add("nb2", upBtn);
      navBtns.add("nb3", rootsBtn);
      navBtns.add("nb4", newFolderBtn);

      BEdgePane topBar = new BEdgePane();
      topBar.setLeft(new BBorderPane(new BLabel("Folder Path:", boldFont), BInsets.make(0.0, 0.0, 0.0, 6.0)));
      topBar.setCenter(pathField);
      topBar.setRight(new BBorderPane(navBtns, BInsets.make(0.0, 6.0, 0.0, 0.0)));

      folderList = new BList()
      {
        @Override
        public void mouseReleased(javax.baja.ui.event.BMouseEvent event)
        {
          super.mouseReleased(event);
          if (event.getClickCount() >= 2)
          {
            int idx = getSelectedIndex();
            if (idx >= 0 && idx < listedDirs.size())
            {
              File clicked = listedDirs.get(idx);
              if (clicked != null && clicked.isDirectory())
              {
                currentDir = clicked;
                refreshFolderList();
              }
            }
          }
        }
      };

      BScrollPane listScroll = new BScrollPane(folderList);
      listScroll.setPreferredSize(540.0, 220.0);
      BBorderPane listBorder = new BBorderPane(listScroll, BInsets.make(2.0));
      listBorder.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(180, 195, 215))));

      statusLabel = new BLabel("Double-click any subfolder to open it, or click 'Select This Folder' below.", smallFont);
      statusLabel.setForeground(BBrush.makeSolid(BColor.make(65, 80, 105)));

      Command selectCmd = new Command(this, "Select This Folder")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          String typed = pathField.getText().trim();
          if (!typed.isEmpty())
          {
            File f = new File(typed);
            if (!f.exists())
            {
              f.mkdirs();
            }
            selectedFolder = f;
            close();
          }
          else if (currentDir != null)
          {
            selectedFolder = currentDir;
            close();
          }
          return null;
        }
      };
      BButton selectBtn = new BButton(selectCmd, true, true);

      Command cancelCmd = new Command(this, "Cancel")
      {
        @Override
        public CommandArtifact doInvoke()
        {
          selectedFolder = null;
          close();
          return null;
        }
      };
      BButton cancelBtn = new BButton(cancelCmd, true, false);

      BGridPane bottomBtns = new BGridPane(2);
      bottomBtns.setColumnGap(8.0);
      bottomBtns.setValign(BValign.center);
      bottomBtns.add("sel", selectBtn);
      bottomBtns.add("can", cancelBtn);

      BEdgePane footer = new BEdgePane();
      footer.setLeft(statusLabel);
      footer.setRight(bottomBtns);

      BEdgePane root = new BEdgePane();
      root.setTop(new BBorderPane(topBar, BInsets.make(0.0, 0.0, 8.0, 0.0)));
      root.setCenter(listBorder);
      root.setBottom(new BBorderPane(footer, BInsets.make(8.0, 0.0, 0.0, 0.0)));

      setContent(new BBorderPane(root, BInsets.make(12.0)));
      setResizable(true);
      computePreferredSize();
    }

    private void refreshFolderList()
    {
      folderList.removeAllItems();
      listedDirs.clear();

      if (currentDir == null)
      {
        pathField.setText("");
        File[] roots = File.listRoots();
        if (roots != null)
        {
          for (int i = 0; i < roots.length; i++)
          {
            listedDirs.add(roots[i]);
            folderList.addItem("[DRIVE] " + roots[i].getAbsolutePath());
          }
        }
        return;
      }

      pathField.setText(currentDir.getAbsolutePath());
      if (currentDir.getParentFile() != null)
      {
        listedDirs.add(currentDir.getParentFile());
        folderList.addItem(".. [Up to Parent Folder: " + currentDir.getParentFile().getName() + "]");
      }

      File[] children = currentDir.listFiles();
      if (children != null)
      {
        Arrays.sort(children, new Comparator<File>()
        {
          @Override
          public int compare(File a, File b)
          {
            return a.getName().compareToIgnoreCase(b.getName());
          }
        });
        for (int i = 0; i < children.length; i++)
        {
          File ch = children[i];
          if (ch.isDirectory() && !ch.isHidden())
          {
            listedDirs.add(ch);
            folderList.addItem("[DIR]  " + ch.getName());
          }
        }
      }
      folderList.relayout();
      folderList.repaint();
    }
  }
}
