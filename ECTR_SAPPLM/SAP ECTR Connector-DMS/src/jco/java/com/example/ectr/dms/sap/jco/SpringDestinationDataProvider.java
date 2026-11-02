// Compiled only with profile "jco". Uses the public JCo 3 extension API
// (com.sap.conn.jco.ext.DestinationDataProvider). Not compiled in this repository because sapjco3.jar is
// licensed by SAP and not available here: compile with -Pjco against your copy before relying on it.
package com.example.ectr.dms.sap.jco;

import com.sap.conn.jco.ext.DestinationDataEventListener;
import com.sap.conn.jco.ext.DestinationDataProvider;

import java.util.Properties;

/**
 * Supplies JCo with connection properties from Spring configuration, so no
 * {@code <destination>.jcoDestination} file with a clear-text password lies on disk.
 */
public class SpringDestinationDataProvider implements DestinationDataProvider {

    private final JcoProperties config;

    public SpringDestinationDataProvider(JcoProperties config) {
        this.config = config;
    }

    @Override
    public Properties getDestinationProperties(String destinationName) {
        if (!config.destinationName().equals(destinationName)) {
            return null; // unknown destination: JCo reports it as not found
        }
        Properties p = new Properties();
        p.setProperty(DestinationDataProvider.JCO_ASHOST, config.ashost());
        p.setProperty(DestinationDataProvider.JCO_SYSNR, config.sysnr());
        p.setProperty(DestinationDataProvider.JCO_CLIENT, config.client());
        p.setProperty(DestinationDataProvider.JCO_USER, config.user());
        p.setProperty(DestinationDataProvider.JCO_PASSWD, config.password());
        p.setProperty(DestinationDataProvider.JCO_LANG, config.language());
        p.setProperty(DestinationDataProvider.JCO_POOL_CAPACITY, String.valueOf(config.poolCapacity()));
        p.setProperty(DestinationDataProvider.JCO_PEAK_LIMIT, String.valueOf(config.peakLimit()));
        return p;
    }

    @Override
    public boolean supportsEvents() {
        return false;
    }

    @Override
    public void setDestinationDataEventListener(DestinationDataEventListener listener) {
        // properties never change at runtime
    }
}
