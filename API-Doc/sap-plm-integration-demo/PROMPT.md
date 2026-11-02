SAP PLM Integration Sample Project — Developer Prompt
Goal
Build a learning demo that explains two different integration architectures:
1. CAD → SAP ECTR → SAP backend (ECC or S/4HANA).
2. External PLM → integration layer → SAP ERP/S/4HANA.
The demo must clearly distinguish these architectures. Do not present them as the same integration pattern.
Important constraints
- This is a learning/sample project, not a production-ready SAP integration.
- Do not invent undocumented SAP ECTR API methods, SAP endpoints, payload schemas, or authentication flows.
- Clearly label all mock APIs and mock responses.
- Use real ECTR Connector method names only where documented by the supplied notes, such as materialCreate(...) and cdeskBomWizard(...).
- The ECTR Connector is a client-side integration API, not automatically a REST API that a browser can call.
- A real ECTR integration requires the matching ECTR toolkit/Javadoc, supported version, configuration, application type, document types, licensing, and SAP connectivity.
- External PLM-to-ERP integration requires an interface supported by the actual PLM and SAP versions. Treat REST/OData, IDoc, RFC, middleware, or SAP integration add-ons as alternatives selected for the real environment—not as universally available.
- Never put credentials in frontend code or source control. Use environment variables or a secrets manager.
- Include input validation, clear error handling, correlation IDs, duplicate-submission protection, and transfer status tracking in the mock architecture.
- Explain that creating a CAD document, SAP material, document structure (BoD), and manufacturing BOM are distinct operations. A CAD assembly does not automatically become a released manufacturing BOM.
Recommended stack
- Java 17+ and Spring Boot for a mock integration service.
- React + TypeScript for an optional demonstration UI.
- In-memory mock adapters by default; no SAP connection is required to run the demo.
Business scenario
A motor assembly MOTOR-100, revision B, plant 1000 contains:
- SHAFT-101, quantity 1
- BEARING-201, quantity 2
Architecture A: CAD → SAP ECTR → SAP backend
Explain:
- CAD authoring tool and CAD-specific plug-in.
- ECTR Connector API for custom authoring-tool integration.
- ECTR OSGi API for extending the ECTR Java frontend.
- ECTR-to-SAP backend communication (including RFC where configured).
- Document Info Records, original files/content repository, material master, BoD, BOM, and engineering-change/release processes.
Provide:
1. An architecture diagram in Mermaid.
2. A sequence diagram for creating/checking in a CAD document and then creating/linking a material and maintaining a BOM.
3. A small Java example showing the documented ECTR Connector usage for materialCreate(...) and cdeskBomWizard(...). Mark it as illustrative pseudocode unless all toolkit types and setup are available.
4. Explain prerequisites: supported ECTR/CAD/SAP versions, Product Availability Matrix, toolkit/Javadoc, application type, license, document types, SAP configuration, and credentials/connectivity.
5. Explain typical failures and where to inspect logs.
Architecture B: External PLM → integration layer → SAP ERP/S/4HANA
Explain:
- The source PLM owns engineering data and release state.
- An integration adapter validates and maps the source BOM into the target SAP interface contract.
- The SAP adapter calls a configured SAP integration interface.
- The integration records transfer state and SAP response identifiers.
- Explain synchronous API vs asynchronous/middleware alternatives without asserting a particular one is configured.
Provide:
1. An architecture diagram in Mermaid.
2. Spring Boot code with interfaces such as PlmBomSource, SapBomGateway, BomMapper, and BomTransferService.
3. A mock SapBomGateway implementation so the demo runs without SAP.
4. A clearly marked placeholder for the real SAP adapter, with a note to implement it using the SAP/PLM version's supported API and payload schema.
5. Request/response JSON examples.
6. Error handling, retry rules for transient failures, idempotency, validation, and status tracking.
7. Do not use a fake endpoint and imply it is a standard SAP endpoint.
Deliverables
- Project folder structure.
- Key Java source files.
- application.yml using environment-variable placeholders.
- .env.example containing placeholder names only, never secrets.
- API request/response examples.
- Mermaid architecture and sequence diagrams.
- README.md explaining how to run in mock mode and how to replace mock adapters.
- PROMPT.md containing this entire prompt so it can be reused with a coding assistant.
- Testing examples for release validation, invalid quantities, SAP failure, and duplicate submissions.
Quality requirements
- Keep business logic independent from SAP-specific transport code.
- Use DTOs for external contracts and internal domain models.
- Make mock vs real integration explicit in class names and documentation.
- Never claim the sample creates real SAP records until connected to and verified against the actual SAP environment.