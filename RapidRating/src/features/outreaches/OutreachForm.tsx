import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { AlertTriangle, Send } from "lucide-react";
import { useCreateOutreach } from "@/hooks/useOutreaches";
import { ErrorState } from "@/components/common/ErrorState";
import { getAuthMode } from "@/services/config";
import type { OutreachRequest, OutreachRequestCreate } from "@/types/outreach";

/** Validation mirrors the provider OpenAPI request schema (CounterpartyInput, ContactDetailsInput, RelationshipManager). */
const str = (label: string) => z.string().trim().min(1, `${label} is required`).max(255, `${label}: max 255 characters`);
const schema = z.object({
  companyName: str("Company name"),
  referenceId: z.string().trim().max(255).optional(),
  firstName: str("First name"),
  lastName: str("Last name"),
  email: str("Email").email("Enter a valid email"),
  country: z.string().trim().regex(/^[A-Z]{3}$/, "Use an ISO-3166 alpha-3 code, e.g. USA"),
  tel: z.string().trim().max(255).optional(),
  rmName: str("Relationship manager name"),
  rmEmail: str("Relationship manager email").email("Enter a valid email"),
  confirm: z.boolean(),
});
type FormValues = z.infer<typeof schema>;

export function OutreachForm({ onCreated, onCancel }: { onCreated: (o: OutreachRequest) => void; onCancel: () => void }) {
  const mock = getAuthMode() === "mock";
  const create = useCreateOutreach();
  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { confirm: false, country: "USA" } });

  const submit = handleSubmit((v) => {
    const body: OutreachRequestCreate = {
      counterparty: {
        company_name: v.companyName,
        ...(v.referenceId ? { reference_id: v.referenceId } : {}),
        contact: { first_name: v.firstName, last_name: v.lastName, email: v.email, country: v.country, ...(v.tel ? { tel: v.tel } : {}) },
      },
      relationship_manager: { name: v.rmName, email: v.rmEmail },
    };
    create.mutate(body, { onSuccess: onCreated });
  });

  const field = (name: keyof FormValues, label: string, opts: { type?: string; placeholder?: string; optional?: boolean } = {}) => (
    <div>
      <label htmlFor={`of-${name}`} className="label">
        {label}
        {!opts.optional && <span className="text-red-600"> *</span>}
      </label>
      <input id={`of-${name}`} type={opts.type ?? "text"} className="input" placeholder={opts.placeholder} aria-invalid={!!errors[name]} {...register(name)} />
      {errors[name] && <p className="field-error">{String(errors[name]?.message)}</p>}
    </div>
  );

  const confirmed = watch("confirm");

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      <div className={`flex gap-2 rounded-md border p-3 text-sm ${mock ? "border-violet-200 bg-violet-50 text-violet-800" : "border-amber-300 bg-amber-50 text-amber-900"}`}>
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        {mock ? (
          <span>Demo mode: the request is created in the in-memory demo store only. Nothing is sent.</span>
        ) : (
          <span>
            <strong>This calls POST /outreach-requests.</strong> RapidRatings will email the counterparty contact to request financial statements. Only submit real, approved requests.
          </span>
        )}
      </div>

      <fieldset className="space-y-3">
        <legend className="mb-1 text-sm font-semibold text-slate-800">Counterparty</legend>
        {field("companyName", "Company name")}
        {field("referenceId", "Your reference ID (e.g. supplier number)", { optional: true })}
        <div className="grid grid-cols-2 gap-3">
          {field("firstName", "Contact first name")}
          {field("lastName", "Contact last name")}
        </div>
        {field("email", "Contact email", { type: "email" })}
        <div className="grid grid-cols-2 gap-3">
          {field("country", "Country (ISO alpha-3)", { placeholder: "USA" })}
          {field("tel", "Telephone", { optional: true })}
        </div>
      </fieldset>

      <fieldset className="space-y-3">
        <legend className="mb-1 text-sm font-semibold text-slate-800">Relationship manager</legend>
        {field("rmName", "Name")}
        {field("rmEmail", "Email", { type: "email" })}
      </fieldset>

      {!mock && (
        <label className="flex items-start gap-2 text-sm text-slate-700">
          <input type="checkbox" className="mt-0.5" {...register("confirm")} />I understand this sends a real outreach request to the counterparty.
        </label>
      )}

      {create.error && <ErrorState error={create.error} compact />}

      <div className="flex gap-2">
        <button type="submit" className={mock ? "btn-primary" : "btn-danger"} disabled={create.isPending || (!mock && !confirmed)}>
          <Send className="h-4 w-4" aria-hidden /> {create.isPending ? "Submitting…" : "Submit outreach request"}
        </button>
        <button type="button" className="btn-secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
