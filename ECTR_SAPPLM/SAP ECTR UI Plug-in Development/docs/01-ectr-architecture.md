# 1. SAP ECTR architecture and its role in SAP PLM and CAD integration

> **Verified vs illustrative.** This chapter describes what ECTR does at a level that matches SAP's public product description. It does **not** describe ECTR internals such as class names, process layout or configuration keys. Check those against the SAP Help Portal documentation for the customer's ECTR release ("SAP Engineering Control Center").

## What ECTR is

SAP Engineering Control Center (ECTR) is a **desktop client** that sits between CAD authoring tools and the SAP PLM backend. Engineers use it to:

- manage CAD models, drawings and other engineering documents as **SAP document info records (DMS)**
- link those documents to **material masters**, and create or update **BOMs** from the CAD structure
- run check-in/check-out, versioning, status changes and **engineering change** processes without leaving the CAD environment
- browse PLM objects and their relationships in a tree/list UI, referred to in this project as the **Object Browser**

## Where it fits

```
 ┌────────────────────┐   CAD integration    ┌─────────────────────────────┐    RFC     ┌──────────────────────┐
 │ CAD tool           │ ◄──────────────────► │ SAP ECTR desktop client      │ ◄────────► │ SAP backend (ERP/S4) │
 │ (NX, Creo, CATIA,  │   (per-CAD interface)│  - Object Browser / UI       │            │  - DMS documents     │
 │  SOLIDWORKS, ...)  │                      │  - CAD structure handling    │            │  - Material master   │
 └────────────────────┘                      │  - customer extensions  ◄─── this project  │  - BOM, ECM, status  │
                                             └─────────────────────────────┘            └──────────────────────┘
```

Which CAD integrations are available, and how ECTR talks to SAP, depends on the release and on the licences installed. Confirm the specific set for your customer.

## Why customers extend ECTR

Standard ECTR handles the generic PLM workflow. Automotive customers usually add their own business rules on top, for example:

| Need | In this project |
|---|---|
| Stop incomplete data from reaching SAP | `ValidationSaveInterceptor`: mandatory fields, formats, required languages |
| Guide the user when they drag objects | `ObjectBrowserDropHandler`: allowed targets, cycle check, validation |
| Automate repetitive packaging work | `PackagingRuleEngine`: material-group packages, drawings, size limits |
| Global teams | Multilingual descriptions and messages (`LocalizedText`, `messages_*.properties`) |

## Key PLM terms used in this guide

| Term | Meaning here |
|---|---|
| Engineering object | Anything ECTR manages: a part, an assembly, a drawing. Modelled as `EngineeringObject`. |
| Metadata / properties | Attributes such as material group, weight and revision. They are addressed by our **logical** keys (`PropertyKey`), never by SAP field names. |
| CAD structure | Parent/child tree of an assembly with quantities. Modelled as `CadStructureNode`. |
| Packaging | Grouping an assembly's components (and their drawings) into packages for downstream processes such as logistics or supplier hand-over. The rules are customer-specific. |

## Next

[Chapter 2](02-osgi-fundamentals.md) explains the OSGi concepts this project uses.
