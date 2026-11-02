// Netlify Edge Function: the production counterpart of the `/rapidratings` middleware in
// vite.config.ts. The token handling and path rules live in ../../proxy/rapidRatings.ts.
//
// Netlify environment variables (Site configuration → Environment variables; redeploy after
// changing them):
//   RAPIDRATINGS_CLIENT_ID       OAuth client ID
//   RAPIDRATINGS_CLIENT_SECRET   OAuth client secret
//   RAPIDRATINGS_API_BASE_URL    optional; defaults to https://rridata.sandbox.rrilabs.co/v2.0
//
// The access token is cached per edge isolate and never leaves this function.

import { createRapidRatingsProxy } from '../../proxy/rapidRatings.ts';

declare const Netlify: { env: { get(name: string): string | undefined } };

let handle: ((request: Request) => Promise<Response>) | null = null;

export default (request: Request): Promise<Response> => {
  handle ??= createRapidRatingsProxy({
    baseUrl: Netlify.env.get('RAPIDRATINGS_API_BASE_URL'),
    clientId: Netlify.env.get('RAPIDRATINGS_CLIENT_ID'),
    clientSecret: Netlify.env.get('RAPIDRATINGS_CLIENT_SECRET'),
  });
  return handle(request);
};

export const config = { path: '/rapidratings/*' };
