# Eddie's Workbench AI (`eddiesWorkbenchAI`) — Project Context & Architecture

**Author:** Eddie Turner  
**Target Platform:** Niagara 4 (`C:\Niagara\EC-Net Facilities-4.15.4.24`)  
**Module Type:** Workbench UI Module (`eddiesWorkbenchAI-wb`)  
**Company AI Reference:** `C:\_MODEL_FARM` (`https://aoai-farm.bosch-temp.com` & Microsoft 365 Work Account SSO)  
**Last Updated:** 2026-11-03  

---

## 1. Ultimate Project Goals

1. **Dedicated Company Copilot / AI Platform Integration**
   - Built specifically to use Eddie's **Company Copilot / Company AI Platform subscription** (`https://aoai-farm.bosch-temp.com` and corporate Microsoft 365 Work Account SSO).
   - Supports the verified company models (`gpt-5-nano-2025-08-07`, `askbosch-prod-farm-openai-gpt-4o-mini-2024-07-18`, `gemini-2.5-flash-lite`, `Google Gemini 4 Argon`, `gemini-3.5-flash`, `gemini-2.5-pro`, `gemini-2.5-flash`, `gpt-5-2025-08-07`, `gpt-5.6-sol-2026-07-09`, `claude-sonnet-4-5@20250929`, `claude-opus-4-8`).
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
2. **Confirmation vs. Auto-Execute**:
   - **Preview & Apply button by default**, with an **"Auto-Apply" checkbox toggle** in the sidebar for rapid workflows, plus **Undo Last Change**.
3. **Hybrid Station Modification Protocol**:
   - **Structured JSON Actions** (proven in `C:\_MODEL_FARM` `ProgramImpl.java` and extended for full Workbench views):
     - `createComponent` / `addComponent` (with smart left-to-right Wixel layout: Column 1 `x=10` inputs, Column 2 `x=30` math/logic, Column 3 `x=50` outputs via `BWsAnnotation.make(p, q)`).
     - `setSlotValue` / `setProperty` (handling `BStatusNumeric`, `BStatusBoolean`, `BStatusString`, `BStatusEnum`, and `BSimple.decodeFromString`, plus enforcing `station:|slot:/...` on `BNiagaraProxyExt` `pointId`).
     - `setFacets` (updating units, precision, min/max, trueText/falseText).
     - `link` / `addLink` (with smart auto-alignment of `BWsAnnotation` and explicit `sourceOrd` so wire lines render cleanly on the WireSheet).
     - `removeLink` / `unlink`
     - `renameComponent` / `renameSlot`
     - `deleteComponent` / `deleteSlot`
     - `updateProgram` (updating `BProgram` source code)
     - `updatePxFile` (updating `.px` graphic XML files)
   - **BOG XML Injection (`importBog`)**:
     - Native Niagara `<bajaObjectGraph>` decoding via `ValueDocDecoder` for complex component trees and proxy point folders.

---

## 3. Module Implementation Files (`eddiesWorkbenchAI-wb`)

- `module-permissions.xml` — Grants `NETWORK_COMMUNICATION` (`hosts="*"`, `ports="*"`, `type="all"`, `proxySelector="get"`, `SSLSockets="true"`), `GET_ENVIRONMENT_VARIABLES` (`variables="*"`), and `UI` permissions for outbound AI Gateway & SSO requests and environment variable lookups.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/BEddiesAiTool.java` & `BEddiesAiSideBar.java` — Registered in `module-include.xml` (`Tools -> Eddie's AI Assistant` and `Window -> Side Bars -> Eddie's AI Assistant`). Trigger `EddiesAiShellManager.scheduleInstall()` on startup and allow toggling the right-side AI panel.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiShellManager.java` — Watches active `BWbShell` instances, docks `BEddiesAiRightPanel` into `wbPane.setRight(...)` with a 6px left-edge resize splitter (`BResizableDockPane`), and installs an `"Eddie's AI"` toggle button on the right end of the Workbench toolbar.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/BEddiesAiRightPanel.java` — Main right-side panel controller. Houses the Model selector (Company Copilot / Model Farm deployments + `Google Gemini 4 Argon`), `Account` button, Auth status badge, `Auto-apply station changes` checkbox, Chat Transcript, and Pending Plan action bar (`Preview`, `Apply Changes`, `Undo`).
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiConfig.java` — SecurityManager-safe persistent settings stored at `<niagara_user_home>/eddiesWorkbenchAI.properties` (`safeGetenv` / `safeGetProperty` with `try/catch (Throwable)` guards).
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiAuthManager.java` — Microsoft 365 Work Account Browser SSO (`login.microsoftonline.com` OAuth 2.0 + PKCE loopback) and credential import.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiSettingsDialog.java` — Modal `BDialog` for configuring the Company AI Platform Subscription Key, Subscription ID, Gateway Domain (`https://aoai-farm.bosch-temp.com`), Local Proxy (`127.0.0.1:3128`), 1-click import from an active Station Program Object, and Microsoft 365 Work Account Browser SSO.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/EddiesAiClient.java` — Asynchronous HTTP client for `https://aoai-farm.bosch-temp.com` (`/api/openai/deployments/{model}/chat/completions` & `/api/google/v1/publishers/anthropic/models/{model}:rawPredict`) with required company headers, optional proxy routing, and automatic fallback to `gpt-5-nano-2025-08-07` if an unprovisioned deployment returns HTTP 403/404.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/StationContextExtractor.java` — Extracts live station context from the active Workbench view (`BWbShell.getActiveView()` / `BWbShell.getActiveOrd()`): target `BComponent`, slots, values, `BFacets`, `BLink` wires, child components with `BWsAnnotation` wixel coordinates, multi-selection (`Mark`), `BProgram` source code, and `.px` XML content.
- `src/com/eddieturner/eddiesWorkbenchAI/ui/StationActionExecutor.java` — Parses `<station-actions>` JSON blocks and executes station modifications (`createComponent`/`addComponent`, `setSlotValue`/`setProperty`, `setFacets`, `link`/`addLink`, `unlink`/`removeLink`, `renameComponent`, `deleteComponent`, `invokeAction`, `updateProgram`, `updatePxFile`, and `importBog`) with Preview summaries and 1-click Undo.

