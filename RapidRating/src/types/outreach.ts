/** Outreach types from the provider OpenAPI spec (https://api.sandbox.rrilabs.co/openapi.yaml). */
export interface OutreachContact {
  first_name: string;
  last_name: string;
  email: string;
  /** ISO-3166 alpha-3 */
  country: string;
  tel?: string | null;
}

export interface OutreachAdditionalContact {
  contact_ref?: string | null;
  email: string;
  first_name?: string;
  last_name?: string;
  tel?: string | null;
}

export interface OutreachCounterparty {
  company_name: string;
  contact: OutreachContact;
  reference_id?: string | null;
  additional_contacts?: OutreachAdditionalContact[];
}

export interface RelationshipManager {
  name: string;
  email: string;
}

export interface OutreachStatusHistory {
  status?: string;
  status_detail?: string | null;
  status_comment?: string | null;
  status_reasons?: string[] | null;
  status_updated_at?: string;
}

export interface OutreachRequest {
  outreach_request_id?: string;
  counterparty: OutreachCounterparty;
  relationship_manager: RelationshipManager;
  created_at?: string;
  modified_at?: string;
  is_rated?: boolean;
  rrid?: string | null;
  status?: string | null;
  status_history?: OutreachStatusHistory[];
  financial_submissions?: { created_at?: string }[];
  _embedded?: Record<string, unknown>;
}

/** Body of POST /outreach-requests (documented operation). */
export interface OutreachRequestCreate {
  counterparty: OutreachCounterparty;
  relationship_manager: RelationshipManager;
}

export interface OutreachCommunications {
  communications: { introductions: { channel: "email"; sent_at: string }[] };
}

/** Documented sort keys; prefix "-" for descending. */
export const OUTREACH_SORT_FIELDS = ["id", "counterparty_company_name", "reference_id", "created_date"] as const;

export interface OutreachQuery {
  limit?: number;
  cursor?: string;
  sort?: string;
  /** Documented q filters, AND-ed: counterparty_company_name, reference_id, is_rated */
  companyName?: string;
  referenceId?: string;
  isRated?: "true" | "false";
}
