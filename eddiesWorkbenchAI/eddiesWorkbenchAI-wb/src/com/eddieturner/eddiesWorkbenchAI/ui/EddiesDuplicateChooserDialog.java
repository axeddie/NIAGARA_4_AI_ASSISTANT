/*
 * Copyright 2026 eddieturner. All Rights Reserved.
 */
package com.eddieturner.eddiesWorkbenchAI.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.baja.file.BIDirectory;
import javax.baja.file.BIFile;
import javax.baja.file.BFileSpace;
import javax.baja.file.BFileSystem;
import javax.baja.file.BSubSpaceFile;
import javax.baja.file.FilePath;
import javax.baja.gx.BBrush;
import javax.baja.gx.BColor;
import javax.baja.gx.BFont;
import javax.baja.gx.BImage;
import javax.baja.gx.BInsets;
import javax.baja.naming.BOrd;
import javax.baja.nav.BINavNode;
import javax.baja.space.BSpace;
import javax.baja.sys.BComponent;
import javax.baja.sys.BObject;
import javax.baja.sys.Sys;
import javax.baja.ui.BBorder;
import javax.baja.ui.BButton;
import javax.baja.ui.BDialog;
import javax.baja.ui.BLabel;
import javax.baja.ui.BListDropDown;
import javax.baja.ui.BTextField;
import javax.baja.ui.BWidget;
import javax.baja.ui.Command;
import javax.baja.ui.CommandArtifact;
import javax.baja.ui.enums.BHalign;
import javax.baja.ui.enums.BValign;
import javax.baja.ui.event.BWidgetEvent;
import javax.baja.ui.file.BFileChooser;
import javax.baja.ui.list.BList;
import javax.baja.ui.pane.BBorderPane;
import javax.baja.ui.pane.BEdgePane;
import javax.baja.ui.pane.BGridPane;
import javax.baja.ui.pane.BScrollPane;

/**
 * Dual-space file & directory chooser dialog for the "Make Duplicate Only" guardrail option.
 * Allows selecting a target folder and file name either in the connected Station's Files space
 * (station:|file:^...) or on the local machine (Workbench User Home / Local Disk), and includes
 * 1-click "+ New Folder" creation in both spaces.
 */
public final class EddiesDuplicateChooserDialog extends BDialog
{
  public static final class DuplicateSaveResult
  {
    public final boolean saved;
    public final String savedLocationSummary;
    public final BOrd savedOrd;
    public final File savedLocalFile;

    DuplicateSaveResult(boolean saved, String savedLocationSummary, BOrd savedOrd, File savedLocalFile)
    {
      this.saved = saved;
      this.savedLocationSummary = savedLocationSummary;
      this.savedOrd = savedOrd;
      this.savedLocalFile = savedLocalFile;
    }
  }

  private static final class FolderEntry
  {
    final String displayLabel;
    final boolean isDirectory;
    final BIFile stationFile;
    final File localFile;

    FolderEntry(String displayLabel, boolean isDirectory, BIFile stationFile, File localFile)
    {
      this.displayLabel = displayLabel;
      this.isDirectory = isDirectory;
      this.stationFile = stationFile;
      this.localFile = localFile;
    }

    @Override
    public String toString()
    {
      return displayLabel;
    }
  }

  private static final int SPACE_STATION = 0;
  private static final int SPACE_USER_HOME = 1;
  private static final int SPACE_LOCAL_MACHINE = 2;

  private final BWidget ownerWidget;
  private final BFileSpace stationFileSpace;
  private final byte[] duplicateBytes;
  private final String defaultExtension;

  private int activeSpaceMode;
  private String currentStationRelPath = "^";
  private File currentLocalDir;

  private BListDropDown spaceDropDown;
  private BTextField currentPathField;
  private BList directoryList;
  private BTextField fileNameField;
  private BLabel statusBanner;

  private final List<FolderEntry> currentEntries = new ArrayList<FolderEntry>();
  private DuplicateSaveResult result = new DuplicateSaveResult(false, "", null, null);

  private EddiesDuplicateChooserDialog(
    BWidget owner,
    BFileSpace stationFileSpace,
    String initialStationRelDir,
    File initialLocalDir,
    String defaultFileName,
    String defaultExtension,
    byte[] duplicateBytes)
  {
    super(owner, "Make Duplicate Only — Select Target Location", true);
    this.ownerWidget = owner;
    this.stationFileSpace = stationFileSpace;
    this.duplicateBytes = duplicateBytes != null ? duplicateBytes : new byte[0];
    this.defaultExtension = (defaultExtension != null && !defaultExtension.isEmpty()) ? defaultExtension : "bog";

    if (initialStationRelDir != null && !initialStationRelDir.trim().isEmpty())
    {
      this.currentStationRelPath = normalizeStationRelPath(initialStationRelDir);
    }

    if (initialLocalDir != null && initialLocalDir.isDirectory())
    {
      this.currentLocalDir = initialLocalDir;
    }
    else
    {
      this.currentLocalDir = resolveDefaultUserHomeDir();
    }

    this.activeSpaceMode = (stationFileSpace != null) ? SPACE_STATION : SPACE_USER_HOME;
    buildDialogUi(defaultFileName);
    refreshDirectoryListing();
  }

  /**
   * Opens the Duplicate File Chooser modal dialog and saves the provided duplicateBytes
   * to the user's chosen location in either the Station's Files space or on her local machine.
   */
  public static DuplicateSaveResult promptAndSaveDuplicate(
    BWidget owner,
    BComponent stationContextComp,
    BIFile activeFile,
    String suggestedFileName,
    String defaultExt,
    byte[] duplicateBytes)
  {
    BFileSpace stationSpace = resolveStationFileSpace(stationContextComp, activeFile);
    String initialStationDir = "^";
    File initialLocalDir = resolveDefaultUserHomeDir();

    BSubSpaceFile backingBog = StationContextExtractor.getBackingSubSpaceFile(stationContextComp);
    BIFile fileForInitialDir = (activeFile != null) ? activeFile : backingBog;
    if (fileForInitialDir != null)
    {
      try
      {
        FilePath fp = fileForInitialDir.getFilePath();
        if (fp != null && fp.getParent() != null)
        {
          initialStationDir = fp.getParent().toString();
        }
        File localBacking = BFileSystem.INSTANCE.pathToLocalFile(fp);
        if (localBacking != null && localBacking.getParentFile() != null && localBacking.getParentFile().isDirectory())
        {
          initialLocalDir = localBacking.getParentFile();
        }
      }
      catch (Throwable ignore)
      {
      }
    }

    EddiesDuplicateChooserDialog dlg = new EddiesDuplicateChooserDialog(
      owner,
      stationSpace,
      initialStationDir,
      initialLocalDir,
      suggestedFileName,
      defaultExt,
      duplicateBytes);
    dlg.setBoundsCenteredOnOwner();
    dlg.open();
    return dlg.result;
  }

  private void buildDialogUi(String defaultFileName)
  {
    BFont titleFont = BFont.make("Tahoma", 12.0, BFont.BOLD);
    BFont boldSmall = BFont.make("Tahoma", 11.0, BFont.BOLD);
    BFont normalSmall = BFont.make("Tahoma", 11.0);

    // 1. Top Header Banner
    BLabel headerTitle = new BLabel(
      "Choose where to save the AI-modified duplicate (original station/file will NOT be modified):",
      titleFont);
    headerTitle.setForeground(BBrush.makeSolid(BColor.make(20, 65, 125)));

    BBorderPane topBanner = new BBorderPane(headerTitle, BInsets.make(8.0, 10.0, 8.0, 10.0));
    topBanner.setFill(BBrush.makeSolid(BColor.make(232, 240, 252)));
    topBanner.setBorder(BBorder.make(
      0.0, BBorder.NONE, BBrush.NULL,
      0.0, BBorder.NONE, BBrush.NULL,
      1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(190, 205, 228)),
      0.0, BBorder.NONE, BBrush.NULL
    ));

    // 2. Space Selector + Navigation Toolbar
    BLabel spaceLabel = new BLabel("Target Space:", boldSmall);
    spaceDropDown = new BListDropDown()
    {
      @Override
      public void doListActionPerformed()
      {
        super.doListActionPerformed();
        onSpaceSelectionChanged();
      }
    };
    if (stationFileSpace != null)
    {
      spaceDropDown.getList().addItem("1. Connected Station Files (station:|file:^)");
    }
    else
    {
      spaceDropDown.getList().addItem("1. Connected Station Files (Not Connected — Uses Local)");
    }
    spaceDropDown.getList().addItem("2. Workbench User Home (file:! / Niagara User Home)");
    spaceDropDown.getList().addItem("3. This Machine (C:\\ & Local Folders)");
    spaceDropDown.setSelectedIndex(activeSpaceMode);

    Command nativeChooserCmd = new Command(this, "Niagara File Chooser...")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        openNativeNiagaraChooser();
        return null;
      }
    };
    BButton nativeChooserBtn = new BButton(nativeChooserCmd, true, false);

    BEdgePane spaceRow = new BEdgePane();
    spaceRow.setLeft(new BBorderPane(spaceLabel, BInsets.make(2.0, 8.0, 2.0, 0.0)));
    spaceRow.setCenter(spaceDropDown);
    spaceRow.setRight(new BBorderPane(nativeChooserBtn, BInsets.make(0.0, 0.0, 0.0, 6.0)));

    // 3. Current Directory Path + Up Level + New Folder Buttons
    BLabel dirLabel = new BLabel("Folder:", boldSmall);
    currentPathField = new BTextField("", 38, true);

    Command goPathCmd = new Command(this, "Go")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        navigateToTypedPath();
        return null;
      }
    };
    BButton goBtn = new BButton(goPathCmd, true, false);

    Command upCmd = new Command(this, "Up Folder")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        navigateUpOneLevel();
        return null;
      }
    };
    BButton upBtn = new BButton(upCmd, true, false);

    Command newFolderCmd = new Command(this, "+ New Folder")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        promptCreateNewFolder();
        return null;
      }
    };
    BButton newFolderBtn = new BButton(newFolderCmd, true, false);

    BGridPane folderActions = new BGridPane(3);
    folderActions.setColumnGap(4.0);
    folderActions.add("go", goBtn);
    folderActions.add("up", upBtn);
    folderActions.add("newFolder", newFolderBtn);

    BEdgePane pathRow = new BEdgePane();
    pathRow.setLeft(new BBorderPane(dirLabel, BInsets.make(2.0, 8.0, 2.0, 0.0)));
    pathRow.setCenter(currentPathField);
    pathRow.setRight(new BBorderPane(folderActions, BInsets.make(0.0, 0.0, 0.0, 6.0)));

    BGridPane topControls = new BGridPane(1);
    topControls.setRowGap(6.0);
    topControls.setStretchColumn(0);
    topControls.setColumnAlign(BHalign.left);
    topControls.add("spaceRow", spaceRow);
    topControls.add("pathRow", pathRow);

    // 4. Center Directory & File Listing
    BLabel listHint = new BLabel(
      "Folders & Files in Current Location (Double-click a [Folder] to open it, or click '+ New Folder'):",
      normalSmall);

    directoryList = new BList()
    {
      @Override
      public void fireActionPerformed(BWidgetEvent event)
      {
        super.fireActionPerformed(event);
        openSelectedDirectoryOrSelectFile(true);
      }

      @Override
      public void fireSelectionModified(BWidgetEvent event)
      {
        super.fireSelectionModified(event);
        openSelectedDirectoryOrSelectFile(false);
      }
    };

    BScrollPane listScroll = new BScrollPane(directoryList);
    listScroll.setPreferredSize(580.0, 240.0);
    BBorderPane listBorder = new BBorderPane(listScroll, BInsets.make(2.0));
    listBorder.setBorder(BBorder.make(1.0, BBorder.SOLID, BBrush.makeSolid(BColor.make(180, 190, 205))));

    Command openFolderBtnCmd = new Command(this, "Open Selected Folder")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        openSelectedDirectoryOrSelectFile(true);
        return null;
      }
    };
    BButton openFolderBtn = new BButton(openFolderBtnCmd, true, false);

    BEdgePane listHeaderRow = new BEdgePane();
    listHeaderRow.setLeft(new BBorderPane(listHint, BInsets.make(2.0, 0.0, 2.0, 0.0)));
    listHeaderRow.setRight(openFolderBtn);

    BEdgePane centerListPane = new BEdgePane();
    centerListPane.setTop( new BBorderPane(listHeaderRow, BInsets.make(0.0, 0.0, 4.0, 0.0)));
    centerListPane.setCenter(listBorder);

    // 5. Bottom File Name + Status + Save / Cancel Buttons
    BLabel fileLabel = new BLabel("Duplicate File Name:", boldSmall);
    fileNameField = new BTextField(
      (defaultFileName != null && !defaultFileName.trim().isEmpty())
        ? defaultFileName.trim()
        : ("AI_Duplicate." + defaultExtension),
      40,
      true);

    BEdgePane fileRow = new BEdgePane();
    fileRow.setLeft(new BBorderPane(fileLabel, BInsets.make(2.0, 8.0, 2.0, 0.0)));
    fileRow.setCenter(fileNameField);

    statusBanner = new BLabel("Ready to save duplicate.", normalSmall);
    statusBanner.setForeground(BBrush.makeSolid(BColor.make(40, 100, 55)));

    Command cancelCmd = new Command(this, "Cancel")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        result = new DuplicateSaveResult(false, "", null, null);
        close();
        return null;
      }
    };
    BButton cancelBtn = new BButton(cancelCmd, true, false);

    Command saveDupCmd = new Command(this, "Save Duplicate Here")
    {
      @Override
      public CommandArtifact doInvoke()
      {
        saveDuplicateToCurrentSelection();
        return null;
      }
    };
    BButton saveDupBtn = new BButton(saveDupCmd, true, true);

    BGridPane bottomBtns = new BGridPane(2);
    bottomBtns.setColumnGap(8.0);
    bottomBtns.setValign(BValign.center);
    bottomBtns.add("cancel", cancelBtn);
    bottomBtns.add("save", saveDupBtn);

    BEdgePane actionFooter = new BEdgePane();
    actionFooter.setLeft(new BBorderPane(statusBanner, BInsets.make(4.0, 4.0, 4.0, 4.0)));
    actionFooter.setRight(bottomBtns);

    BGridPane bottomStack = new BGridPane(1);
    bottomStack.setRowGap(8.0);
    bottomStack.setStretchColumn(0);
    bottomStack.setColumnAlign(BHalign.left);
    bottomStack.add("fileRow", fileRow);
    bottomStack.add("footer", actionFooter);

    BEdgePane mainBody = new BEdgePane();
    mainBody.setTop(new BBorderPane(topControls, BInsets.make(8.0, 10.0, 6.0, 10.0)));
    mainBody.setCenter(new BBorderPane(centerListPane, BInsets.make(4.0, 10.0, 6.0, 10.0)));
    mainBody.setBottom(new BBorderPane(bottomStack, BInsets.make(6.0, 10.0, 10.0, 10.0)));

    BEdgePane dialogRoot = new BEdgePane();
    dialogRoot.setTop(topBanner);
    dialogRoot.setCenter(mainBody);
    dialogRoot.setPreferredSize(640.0, 460.0);

    setContent(dialogRoot);
  }

  private void onSpaceSelectionChanged()
  {
    int idx = spaceDropDown.getSelectedIndex();
    if (idx == SPACE_STATION && stationFileSpace != null)
    {
      activeSpaceMode = SPACE_STATION;
    }
    else if (idx == SPACE_USER_HOME)
    {
      activeSpaceMode = SPACE_USER_HOME;
      currentLocalDir = resolveDefaultUserHomeDir();
    }
    else
    {
      activeSpaceMode = SPACE_LOCAL_MACHINE;
      if (currentLocalDir == null || !currentLocalDir.exists())
      {
        currentLocalDir = new File("C:\\");
      }
    }
    refreshDirectoryListing();
  }

  private void refreshDirectoryListing()
  {
    currentEntries.clear();
    directoryList.removeAllItems();

    BImage folderIcon = makeIcon("module://icons/x16/folder.png");
    BImage fileIcon = makeIcon("module://icons/x16/file.png");

    if (activeSpaceMode == SPACE_STATION && stationFileSpace != null)
    {
      currentStationRelPath = normalizeStationRelPath(currentStationRelPath);
      currentPathField.setText("station:|file:" + currentStationRelPath);

      try
      {
        BIFile dirNode = resolveStationDirNode(currentStationRelPath);
        BIFile[] kids = null;
        if (dirNode instanceof BIDirectory)
        {
          kids = ((BIDirectory) dirNode).listFiles();
        }
        else if (dirNode != null)
        {
          kids = stationFileSpace.getChildren(dirNode);
        }
        else
        {
          BINavNode[] navKids = stationFileSpace.getNavChildren();
          if (navKids != null)
          {
            List<BIFile> tmp = new ArrayList<BIFile>();
            for (int i = 0; i < navKids.length; i++)
            {
              if (navKids[i] instanceof BIFile)
              {
                tmp.add((BIFile) navKids[i]);
              }
            }
            kids = tmp.toArray(new BIFile[0]);
          }
        }

        if (kids != null)
        {
          Arrays.sort(kids, new Comparator<BIFile>()
          {
            @Override
            public int compare(BIFile a, BIFile b)
            {
              boolean da = a.isDirectory() || (a instanceof BIDirectory);
              boolean db = b.isDirectory() || (b instanceof BIDirectory);
              if (da != db) return da ? -1 : 1;
              return safeFileName(a).compareToIgnoreCase(safeFileName(b));
            }
          });

          for (int i = 0; i < kids.length; i++)
          {
            BIFile f = kids[i];
            boolean isDir = f.isDirectory() || (f instanceof BIDirectory);
            String label = isDir
              ? ("[Folder]  " + safeFileName(f))
              : ("          " + safeFileName(f));
            FolderEntry entry = new FolderEntry(label, isDir, f, null);
            currentEntries.add(entry);
            directoryList.addItem(isDir ? folderIcon : fileIcon, entry);
          }
        }
        statusBanner.setText("Browsing Connected Station Files (" + currentEntries.size() + " item(s))");
      }
      catch (Throwable t)
      {
        statusBanner.setText("Station Files note: " + t.getMessage());
      }
    }
    else
    {
      if (currentLocalDir == null)
      {
        // Root drive list on Windows
        currentPathField.setText("This Machine (Drives)");
        File[] roots = File.listRoots();
        if (roots != null)
        {
          for (int i = 0; i < roots.length; i++)
          {
            File r = roots[i];
            FolderEntry entry = new FolderEntry("[Drive]   " + r.getAbsolutePath(), true, null, r);
            currentEntries.add(entry);
            directoryList.addItem(folderIcon, entry);
          }
        }
      }
      else
      {
        currentPathField.setText(currentLocalDir.getAbsolutePath());
        File[] files = currentLocalDir.listFiles();
        if (files != null)
        {
          Arrays.sort(files, new Comparator<File>()
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

          for (int i = 0; i < files.length; i++)
          {
            File f = files[i];
            if (f.isHidden()) continue;
            boolean isDir = f.isDirectory();
            String label = isDir
              ? ("[Folder]  " + f.getName())
              : ("          " + f.getName());
            FolderEntry entry = new FolderEntry(label, isDir, null, f);
            currentEntries.add(entry);
            directoryList.addItem(isDir ? folderIcon : fileIcon, entry);
          }
        }
        statusBanner.setText("Browsing Local Folder (" + currentEntries.size() + " item(s))");
      }
    }

    directoryList.relayout();
    directoryList.repaint();
  }

  private void openSelectedDirectoryOrSelectFile(boolean navigateIfFolder)
  {
    int selIdx = directoryList.getSelectedIndex();
    if (selIdx < 0 || selIdx >= currentEntries.size())
    {
      return;
    }
    FolderEntry entry = currentEntries.get(selIdx);
    if (entry.isDirectory)
    {
      if (navigateIfFolder)
      {
        if (activeSpaceMode == SPACE_STATION && entry.stationFile != null)
        {
          FilePath fp = entry.stationFile.getFilePath();
          if (fp != null)
          {
            currentStationRelPath = normalizeStationRelPath(fp.toString());
          }
          else
          {
            currentStationRelPath = mergeStationRelPath(currentStationRelPath, safeFileName(entry.stationFile));
          }
          refreshDirectoryListing();
        }
        else if (entry.localFile != null)
        {
          currentLocalDir = entry.localFile;
          refreshDirectoryListing();
        }
      }
    }
    else
    {
      if (entry.stationFile != null)
      {
        fileNameField.setText(safeFileName(entry.stationFile));
      }
      else if (entry.localFile != null)
      {
        fileNameField.setText(entry.localFile.getName());
      }
    }
  }

  private void navigateToTypedPath()
  {
    String raw = currentPathField.getText();
    if (raw == null || raw.trim().isEmpty()) return;
    String clean = raw.trim();

    if (clean.startsWith("station:|file:") || clean.startsWith("file:^") || clean.startsWith("^"))
    {
      if (stationFileSpace != null)
      {
        activeSpaceMode = SPACE_STATION;
        spaceDropDown.setSelectedIndex(SPACE_STATION);
        int caretIdx = clean.indexOf('^');
        currentStationRelPath = caretIdx >= 0 ? clean.substring(caretIdx) : "^";
        refreshDirectoryListing();
        return;
      }
    }

    File candidate = new File(clean);
    if (candidate.exists() && candidate.isDirectory())
    {
      activeSpaceMode = SPACE_LOCAL_MACHINE;
      spaceDropDown.setSelectedIndex(SPACE_LOCAL_MACHINE);
      currentLocalDir = candidate;
      refreshDirectoryListing();
    }
    else if (candidate.getParentFile() != null && candidate.getParentFile().isDirectory())
    {
      activeSpaceMode = SPACE_LOCAL_MACHINE;
      spaceDropDown.setSelectedIndex(SPACE_LOCAL_MACHINE);
      currentLocalDir = candidate.getParentFile();
      fileNameField.setText(candidate.getName());
      refreshDirectoryListing();
    }
    else
    {
      BDialog.error(this, "Invalid Folder Path", "Could not find folder: " + clean);
    }
  }

  private void navigateUpOneLevel()
  {
    if (activeSpaceMode == SPACE_STATION && stationFileSpace != null)
    {
      String p = normalizeStationRelPath(currentStationRelPath);
      if ("^".equals(p) || "^/".equals(p))
      {
        return;
      }
      int slash = p.lastIndexOf('/');
      if (slash > 1)
      {
        currentStationRelPath = p.substring(0, slash);
      }
      else
      {
        currentStationRelPath = "^";
      }
      refreshDirectoryListing();
    }
    else
    {
      if (currentLocalDir != null)
      {
        currentLocalDir = currentLocalDir.getParentFile();
        refreshDirectoryListing();
      }
    }
  }

  private void promptCreateNewFolder()
  {
    String folderName = BDialog.prompt(this, "Create New Folder", "NewFolder", 28);
    if (folderName == null || folderName.trim().isEmpty())
    {
      return;
    }
    String cleanName = sanitizeFileOrFolderName(folderName.trim());
    if (cleanName.isEmpty())
    {
      BDialog.error(this, "Invalid Folder Name", "Please enter a valid folder name.");
      return;
    }

    try
    {
      if (activeSpaceMode == SPACE_STATION && stationFileSpace != null)
      {
        String targetRel = mergeStationRelPath(currentStationRelPath, cleanName);
        FilePath newDirPath = new FilePath(targetRel);
        stationFileSpace.makeDir(newDirPath);
        currentStationRelPath = targetRel;
        refreshDirectoryListing();
        statusBanner.setText("Created new station folder: station:|file:" + targetRel);
      }
      else
      {
        File baseDir = (currentLocalDir != null) ? currentLocalDir : resolveDefaultUserHomeDir();
        File newDir = new File(baseDir, cleanName);
        if (!newDir.exists() && !newDir.mkdirs())
        {
          throw new Exception("Unable to create local directory: " + newDir.getAbsolutePath());
        }
        currentLocalDir = newDir;
        refreshDirectoryListing();
        statusBanner.setText("Created new local folder: " + newDir.getAbsolutePath());
      }
    }
    catch (Throwable t)
    {
      BDialog.error(this, "Create Folder Error", "Could not create folder \"" + cleanName + "\": " + t.getMessage(), t);
    }
  }

  private void openNativeNiagaraChooser()
  {
    try
    {
      BFileChooser chooser = BFileChooser.makeSave(ownerWidget != null ? ownerWidget : this);
      List<BSpace> spaces = new ArrayList<BSpace>();
      if (stationFileSpace != null)
      {
        spaces.add(stationFileSpace);
      }
      spaces.add(BFileSystem.INSTANCE);
      chooser.setSpaces(spaces.toArray(new BSpace[0]));

      String curFileName = fileNameField.getText();
      if (curFileName != null && !curFileName.trim().isEmpty())
      {
        chooser.setDefaultFileName(curFileName.trim());
      }
      chooser.setCreateFileOnSave(true);
      chooser.setConfirmOverwrite(true);

      // Initialize currentDirectory to a concrete BIDirectory so Niagara's built-in CreateDirCommand works
      try
      {
        if (activeSpaceMode == SPACE_STATION && stationFileSpace != null)
        {
          BIFile dirNode = resolveStationDirNode(currentStationRelPath);
          if (dirNode != null)
          {
            chooser.setCurrentDirectory(dirNode.getAbsoluteOrd());
          }
        }
        else if (currentLocalDir != null)
        {
          BOrd localOrd = BFileSystem.INSTANCE.localFileToOrd(currentLocalDir);
          if (localOrd != null)
          {
            chooser.setCurrentDirectory(localOrd);
          }
        }
      }
      catch (Throwable ignore)
      {
      }

      BOrd chosenOrd = chooser.show();
      if (chosenOrd != null && !chosenOrd.isNull())
      {
        BObject resolved = chosenOrd.resolve(stationFileSpace != null ? stationFileSpace : BFileSystem.INSTANCE).get();
        if (resolved instanceof BIFile)
        {
          BIFile targetFile = (BIFile) resolved;
          targetFile.write(duplicateBytes);
          result = new DuplicateSaveResult(true, chosenOrd.toString(), chosenOrd, null);
          close();
        }
      }
    }
    catch (Throwable t)
    {
      BDialog.error(this, "Niagara File Chooser Error", t.getMessage(), t);
    }
  }

  private void saveDuplicateToCurrentSelection()
  {
    String rawName = fileNameField.getText();
    if (rawName == null || rawName.trim().isEmpty())
    {
      BDialog.error(this, "Missing File Name", "Please enter a file name for the duplicate.");
      return;
    }

    String fileName = sanitizeFileOrFolderName(rawName.trim());
    if (fileName.indexOf('.') < 0 && defaultExtension != null && !defaultExtension.isEmpty())
    {
      fileName = fileName + "." + defaultExtension;
    }

    try
    {
      if (activeSpaceMode == SPACE_STATION && stationFileSpace != null)
      {
        String fileRelPath = mergeStationRelPath(currentStationRelPath, fileName);
        FilePath fp = new FilePath(fileRelPath);
        BIFile existing = stationFileSpace.findFile(fp);
        if (existing != null)
        {
          int overwrite = BDialog.confirm(
            this,
            "Confirm Overwrite",
            "File already exists at station:|file:" + fileRelPath + ".\nOverwrite it?");
          if (overwrite != BDialog.YES && overwrite != BDialog.OK)
          {
            return;
          }
        }
        BIFile targetFile = (existing != null) ? existing : stationFileSpace.makeFile(fp);
        targetFile.write(duplicateBytes);
        BOrd absOrd = stationFileSpace.getAbsoluteOrd(fp);
        String summary = (absOrd != null) ? absOrd.toString() : ("station:|file:" + fileRelPath);
        result = new DuplicateSaveResult(true, summary, absOrd, null);
        close();
      }
      else
      {
        File dir = (currentLocalDir != null) ? currentLocalDir : resolveDefaultUserHomeDir();
        if (!dir.exists())
        {
          dir.mkdirs();
        }
        File targetLocalFile = new File(dir, fileName);
        if (targetLocalFile.exists())
        {
          int overwrite = BDialog.confirm(
            this,
            "Confirm Overwrite",
            "File already exists at:\n" + targetLocalFile.getAbsolutePath() + "\nOverwrite it?");
          if (overwrite != BDialog.YES && overwrite != BDialog.OK)
          {
            return;
          }
        }

        FileOutputStream fos = new FileOutputStream(targetLocalFile);
        try
        {
          fos.write(duplicateBytes);
          fos.flush();
        }
        finally
        {
          try { fos.close(); } catch (Throwable ignore) {}
        }

        BOrd localOrd = null;
        try
        {
          localOrd = BFileSystem.INSTANCE.localFileToOrd(targetLocalFile);
        }
        catch (Throwable ignore)
        {
        }
        result = new DuplicateSaveResult(true, targetLocalFile.getAbsolutePath(), localOrd, targetLocalFile);
        close();
      }
    }
    catch (Throwable t)
    {
      BDialog.error(this, "Save Duplicate Failed", "Failed to write duplicate file: " + t.getMessage(), t);
    }
  }

  private BIFile resolveStationDirNode(String relPath)
  {
    if (stationFileSpace == null) return null;
    try
    {
      String norm = normalizeStationRelPath(relPath);
      FilePath fp = new FilePath(norm);
      BIFile found = stationFileSpace.findFile(fp);
      if (found != null) return found;
    }
    catch (Throwable ignore)
    {
    }
    return null;
  }

  public static BFileSpace resolveStationFileSpace(BComponent stationComp, BIFile activeFile)
  {
    if (activeFile != null)
    {
      try
      {
        BFileSpace fs = activeFile.getFileSpace();
        if (fs != null && fs != BFileSystem.INSTANCE)
        {
          return fs;
        }
      }
      catch (Throwable ignore)
      {
      }
    }
    if (stationComp != null)
    {
      try
      {
        BObject obj = BOrd.make("station:|file:^").resolve(stationComp).get();
        if (obj instanceof BIFile)
        {
          return ((BIFile) obj).getFileSpace();
        }
        if (obj instanceof BFileSpace)
        {
          return (BFileSpace) obj;
        }
      }
      catch (Throwable ignore)
      {
      }
      try
      {
        BObject obj = BOrd.make("file:^").resolve(stationComp).get();
        if (obj instanceof BIFile)
        {
          return ((BIFile) obj).getFileSpace();
        }
        if (obj instanceof BFileSpace)
        {
          return (BFileSpace) obj;
        }
      }
      catch (Throwable ignore)
      {
      }
    }
    return null;
  }

  private static File resolveDefaultUserHomeDir()
  {
    try
    {
      File nuh = Sys.getNiagaraUserHome();
      if (nuh != null && nuh.isDirectory())
      {
        return nuh;
      }
    }
    catch (Throwable ignore)
    {
    }
    try
    {
      String userHome = System.getProperty("user.home");
      if (userHome != null && !userHome.isEmpty())
      {
        File f = new File(userHome);
        if (f.isDirectory()) return f;
      }
    }
    catch (Throwable ignore)
    {
    }
    return new File(".");
  }

  private static String normalizeStationRelPath(String raw)
  {
    if (raw == null || raw.trim().isEmpty()) return "^";
    String s = raw.trim().replace('\\', '/');
    int caret = s.indexOf('^');
    if (caret >= 0)
    {
      s = s.substring(caret);
    }
    else if (s.startsWith("/"))
    {
      s = "^" + s.substring(1);
    }
    else if (!s.startsWith("^"))
    {
      s = "^" + s;
    }
    while (s.length() > 1 && s.endsWith("/"))
    {
      s = s.substring(0, s.length() - 1);
    }
    return s;
  }

  private static String mergeStationRelPath(String dirRelPath, String childName)
  {
    String base = normalizeStationRelPath(dirRelPath);
    String cleanChild = childName != null ? childName.trim().replace('\\', '/') : "";
    while (cleanChild.startsWith("/"))
    {
      cleanChild = cleanChild.substring(1);
    }
    if ("^".equals(base))
    {
      return "^" + cleanChild;
    }
    return base + "/" + cleanChild;
  }

  private static String safeFileName(BIFile f)
  {
    if (f == null) return "";
    try
    {
      String n = f.getFileName();
      if (n != null && !n.isEmpty()) return n;
    }
    catch (Throwable ignore)
    {
    }
    try
    {
      String n = f.getNavName();
      if (n != null && !n.isEmpty()) return n;
    }
    catch (Throwable ignore)
    {
    }
    return f.toString();
  }

  private static String sanitizeFileOrFolderName(String name)
  {
    if (name == null) return "";
    StringBuilder sb = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++)
    {
      char c = name.charAt(i);
      if (c == '<' || c == '>' || c == ':' || c == '"' || c == '/' || c == '\\' || c == '|' || c == '?' || c == '*')
      {
        sb.append('_');
      }
      else
      {
        sb.append(c);
      }
    }
    return sb.toString().trim();
  }

  private static BImage makeIcon(String ord)
  {
    try
    {
      return BImage.make(ord);
    }
    catch (Throwable t)
    {
      return BImage.NULL;
    }
  }
}
