# SAP Engineering Control Center (SAP ECTR) – Integration Details

**Source page:** [SAP ECTR – SAP Help Portal (Support Content / PLM)](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363507057.html?locale=en-US)
**Also compiled from these linked pages:** Overview – SAP ECTR, SAP ECTR Connector API Documentation, SAP ECTR OSGi API Documentation, the OSGi services pages, the ECTR-Connector how-to pages, Interface-specific topics, and the Product Availability Matrix.
**Compiled:** 2026-10-22

---

## 1. What SAP ECTR Is

SAP Engineering Control Center (SAP ECTR) is SAP's strategic platform for **integrating authoring tools** such as mechanical (MCAD) and electrical/electronic (ECAD) CAD systems with SAP.

| Item | Detail |
|---|---|
| Supported back ends | SAP ECC 6.0 **EHP7 and higher**, and SAP **S/4HANA 1809 and later** |
| Product lines | *SAP ECTR 5.x* (on ECC) and *SAP ECTR 1.x for SAP S/4HANA* |
| Core concept | Transfer CAD hierarchies into a **Bill of Documents (BOD)**, then derive the **Bill of Material (BOM)** |
| File storage | SAP Content Server. Optionally, SAP Document Management Service on BTP can be the file repository |

---

## 2. Integration Architecture

```
┌──────────────────────── Front-end computer ─────────────────────────┐
│                                                                      │
│  ┌──────────────────┐   plugin    ┌──────────────┐                   │
│  │ Authoring tool   │◄──────────► │ ECTR plug-in │                   │
│  │ (CAD system)     │  menus/     │ (per CAD)    │                   │
│  └──────────────────┘  ribbon     └──────┬───────┘                   │
│                                          │ files, metadata,          │
│                                          │ structure info            │
│                              ┌───────────▼────────────┐              │
│                              │  ECTR Connector (API)  │ ◄─ custom     │
│                              └───────────┬────────────┘   integrations│
│                              ┌───────────▼────────────┐              │
│                              │   SAP ECTR front end   │◄─ OSGi       │
│                              │   (Java client)        │   extensions │
│                              └───────────┬────────────┘              │
└──────────────────────────────────────────┼───────────────────────────┘
                                           │ RFC
               ┌───────────────────────────┼─────────────────────────┐
               ▼                                                     ▼
   ┌───────────────────────────┐                     ┌──────────────────────────┐
   │ SAP back end              │                     │ SAP Content Server       │
   │ (S/4HANA or ECC 6.0)      │                     │ (or BTP DMS repository)  │
   │ DIR, Material, BOM, ECM   │                     │ CAD originals            │
   └───────────────────────────┘                     └──────────────────────────┘
```

**How the pieces work together**
1. The **authoring tool** (CAD system) is installed on the front-end computer.
2. A **plug-in** adds menu items, a ribbon and other UI elements to the authoring tool.
3. The plug-in talks to the **local ECTR client** to exchange files, metadata and structure information.
4. **ECTR** handles communication with the **SAP back end** (S/4HANA / ECC 6.0) and the **SAP Content Server**.
5. For new or custom tools, the **ECTR Connector** (a front-end component) sits **between the authoring-tool plug-in and the ECTR front end**.

---

## 3. Integration Options

| # | Option | Use it for | Technology |
|---|---|---|---|
| 1 | **Standard SAP ECTR interfaces** | Supported CAD tools (Section 4) | Plug-in delivered by SAP |
| 2 | **SAP ECTR Connector (PLM API)** | Building a *new* authoring-tool integration | Java / .NET (C#, VB.NET) client library |
| 3 | **SAP ECTR OSGi API** | Extending the ECTR front end (custom actions, tabs, windows, file handling) | Java OSGi bundles (`plm-api.jar`) |
| 4 | **RFC through the Connector or OSGi `RfcExecutor`** | Calling SAP function modules from the integration | RFC (`/DSCSAG/*` function modules) |

> ⚠️ **Do not use the API directly when an integration already exists for that application.** Contact the provider instead.

---

## 4. Standard Integrated Authoring Tools

**Mechanical CAD (MCAD)**

| CAD system | PAM / version info | Release Note |
|---|---|---|
| Autodesk AutoCAD | [ectr-autocad](https://support.sap.com/infopages/swdc/plm/ectr-autocad.html) | 2126092 (KBA 3146424) |
| Autodesk Inventor | [ectr_invent](https://support.sap.com/infopages/swdc/plm/ectr_invent.html) | 2126093 (KBA 3142760) |
| CATIA V5 | [ectr_catia5](https://support.sap.com/infopages/swdc/plm/ectr_catia5.html) | 2283087 |
| Siemens NX | [ectrint_nx5](https://support.sap.com/infopages/swdc/plm/ectrint_nx5.html) | 2806807 (5.2), 2233704 (5.1) |
| PTC Creo | [ectrintcreo](https://support.sap.com/infopages/swdc/plm/ectrintcreo.html) | 2112629 |
| Solid Edge | [ectr_edge](https://support.sap.com/infopages/swdc/plm/ectr_edge.html) | 2126094 (KBA 3146453) |
| SOLIDWORKS | [ectr_solid](https://support.sap.com/infopages/swdc/plm/ectr_solid.html) | 2126095 (KBA 3146468) |

**Electrical / Electronic CAD (ECAD)**

| CAD system | PAM / version info | Release Note |
|---|---|---|
| Altium Designer | [ectr_ecad10](https://support.sap.com/infopages/swdc/plm/ectr_ecad10.html) | 2406949 |
| Cadence Allegro Design Entry CIS + Allegro PCB Editor | [ectr_ecad10](https://support.sap.com/infopages/swdc/plm/ectr_ecad10.html) | 2406949 |
| Cadence OrCAD Schematic CIS + OrCAD Editor | [ectr_ecad10](https://support.sap.com/infopages/swdc/plm/ectr_ecad10.html) | 2406949 |
| Mentor Xpedition xDX-Designer + xPCB-Layout | [ectr_ecad10](https://support.sap.com/infopages/swdc/plm/ectr_ecad10.html) | 2406949 |
| Mentor PADS Logic / PADS DX Designer + PADS Layout | [ectr_ecad10](https://support.sap.com/infopages/swdc/plm/ectr_ecad10.html) | 2406949 |
| EPLAN P8 | [ectr_eplan](https://support.sap.com/infopages/swdc/plm/ectr_eplan.html) | 2212536 (KBA 3125352) |
| ZUKEN | [ZUKEN page](https://support.sap.com/launchpad/en/swdc/plm/73554900100800003562.html) | — |

**Office:** the MS Office integration is described in the ECTR installation guide, chapter 2.1 ([ECC 6.0](https://help.sap.com/viewer/product/SAP_ENGINEERING_CONTROL_CENTER) / [S/4HANA](https://help.sap.com/viewer/product/SAP_ENGINEERING_CONTROL_CENTER_S4HANA)).

> The **Product Availability Matrix (PAM)** shows which CAD release, ECTR interface release, ECTR release, SAP GUI release and OS release work together. The "End of mainstream maintenance" date for an interface is usually at most one year out. It is either extended or replaced by a newer release under "Upgrade to".

---

## 5. SAP ECTR Connector (PLM API) – Custom Authoring-Tool Integration

### 5.1 Purpose
The Connector lets an external application call SAP functions such as **check in**, **check out**, create documents, create materials and maintain BOMs. ECTR runs these as processes, some of which ask the user for input. The main processes are **fully encapsulated as methods**, so you don't need to know the underlying XML communication.

### 5.2 Prerequisites and Setup
| Item | Detail |
|---|---|
| **Application type** | Every connected application needs its own **application type**. It matches the application's directory name under ECTR's `applications` folder (for example `UGS` for NX). For a new application, define your own abbreviation (`[PRIAPPLTYPE]`), create the application directory and generate a license key in ECTR Customizing. |
| **Document types** | Document types such as `UGM`/`UGD` (NX examples) and workstation applications (for example `UGI`) depend on configuration and must be set up in your SAP system. |
| **Toolkit** | Since ECTR **5.1.13.0**, the toolkit is always installed to `<ECTR_INSTALLATION>\basis\toolkit`. On older versions, run the front-end installer with the `/InstallToolkit` parameter. |
| **Javadoc** | `<ECTR_INSTALLATION>/basis/toolkit/<CONNECTOR_ZIP>/java/javadoc/com/dscsag/plm/comm/PlmConnector.html` |
| **Licensing** | Custom integrations built on the Connector are licensed as **SAP ECTR Professional (Unit ID 7523)**. See SAP Note **2096135**. |

### 5.3 Key Classes
| Class / Interface | Role |
|---|---|
| `PlmConnector` (`com.dscsag.plm.comm`) | Main entry point (`conn`) |
| `PlmRequestPart` / `PlmRequestPartImpl` | A CAD file/part sent to ECTR (filename, `PartType`, `PartLoadState`, flags) |
| `PlmResponse` / `PlmResponsePart` | ECTR's reply. Each part carries a `PartAction` (`OPEN_DISPLAY`, `SET_RW`, `SET_RO`, …) |
| `PlmMetaInfoCadImpl` | CAD metadata: attributes, components/references, application release |
| `PlmMetaInfoComponentImpl` | A component reference (for example drawing → model) |
| `PlmAttributeNames` | Standard attribute keys (for example `DOCUMENT_DESCR`, `DOCUMENT_DTYPE`) |
| `PlmMaterialImpl` | Material master data (number, external number, description, extra data) |
| `PlmBomItemImp` | A BOM item (quantity, material number) |
| `PlmCreateDocumentsRequest` | Request for document creation (import-on-demand) |
| `SaveOption` | For example `SAVECHECKIN` |
| `IRfcCallBuilder`, `RfcTableBuilder`, `RfcStructureBuilder`, `IRfcResult` | Build and run RFC calls (`Connector.RfcExecutor.Execute`) |

### 5.4 Main API Methods and Flows

| Use case | Method(s) | Flow summary |
|---|---|---|
| **Respond to open commands** (`connect` / `connectonly`) | `getPlmResponse(loadedParts)` | The user opens a document in ECTR. ECTR calls the start exe with `connect` (starts the CAD tool if it isn't running) or `connectonly` (does nothing if the CAD tool isn't running). The CAD tool calls `getPlmResponse` and handles each part's action, for example `OPEN_DISPLAY` → open the file. |
| **Check out** | `checkForUpdates(loaded, parts)` → `provideDocuments(loaded, missing)` → `checkoutEdit(loaded, parts)` | ECTR finds missing or out-of-date files, provides them in the session directory, checks for conflicts and opens the lock. The CAD tool gets `SET_RW` and changes the access mode. |
| **Save / check in** | `checkReadonly(loaded, ro)` → `getAdditionalDirectory(part)` → `save(loaded, SaveOption.SAVECHECKIN, rw)` | ECTR shows the Check-in Assistant and returns `SET_RW`. The CAD tool writes metaInf and the file, then creates a preview in the additional directory. ECTR checks in and closes the lock, and returns `SET_RO`. |
| **Create new documents** (import on demand) | `createDocuments(files, metadata)` → `writeMetaInfoCad(meta, file)` → `save(loaded, parts, SAVECHECKIN)` | ECTR creates DIRs in SAP and returns DIR keys plus old/new filenames. The CAD tool saves under the new name. ECTR checks in the file, maintains the DIR structure and transfers attributes. |
| **Create material** | `materialCreate(mat [, part])` | Saves the material in SAP and optionally links it to the DIR (object link). |
| **Maintain BOM** | `cdeskBomWizard(part, materialHead, items)` | Sends the BOM header and items, each with a persistent instance ID and an optional position ID, to SAP. |
| **Show ECTR UI** | `showPlmGui()` | Brings up the ECTR UI. |
| **Run a custom ECTR process** | `callPlmProcess(...)` | Calls a process implemented through the OSGi `PluginProcessService`. |
| **Call SAP RFC** | `Connector.RfcExecutor.Execute(rfcCall)` | See Section 7. |

Further how-to topics (all on the source portal): create master documents, create variants, load seed files, provide files of existing documents, show a generic PLM object in the Application Structure window, handle drag-and-drop events in the Application Structure, and VB.NET sample code.

### 5.5 Sample – Check Out (Java)
```java
public static void checkoutEdit(List<String> parts) throws PlmConnectorException {
    List<PlmRequestPart> listParts = createRequestParts(parts);
    List<PlmRequestPart> loadedParts = listParts;
    PlmResponse response = conn.checkoutEdit(loadedParts, listParts);
    evaluatePlmResponse(response);
}
```

### 5.6 Sample – Handle `connect` (Java)
```java
PlmResponse response = conn.getPlmResponse(loadedParts);
List<PlmResponsePart> parts = response.getParts();
if (parts != null) {
    setLoadOptionsInApplication(response.getLoadOptions());
    for (PlmResponsePart part : parts) {
        switch (part.getAction()) {
            case OPEN_DISPLAY: /* open part.getFilename() in the CAD application */ break;
            default: logger.error("unhandled Action for open Files");
        }
    }
}
```

### 5.7 Sample – Create Material and BOM (Java)
```java
PlmMaterialImpl mat = new PlmMaterialImpl();
mat.setNumber("00000004711");
mat.setDescription("mat desc");
PlmMaterial created = conn.materialCreate(mat, new PlmRequestPartImpl(filename));

PlmBomItemImp item = new PlmBomItemImp();
item.setQuantity(1);
item.setMatNumber("4711");
conn.cdeskBomWizard(new PlmRequestPartImpl("C:\\test\\test.prt"), mat, List.of(item));
```

---

## 6. SAP ECTR OSGi API – Front-End Extensions

OSGi was introduced with **SAP ECTR 5.1** to provide a standard API for Java extensions of the ECTR front end. Customers and partners can implement extensions.

| Item | Location |
|---|---|
| JAR | `<ECTR_INSTALLATION>/basis/classes/plm-api.jar` |
| Javadoc | `<ECTR_INSTALLATION>/basis/toolkit/plm-api-javadoc.zip` |

### 6.1 OSGi Services *Provided* by ECTR (for your extension to use)
| Service | Purpose | Since |
|---|---|---|
| `RfcExecutor` | Run RFC modules on the connected back end | 5.1.1.0 |
| `PlmLogger` | Write to the PLM log file | 5.1.1.0 |
| `PlmDictionary` | Access the PLM dictionary | 5.1.5.0 |
| `PlmEnvironment` | Access the PLM environment | 5.1.5.0 |
| `PlmLogonData` | Back-end connection info (destination, user, …) | 5.1.5.0 |
| `PlmPreferences` | Access preferences | 5.1.8.1 |
| `ResourceAccessor` | Icons and images | 5.1.15.0 |
| `ObjectListService` | Manage object lists | 5.2.3.0 |
| `DisplayTableSelectionDialogService` | Object selection dialog | 5.2.4.0 |
| `NotificationBroadcastingService` | Notify the user about new SAP inbox messages | 5.2.5.0 |
| `ExpandObjectList`, `GetObjectListNames` | Expand object lists and list their names | 5.2.6.0 |
| `PlmCacheController` | Clear the cache (use sparingly, it affects performance) | 5.2.6.0 |
| `OperationCreateService` | Extend operations in `attributes-from-sap.xml` / `attributes-to-sap.xml` | 5.2.6.0 |
| `ThumbnailAccessor` | Object thumbnails | S4 1.2.0.0 / 5.2.7.0 |
| `CheckoutSetApplFilesService` | Set a document to checked out ("set in work") | S4 1.2.0.0 / 5.2.7.0 |
| `ProvideApplFileLocalInfoService` | Local file state | S4 1.2.0.0 / 5.2.7.0 |
| `TransferApplFilesService` | Transfer application files into the local session | S4 1.2.0.0 / 5.2.7.0 |
| `ProvideApplFileUptodateInfoService` | Check whether files are local and up to date | S4 1.2.0.0 / 5.2.7.0 |
| `ProvideDocumentInfoService` | Document details | S4 1.2.0.0 / 5.2.7.0 |
| `ProvideDocumentStructureService` | Resolve document structures | S4 1.2.0.0 / 5.2.7.0 |
| `TransferOriginalsService` | Download all originals of a DIR to a local directory | S4 1.2.1.0 (plm-api 5.2.10.0) |

### 6.2 OSGi Services *Consumed* by ECTR (for your extension to implement)
| Service | Purpose | Since |
|---|---|---|
| `ObjectExtensionService` | Edit fields (description, long text) of PLM objects in dialogs and the Object Browser | 5.1.1.0 |
| `ApplicationFileHandler` | Called whenever files are provided to or removed from the working directory | 5.1.4.0 |
| `PluginProcessService` | Custom processes, callable from the CAD tool via `callPlmProcess` | 5.1.5.0 |
| `PluginFunctionService` | Custom user actions in ECTR | 5.1.5.0 |
| `PluginContext` | Custom context windows | 5.1.5.0 |
| `PluginObjectBrowserTabService` | Custom Object Browser tabs | 5.1.9.0 |
| `LicenseChecker` | Control which users or groups get which licenses | 5.1.13.0 |
| `ApplCreateDocumentsExtensionService` | Influence import-on-demand processes | 5.1.15.0 |
| `SearchProvider` | Add search providers to ECTR Quick Search | 5.2.2.0 |
| `ShutdownHandler` | Run logic on front-end shutdown | 5.2.3.0 |

**Naming rules:** add-on names must not contain spaces. Allowed characters are `[A-Za-z0-9,-+.]`. Use reverse-domain Java package names (for example `com.example.myaddon`). Preference names follow a similar convention, for example `PRO.delete.PTCCreoUniteTechnologyFiles = CAT;SLW;UGS;PCE`.

### 6.3 ECTR ↔ plm-api Version Mapping
Before 5.2.6.3, the ECTR version equals the plm-api version.

| ECTR version | plm-api version |
|---|---|
| 5.2.6.3 – 5.2.6.6 | 5.2.6.3 |
| 5.2.7.0, 5.2.7.1 | 5.2.7.0 |
| S4 1.2.0.0, S4 1.2.0.1 | 5.2.9.0 |
| S4 1.2.1.0 | 5.2.10.0 |

**OSGi how-tos on the portal:** special file handling after check-out of originals, custom Object Browser tab, custom user action, helper for input values, custom window.

---

## 7. Back-End RFC Integration

ECTR talks to SAP through RFC function modules in the **`/DSCSAG/`** namespace. Integrations can call them through the Connector's `RfcExecutor` (or the OSGi `RfcExecutor` service).

| Function module | Purpose | Key parameters |
|---|---|---|
| `/DSCSAG/FOL4_OPEN` | Open a folder in the ECTR desktop context | IN: `IV_DSC_CONV_ID`, `IT_CONTAINER_OBJECTS` (`/DSCSAG/OBJECT`: `DOKOB`, `OBJKY`, `TAB_INDEX`), `IT_PARAMETERS` (`/DSCSAG/SET_NAME_VALUE`, for example `LOADOPTION=LV`). OUT: `RETURN` (`BAPIRET2`), `EV_OBJECTCOUNT`, `ET_CONTAINER_OBJECTS` |
| `/DSCSAG/CLASS_SEARCH_OBJECTS` | Search objects by classification | `CLASSNAME`, `CLASSTYPE` (for example `017` = documents), `COUNT_ONLY`, `FROM_TOP_CLASS_ONLY`, a characteristics table (`NAME_CHAR`, `CHAR_VALUE`) |

**Sample – RFC call (VB.NET)**
```vb
Dim rfcCallBuilder As IRfcCallBuilder = New RfcCallBuilder("/DSCSAG/FOL4_OPEN")
Dim tbl As RfcTableBuilder = New RfcTableBuilder("DOKOB", "OBJKY", "TAB_INDEX")
Dim row As RfcStructureBuilder = New RfcStructureBuilder("DOKOB", "OBJKY", "TAB_INDEX")
row.SetValue("DOKOB", "DRAW")
row.SetValue("OBJKY", "FOLUSERTESTABC_06330 00000")
row.SetValue("TAB_INDEX", "0")
tbl.AddRow(row.ToRfcStructure())
rfcCallBuilder.AddTable("IT_CONTAINER_OBJECTS", tbl.ToRfcTable())
rfcCallBuilder.CommitAndWait = True

Dim res As IRfcResult = Connector.RfcExecutor.Execute(rfcCallBuilder.ToRfcCall)
If res.IsSuccessful Then
    Dim ret As IRfcTable = res.GetTable("ET_CONTAINER_OBJECTS")
    ' iterate ret.GetRow(i).GetFieldValue("DOKOB" / "OBJKY" / "TAB_INDEX")
Else
    MsgBox("ERROR: " + res.ErrorMessage)
End If
```

> Tip: the ECTR **API log** records each RFC call with its inputs, outputs and runtime. It is the easiest way to find the parameters a function module expects.

---

## 8. SAP Objects Involved

| SAP object | Role in the integration |
|---|---|
| **Document Info Record (DIR)**, object type `DRAW` | Each CAD file/original is stored as a DIR. Originals live on the Content Server. |
| **Bill of Documents (BOD)** | Mirrors the CAD assembly structure |
| **Material master** | Created and linked to the DIR (`materialCreate`) |
| **Bill of Material (BOM)** | Derived from the BOD (`cdeskBomWizard`, BOM/BOS transfer) |
| **Classification** | Attribute storage and search (`/DSCSAG/CLASS_SEARCH_OBJECTS`) |
| **Attribute mapping** | `attributes-to-sap.xml` / `attributes-from-sap.xml` control CAD ↔ SAP attribute transfer |

---

## 9. Configuration Topics (Best Practices on the Portal)

- Distribute ECTR configurations through the SAP system ("Backend Config")
- Use SAP Document Management Service on BTP as the file repository
- Send CAD files to cloud conversion
- Configure ECTR authentication for SAP IPD capabilities (ECTR 1.2.1.0+), and external collaboration with IPD Design Items
- Create materials with internal or external numbers
- BOM/BOS transfer, load scenarios and partial load scenarios, shattered viewing
- Embedded Search, Access Control Management (ACM) in S/4HANA, digital signature on status change
- Prevent document create/edit outside ECTR
- Interface-specific topics for AutoCAD, NX, PTC Creo (cloning, family tables, formats) and SOLIDWORKS (configurations, viewables, mass import)

---

## 10. Release Strategy, Notes and Resources

| Topic | Reference |
|---|---|
| Release strategy / SP planning | [20 Release Strategy and Planning of Support Packages](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506056.html), SAP Note **2949165** |
| SAP Notes for ECTR | [SAP Notes with respect to SAP ECTR](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363507527.html) |
| Licensing of custom integrations | SAP Note **2096135** |
| Influence / improvement requests | [SAP Influence campaign 770](https://influence.sap.com/sap/ino/#/campaign/770), SAP Note **2307014** |
| **Critical note** | **3503146** – Cache Server caches files with the wrong content (26 Aug 2024) |
| Official documentation | [SAP ECTR for S/4HANA](https://help.sap.com/viewer/product/SAP_ENGINEERING_CONTROL_CENTER_S4HANA), [SAP ECTR](https://help.sap.com/viewer/product/SAP_ENGINEERING_CONTROL_CENTER) |
| Connector API | [SAP ECTR Connector – API Documentation](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506386.html) |
| OSGi API | [SAP ECTR OSGi – API Documentation](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363507638.html) |
| Product Availability Matrix | [PAM for SAP ECTR](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363505406.html) |
| What's New | [ECTR 1.3 for S/4HANA](https://help.sap.com/docs/SUPPORT_CONTENT/plm/5327150206.html), [1.2](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3611335652.html), [5.2 / 1.1](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363504442.html), [5.1 / 1.0](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506322.html) |
| Product page / Learning | [sap.com/ectr](https://www.sap.com/ectr), [learning.sap.com – Using ECTR](https://learning.sap.com/learning-journeys/implementing-document-management-in-sap-s-4hana-cloud-private-edition/using-the-engineering-control-center) |
| Roadmap | [SAP Road Map Explorer – ECTR](https://roadmaps.sap.com/board?BC=6EAE8B27FCC11ED892E9186EE86080CC&range=FIRST-LAST) |
| Community | [SAP Community – ECTR](https://pages.community.sap.com/topics/engineering-control-center), [Ask a question](https://answers.sap.com/tags/67838200100800006788) |
| Videos | [YouTube playlist](https://url.sap/ojkw5h), [ECTR Video Library – Release 1.3.0.0](https://dam.sap.com/mac/app/p/assets/digital_supply_chain__design/9ufxTRf) |
| FAQ | [FAQ – SAP ECTR](https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506477.html) |

---

## 11. Quick Checklist for a New Integration

1. Check the **PAM**: is there already a standard ECTR interface for your tool? If so, use it and don't build on the API.
2. Confirm **licensing**: SAP ECTR Professional, Unit ID 7523 (Note 2096135).
3. Set up the **SAP side**: document types, workstation application, Content Server, attribute mapping.
4. Define the **application type** in ECTR Customizing: application directory plus license key.
5. Install the **toolkit** (automatic since 5.1.13.0) and study the `PlmConnector` Javadoc.
6. Implement the core flows: `connect`/`getPlmResponse` → `checkoutEdit` → `save(SAVECHECKIN)` → `createDocuments` → `materialCreate` / `cdeskBomWizard`.
7. Use **OSGi services** for front-end extensions and **`RfcExecutor`** for direct back-end calls.
8. Match the **plm-api version** to your ECTR release (Section 6.3).
