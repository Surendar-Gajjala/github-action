/**
 * Ports: the only boundary between plug-in logic and the host application.
 * <p>
 * ILLUSTRATIVE: these interfaces are OUR abstraction, designed so that a real SAP ECTR
 * adapter can implement or call them. They are NOT SAP ECTR API types, and the names do not
 * claim to match ECTR extension points. In this project they are implemented by the
 * {@code ectr-plugin-mock} bundle.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("1.0.0")
package com.example.ectr.plugin.api.port;
