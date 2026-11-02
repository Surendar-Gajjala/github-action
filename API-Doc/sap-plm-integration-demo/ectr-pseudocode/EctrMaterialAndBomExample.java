/*
 * ============================================================================
 *  ILLUSTRATIVE PSEUDOCODE - NOT COMPILED, NOT PART OF THE MAVEN BUILD
 * ============================================================================
 *
 * Shows the documented SAP ECTR Connector calls materialCreate(...) and
 * cdeskBomWizard(...) for the MOTOR-100 demo scenario.
 *
 * Source of the method names and usage pattern (SAP Help Portal, SAP ECTR):
 *   - "How to create materials with ECTR-Connector"
 *       https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506450.html
 *   - "How to maintain BOM with ECTR-Connector"
 *       https://help.sap.com/docs/SUPPORT_CONTENT/plm/3363506441.html
 *
 * It only compiles and runs if ALL of these are true:
 *   - the ECTR toolkit for YOUR ECTR release is on the classpath
 *     (Javadoc: <ECTR_INSTALLATION>/basis/toolkit/<CONNECTOR_ZIP>/java/javadoc/
 *      com/dscsag/plm/comm/PlmConnector.html)
 *   - you have a connected PlmConnector instance (how you obtain it is described
 *     in your toolkit documentation; it is not shown here on purpose)
 *   - an application type and license key exist for your integration
 *   - ECTR is running and logged on to SAP, and SAP is configured (material type,
 *     plant 1000, BOM usage, document types, ...)
 *
 * Imports are omitted because package names must be taken from your toolkit Javadoc.
 * The CAD file path is an example value.
 */
public class EctrMaterialAndBomExample {

    /** Connected ECTR Connector. Obtained via your toolkit; NOT shown here. */
    private final PlmConnector conn;

    public EctrMaterialAndBomExample(PlmConnector conn) {
        this.conn = conn;
    }

    /**
     * Step 1: create the material MOTOR-100 and link it to the checked-in CAD document.
     * Pattern from "How to create materials with ECTR-Connector".
     */
    public PlmMaterial createMotorMaterial(String checkedInCadFile) throws Throwable {
        PlmMaterialImpl mat = new PlmMaterialImpl();
        mat.setNumber("MOTOR-100");            // or leave empty for internal numbering, depending on configuration
        mat.setDescription("Motor assembly, rev B");

        // Passing the CAD part creates the object link Material <-> Document Info Record.
        PlmRequestPart part = new PlmRequestPartImpl(checkedInCadFile);
        return conn.materialCreate(mat, part);
    }

    /**
     * Step 2: maintain the BOM of MOTOR-100 with SHAFT-101 x1 and BEARING-201 x2.
     * Pattern from "How to maintain BOM with ECTR-Connector".
     *
     * Note from the SAP page: each item carries a persistent instance ID that must
     * survive a component replace in CAD, and optionally a position ID. How you set
     * them depends on your toolkit version, so check PlmBomItem in your Javadoc.
     */
    public void maintainMotorBom(String checkedInCadFile, PlmMaterial motor) throws Throwable {
        PlmRequestPart part = new PlmRequestPartImpl(checkedInCadFile);

        PlmMaterialImpl head = new PlmMaterialImpl();
        head.setNumber(motor.getNumber());

        Collection<PlmBomItem> items = new ArrayList<>();

        PlmBomItemImp shaft = new PlmBomItemImp();
        shaft.setQuantity(1);
        shaft.setMatNumber("SHAFT-101");
        items.add(shaft);

        PlmBomItemImp bearing = new PlmBomItemImp();
        bearing.setQuantity(2);
        bearing.setMatNumber("BEARING-201");
        items.add(bearing);

        conn.cdeskBomWizard(part, head, items);

        // The BOM now exists in SAP, but it is NOT automatically released for production.
        // Release follows your change-number / status process in SAP.
    }

    /** Example flow. Exceptions must be handled and shown to the user in a real plug-in. */
    public void run() throws Throwable {
        String cadFile = "C:\\work\\ectr-session\\motor-100.asm";   // example path only
        PlmMaterial motor = createMotorMaterial(cadFile);
        maintainMotorBom(cadFile, motor);
    }
}
