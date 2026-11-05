# Eddie's Workbench AI (`eddiesWorkbenchAI`) — Project Context & Architecture

**Author:** Eddie Turner  
**Target Platform:** Niagara 4 (`C:\Niagara\EC-Net Facilities-4.15.4.24`)  
**Module Type:** Workbench UI Module (`eddiesWorkbenchAI-wb`)  
**Company AI Reference:** `C:\_MODEL_FARM` (`https://aoai-farm.bosch-temp.com` & Microsoft 365 Work Account SSO)  
**Last Updated:** 2026-11-03  

---

## 1. Ultimate Project Goals

1. **Personal Google Account (Direct Gemini API) & Company AI Platform Integration**
   - Supports **Personal Google Account** via Google's official Generative Language API (`https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`) using a personal Google AI Studio API Key (masked in UI with `PasswordRenderer` and encrypted on disk with AES-GCM-256 + Windows user/machine-bound PBKDF2 key, or read from `GEMINI_API_KEY` / `GOOGLE_API_KEY` environment variables) or Google Browser OAuth 2.0 + PKCE.
   - Also supports **Company Copilot / Company AI Platform subscription** (`https://aoai-farm.bosch-temp.com` and corporate Microsoft 365 Work Account SSO).
   - Supports personal Google models (`gemini-2.5-pro`, `gemini-2.5-flash`, `gemini-2.5-flash-lite`, `gemini-2.0-flash`) alongside verified company models (`gpt-5-nano-2025-08-07`, `askbosch-prod-farm-openai-gpt-4o-mini-2024-07-18`, `gemini-2.5-flash-lite`, `gemini-3.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash`, `gpt-5-2025-08-07`, `gpt-5.6-sol-2026-07-09`, `claude-sonnet-4-5@20250929`, `claude-opus-4-8`).
   - Sends the required company gateway headers (`genaiplatform-farm-subscription-key`, `genaiplatform-farm-subscription-id`, `Authorization: Bearer`, `Ocp-Apim-Subscription-Key`) and supports optional local Px proxy (`127.0.0.1:3128`) as well as Microsoft 365 Work Account Browser SSO (`login.microsoftonline.com`).

2. **Station-Aware Context (Active View Integration)**
   - Works specifically on the **Niagara Station** (and specific `BComponent`, WireSheet, Folder, `BProgram`, or `.px` graphic) currently showing in the active Niagara Workbench view (`BWbShell.getActiveView()` / `BWbShell.getActiveOrd()`).
   - Automatically harvests active component properties, status values, facets, incoming/outgoing `BLink` wires, wixel coordinates (`BWsAnnotation`), child components, multi-selection (`Mark`), `BProgram` source code, and `.px` XML files.

3. **Prompt-to-Action Station Modification Engine**
   - Takes a natural-language prompt and parses the AI's response containing executable station modification instructions.
   - Supports **Preview & Apply Changes** by default, with an **Auto-Apply** checkbox toggle in the sidebar and a 1-click **Undo** snapshot.

---

## 2. Confirmed Design Decisions

1. **Company AI / Copilot Authentication**:
   - **Company AI Platform Subscription Key & Subscription ID** (`https://aoai-farm.bosch-temp.com`), with 1-click import if a station `Program` object already holds `ApiKey` / `Domain` / `SubscriptionId` slots.
   - **Microsoft 365 Work Account SSO** via system browser (`login.microsoftonline.com` OAuth 2.0 + PKCE loopback) so corporate SSO, Windows sign-in, and MFA work without typing a password into Workbench.
   - **Optional Local Proxy (`127.0.0.1:3128`)** toggle for corporate network laptop routing via `Px`.
2. **Mandatory 3-Option Guardrail Dialog & Snapshot Undo**:
   - **Every modification (big or small)** pops up `EddiesModificationGuardDialog` with an efficient itemized description of what will be done and three choices:
     - **`Cancel`** — Cancels without touching the station or file (retaining the plan in `Review Plan` / `Apply Changes`).
     - **`Make Duplicate Only`** — Opens `EddiesDuplicateChooserDialog`, allowing the user to select any target folder in **Station Files (`station:|file:^`)**, **Workbench User Home (`file:!`)**, or **This Machine (Local Disk)**, create a **New Folder** if desired, and save the modified duplicate (as a `.bog` file for station component/WireSheet operations or as a duplicate `.px`/file for file operations) while leaving the live station and original file untouched.
     - **`Proceed`** — Captures a full pre-change BOG/byte snapshot in `StationUndoManager` (up to 20 multi-level undo steps) and applies the changes to the live station or active file.
3. **Universal Station & File Modification Protocol + Agentic Inspection**:
   - **Agentic Read-Only Inspection (`<station-inspect>`)**:
     - `inspectOrd`, `runBql`, `readFile`, `listFiles` — Allows the AI to autonomously inspect any station ORD, run BQL queries, or read/list files before proposing its `<station-actions>` plan.
   - **Universal Structured JSON Actions (`<station-actions>`)**:
     - `createComponent` / `addComponent` (with smart Wixel coordinates, initial `value`, `facets`, and `flags`).
     - `setSlotValue` / `setProperty` (arbitrary deep dot-paths like `proxyExt.pointId`, `alarmExt.offnormalAlgorithm.highLimit`, `historyExt.interval`, plus enforcing `station:|slot:/...` on `BNiagaraProxyExt` `pointId`).
     - `addDynamicSlot` / `addProperty` & `addExtension` (attaching history, alarm, and proxy extensions with property configs).
     - `setFacets`, `setSlotFlags`, `addTag`, `removeTag`, `addRelation`
     - `link` / `addLink` & `removeLink` / `unlink` (with full link restoration on Undo)
     - `renameComponent` / `renameSlot`, `copyComponent` / `duplicateComponent`, `moveComponent`
     - `deleteComponent` / `deleteSlot` (with full `.bog` XML subtree backup for 1-click Undo restoration)
     - `invokeAction` (with parameter decoding for `override`, `active`, `inactive`, `set`, `compile`, etc.)
     - `batchBql` (BQL mass-modification across matching station components)
     - `updateProgram` (updating & compiling `BProgram` Java source and imports)
     - `updatePxFile`, `writeFile`, `createFile`, `patchFile` / `editFile`, `copyFile`, `renameFile`, `deleteFile`, `makeDir` (across both Station `Files` space and local Workbench filesystem)
     - `importBog` & `arrangeWiresheet`

---

## 3. Module Implementation Files (`eddiesWorkbenchAI-wb`)

- `module-permissions.xml` — Grants `NETWORK_COMMUNICATION`, `GET_ENVIRONMENT_VARIABLES`, `SYSTEM_PROPERTIES`, and `UI` permissions for outbound AI Gateway & SSO requests and Workbench operations.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/BEddiesAiTool.java` & `BEddiesAiSideBar.java` — Registered in `module-include.xml` (`Tools -> Eddie's AI Assistant` and `Window -> Side Bars -> Eddie's AI Assistant`). Trigger `EddiesAiShellManager.scheduleInstall()` on startup and allow toggling the right-side AI panel.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiShellManager.java` — Watches active `BWbShell` instances, docks `BEddiesAiRightPanel` into `wbPane.setRight(...)` with a 6px left-edge resize splitter (`BResizableDockPane`), and installs an `"Eddie's AI"` toggle button on the right end of the Workbench toolbar.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/BEddiesAiRightPanel.java` — Main right-side panel controller rendered with a Dark-Theme HTML5/CSS3/JS UI inside a JavaFX `WebView` (`BFxWidget`). Houses the Model selector, `Account` button, Auth status badge, rich Markdown & Code-Block Chat Transcript, animated `"Working..."` indicator, Agentic Inspection loop, and Pending Plan action bar (`Review & Apply`, `Undo`).
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiWebUiTemplate.java` — Self-contained Dark-Theme HTML5/CSS3/ES5-JavaScript template for the right-side AI Assistant panel with Markdown/code-block formatting, 1-click Copy buttons, and zero duplicate buttons.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesModificationGuardDialog.java` — Mandatory 3-option confirmation dialog (`"Cancel"` | `"Make Duplicate Only"` | `"Proceed"`) shown with an efficient summary before every station or file modification.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesDuplicateChooserDialog.java` — Dual-space file & directory chooser for `"Make Duplicate Only"`, supporting **Connected Station Files (`station:|file:^`)**, **Workbench User Home (`file:!`)**, **This Machine (Local Disk)**, and **`+ New Folder`** creation.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/StationUndoManager.java` — Multi-level full-snapshot Undo manager (up to 20 batches) using `ValueDocEncoder` `.bog` XML subtree snapshots and byte-level file backups.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/StationInspector.java` — Executes `<station-inspect>` read-only queries (`inspectOrd`, `runBql`, `readFile`, `listFiles`) so the AI can inspect any station component or file before modifying.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiConfig.java` — SecurityManager-safe persistent settings stored at `<niagara_user_home>/eddiesWorkbenchAI.properties` (`safeGetenv` / `safeGetProperty` with `try/catch (Throwable)` guards).
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiAuthManager.java` — Microsoft 365 Work Account Browser SSO (`login.microsoftonline.com` OAuth 2.0 + PKCE loopback) and credential import.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiSettingsDialog.java` — Modal `BDialog` for configuring Personal Google Gemini API Key (AES-GCM-256 encrypted) and Company AI Platform credentials.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiClient.java` — Asynchronous HTTP client for Personal Google Gemini API and Company AI Gateway (`https://aoai-farm.bosch-temp.com`).
- `src/com/eddieturner/eddiesWorkbenchAI/ui/StationContextExtractor.java` — Extracts live station & file context from the active Workbench view (`BWbShell.getActiveView()` / `BWbShell.getActiveOrd()`).
- `src/com/eddieturner/eddiesWorkbenchAI/ui/StationActionExecutor.java` — Universal station & file modification engine supporting live execution (`Proceed`), offline duplicate generation (`Make Duplicate Only`), and full Snapshot Undo.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/WiresheetLayoutEngine.java` — Intelligent WireSheet layout engine separating unlinked blocks from connected logic chains using left-to-right topological signal-flow layering and dynamic `wixelWidth` sizing.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiHomePageCustomizer.java` — Enhances the Niagara Workbench Home / About page (`workbench:/help/about.html`) with AI-Powered Workbench vector graphics and neural-wiresheet SVG diagrams.

