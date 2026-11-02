// ILLUSTRATIVE: prints to the console. A real adapter would show an ECTR client message or dialog.
package com.example.ectr.plugin.mock;

import com.example.ectr.plugin.api.port.UserNotifier;
import org.osgi.service.component.annotations.Component;

/** Writes user messages to standard output with a severity prefix. */
@Component(service = UserNotifier.class)
public final class ConsoleNotifier implements UserNotifier {

    @Override
    public void info(String message) {
        print("INFO ", message);
    }

    @Override
    public void warn(String message) {
        print("WARN ", message);
    }

    @Override
    public void error(String message) {
        print("ERROR", message);
    }

    private static void print(String level, String message) {
        System.out.println("[user " + level + "] " + message.replace(System.lineSeparator(), System.lineSeparator() + "              "));
    }
}
