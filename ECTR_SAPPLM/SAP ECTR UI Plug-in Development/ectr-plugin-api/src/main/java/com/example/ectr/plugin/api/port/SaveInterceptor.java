// ILLUSTRATIVE port. The host (real ECTR adapter or the mock shell) calls this before saving.
package com.example.ectr.plugin.api.port;

import com.example.ectr.plugin.api.model.EngineeringObject;

/**
 * Hook called before an engineering object is saved.
 * <p>
 * How a real ECTR system offers a "before save" hook depends on the ECTR release and the
 * customer's SDK. The adapter must find that hook and call every registered
 * {@code SaveInterceptor} service from it. See docs/05-mandatory-field-validation.md.
 */
public interface SaveInterceptor {

    SaveDecision beforeSave(EngineeringObject object);
}
