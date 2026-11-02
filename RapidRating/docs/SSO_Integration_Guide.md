# Single Sign-On (SSO) Integration Guide: Keycloak or a Third-Party Provider

This guide explains, step by step, how to add single sign-on to a web application with a React frontend and a separate backend API. It uses Keycloak for its examples, and the same steps apply to providers such as Okta, Microsoft Entra ID and Auth0.

## 1. Key terms

| Term | Meaning |
|---|---|
| **SSO** | Users sign in once and can use several applications without signing in again |
| **Identity provider (IdP)** | The service that checks who the user is, such as Keycloak or Okta |
| **OpenID Connect (OIDC)** | The standard login protocol used in this guide |
| **Authorization Code flow with PKCE** | The recommended OIDC login flow for browser applications |
| **SAML** | An older login standard that some enterprise customers still use |
| **Realm** | An isolated Keycloak space with its own users, roles and clients |
| **Client** | An application registered with the provider |
| **Access token** | A short-lived signed token that the frontend sends to the API |
| **JWKS** | The provider's published public keys, used to check token signatures |

SSO is separate from service-to-service credentials. SSO identifies the **user signing in**. A Client ID and Secret used by your backend to call another system, such as RapidRatings, identify **your backend**. Never use one in place of the other.

## 2. How the login works

```text
1. The user opens the application.
2. The frontend redirects the user to the provider's login page.
3. The user signs in at the provider. The application never sees the password.
4. The provider redirects back with a one-time authorization code.
5. The code is exchanged for tokens.
6. The frontend calls the backend with: Authorization: Bearer <access_token>
7. The backend checks the token, then applies roles and tenant rules.
```

## 3. Choose a provider

| Option | Best when | Trade-off |
|---|---|---|
| **Keycloak** | You want it free and under your control | You host, patch and back it up yourself |
| **Okta, Auth0 or Entra ID** | You want a managed service | Subscription cost, depending on the provider and plan |
| **Keycloak in front of customers' providers** | Each customer uses their own company login | More setup, but the application only connects to Keycloak |

For a multi-tenant product where customers want to use their own company login, the third option is usually the best fit.

## 4. Set up the provider

For a local Keycloak test, run:

```bash
docker run -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

1. Open `http://localhost:8080` and sign in to the admin console.
2. Create a realm, for example `z2`.
3. Create a test user, and set a password for that user.

This setup is for local development only. Production settings are in Step 12.

With a third-party provider, create an account or tenant instead. You still need the settings in Steps 5 and 6, although each provider names them differently.

## 5. Register the application as a client

| Setting | Value |
|---|---|
| Client ID | `z2-web` (example) |
| Protocol | OpenID Connect |
| Flow | Standard flow (Authorization Code) |
| Client authentication | Off for a browser-only app, on if the backend handles the login |
| Valid redirect URIs | `http://localhost:5173/*` for local use, then exact production URLs |
| Web origins | `http://localhost:5173` for local use, then the production origin |
| Valid post-logout redirect URIs | Your application URLs |

Check that PKCE is enabled with the `S256` method. Don't enable Implicit flow or Direct Access Grants, because they aren't needed for this login.

## 6. Add roles and tenant information

1. Create roles, for example `integration-admin`, `business-user` and `auditor`.
2. Assign the roles to your test user.
3. Add a user attribute, for example `tenant_id`, and a token mapper that adds it to the access token.
4. Add an audience mapper, so the backend can check that the token was issued for its API.
5. Sign in with the test user and inspect the token claims to confirm that the roles and tenant appear.

Example of the claims the backend should receive:

```json
{
  "iss": "http://localhost:8080/realms/z2",
  "aud": "z2-api",
  "sub": "user-id",
  "exp": 1893456000,
  "email": "user@example.com",
  "tenant_id": "tenant-a",
  "realm_access": { "roles": ["integration-admin"] }
}
```

## 7. Add login to the React frontend

Install the libraries:

```bash
npm install oidc-client-ts react-oidc-context
```

Wrap the application:

```tsx
import { AuthProvider } from "react-oidc-context";

const oidcConfig = {
  authority: "http://localhost:8080/realms/z2",
  client_id: "z2-web",
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: "openid profile email",
};

export function Root() {
  return (
    <AuthProvider {...oidcConfig}>
      <App />
    </AuthProvider>
  );
}
```

Protect the application and add sign-out:

```tsx
import { useAuth } from "react-oidc-context";

function App() {
  const auth = useAuth();

  if (auth.isLoading) return <p>Loading...</p>;
  if (auth.error) return <p>Sign-in failed. Please try again.</p>;
  if (!auth.isAuthenticated) {
    return <button onClick={() => auth.signinRedirect()}>Sign in</button>;
  }

  return <button onClick={() => auth.signoutRedirect()}>Sign out</button>;
}
```

Send the token on API calls:

```ts
axios.get("/api/resource", {
  headers: { Authorization: `Bearer ${auth.user?.access_token}` },
});
```

Keep the `authority` and `client_id` in environment variables. They aren't secrets. A client secret must never be placed in frontend code or in a `VITE_` variable.

## 8. Protect the backend

This is the step that actually secures your data. Hiding buttons in the UI protects nothing.

On every request, the backend must:

1. Read the Bearer token, and return **401** if it's missing.
2. Check the signature using the provider's JWKS keys, available through `/.well-known/openid-configuration`.
3. Check the issuer (`iss`), audience (`aud`) and expiry (`exp`). Return **401** if any check fails.
4. Read the user ID, roles and `tenant_id` from the token.
5. Return **403** if the user doesn't have the required role.
6. Limit every query to the user's tenant. Never take the tenant ID from a value that the browser can change.

Use a maintained JWT or OIDC library for your backend language. Don't write your own token validation.

## 9. Handle the session

| Topic | Requirement |
|---|---|
| Token renewal | Renew tokens silently before they expire. Send the user to login if renewal fails. |
| Token lifetime | Keep access tokens short, for example 5 to 15 minutes |
| Token storage | Avoid `localStorage`. In production, a backend-managed session with an HttpOnly, Secure cookie is safer. |
| Logout | Clear the local session and also call the provider's logout, or the user can sign straight back in |
| Logging | Never log tokens, passwords or client secrets |

## 10. Connect customers' company logins (optional)

Do this when customers want to sign in with their own Entra ID, Okta, Google or SAML provider.

1. In Keycloak, open **Identity providers** and add the customer's provider.
2. Enter the settings from the customer's administrator, and give them your redirect URL.
3. Decide how each user maps to a tenant, usually by email domain.
4. Decide whether new users are created automatically on first login, and which default role they get. Use the lowest-privilege role.
5. Map the customer's groups to your roles where needed.
6. Test with a real user from that customer, and with a user from an unrelated domain.

## 11. Use a third-party provider instead of Keycloak

The frontend and backend code stays the same. What changes is:

| Setting | Where it comes from |
|---|---|
| `authority` | The provider's issuer URL |
| `client_id` | The application you register with the provider |
| Roles and groups | Set up in the provider's admin console. The claim names can differ, so check a real token. |
| Tenant claim | Added through the provider's custom claim feature |
| Audience | Configured according to that provider's rules |

Read roles and the tenant through one small mapping function in the backend, so that changing provider only affects that function.

## 12. Go to production

- [ ] HTTPS everywhere, including the provider, frontend and API
- [ ] Keycloak runs with `start`, not `start-dev`
- [ ] Keycloak has a production database, a fixed hostname and tested backups
- [ ] The default admin account is replaced, and admins use multi-factor authentication (MFA)
- [ ] Only exact redirect URIs and origins are allowed, with no wildcards in production
- [ ] Brute-force protection and sensible token lifetimes are enabled
- [ ] Keycloak is on a supported version, with a plan for applying security updates
- [ ] Login failures and admin actions are monitored
- [ ] A tested emergency admin login exists
- [ ] The rollout has been run in staging first

## 13. Test before release

| Scenario | Expected result |
|---|---|
| Valid user signs in | Application opens, and the API returns data |
| Signed-out user opens a protected page | Redirected to login |
| API call with no token | 401 |
| Expired or tampered token | 401 |
| Token issued for a different application | 401 |
| Valid user without the required role | 403 |
| Tenant A user requests tenant B data | Denied |
| Token expires during use | Renewed silently, or the user is asked to sign in |
| User signs out | Local session and provider session both end |
| Sign in to one application, then open a second | Second application opens without another password |
| User disabled at the provider | Can't sign in, and access ends when the current session expires |
| Redirect to an unregistered URL | Refused by the provider |

## 14. Common problems

| Problem | Likely cause | Fix |
|---|---|---|
| `Invalid redirect_uri` | The URL isn't registered exactly | Correct the client's valid redirect URIs |
| CORS error on the token request | Origin not allowed | Add the application origin to web origins |
| API returns 401 with a valid login | Wrong issuer or audience | Compare the token's `iss` and `aud` with the backend settings |
| Issuer mismatch between environments | Keycloak's public hostname differs | Set a fixed hostname |
| Roles missing from the token | Role not assigned, or the mapper is missing | Check the user's roles and the client's mappers |
| Login loop after redirect | The callback isn't handled, or the URL's `code` isn't processed | Make sure `AuthProvider` wraps the redirect page |
| User signs straight back in after logout | Only the local session was cleared | Use the provider's logout |
