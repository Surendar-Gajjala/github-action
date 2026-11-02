// ILLUSTRATIVE port. A real implementation would use the ECTR client's message/dialog facility.
package com.example.ectr.plugin.api.port;

/** Shows messages to the current user. Messages passed in are already localized. */
public interface UserNotifier {

    void info(String message);

    void warn(String message);

    void error(String message);
}
