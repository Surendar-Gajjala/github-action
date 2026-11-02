// ILLUSTRATIVE: fixed user and switchable language. A real adapter reads them from the ECTR client.
package com.example.ectr.plugin.mock;

import com.example.ectr.plugin.api.port.EctrSession;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.Locale;

/** Session whose language can be changed at runtime with the {@code ectr:lang} shell command. */
@Component(service = {EctrSession.class, MockEctrSession.class}, configurationPid = MockEctrSession.PID)
@Designate(ocd = MockEctrSession.Config.class)
public final class MockEctrSession implements EctrSession {

    public static final String PID = "com.example.ectr.plugin.mock.session";

    @ObjectClassDefinition(name = "ECTR plug-in mock: session")
    public @interface Config {

        @AttributeDefinition(name = "User id")
        String userId() default "DEMO_USER";

        @AttributeDefinition(name = "Language", description = "BCP 47 language tag, for example en or de")
        String language() default "en";
    }

    private final String userId;
    private volatile Locale locale;

    @Activate
    public MockEctrSession(Config config) {
        this.userId = config.userId();
        this.locale = Locale.forLanguageTag(config.language());
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public Locale locale() {
        return locale;
    }

    void setLocale(Locale locale) {
        this.locale = locale;
    }
}
