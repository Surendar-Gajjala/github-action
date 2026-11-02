import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { itemsApi } from '../../api/endpoints';
import type { ItemDetail } from '../../api/types';
import { MultiSelect } from '../../components/MultiSelect';
import { Modal } from '../../components/Overlay';
import { Field, InlineError } from '../../components/States';
import { useToast } from '../../components/Toast';
import { usePartOptions } from '../options';

const schema = z.object({
  itemNumber: z
    .string()
    .trim()
    .min(1, 'Item number is required')
    .max(64)
    .regex(/^[A-Za-z0-9][A-Za-z0-9._-]*$/, "Use letters, digits, '.', '_' or '-'"),
  name: z.string().trim().min(1, 'Name is required').max(200),
  description: z.string().max(2000).optional(),
  partIds: z.array(z.number()),
});
type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  item?: ItemDetail;
  onClose: () => void;
  onSaved?: (item: ItemDetail) => void;
}

export function ItemFormDialog({ open, item, onClose, onSaved }: Props) {
  const editing = !!item;
  const qc = useQueryClient();
  const toast = useToast();
  const { options: partOptions, loading } = usePartOptions(open);
  // Until the part list arrives, show the item's current parts so chips have labels immediately.
  const options = partOptions.length
    ? partOptions
    : (item?.parts ?? []).map((p) => ({ value: p.id, label: `${p.partNumber} · ${p.name}`, description: p.partType }));
  const {
    register, handleSubmit, control, reset, setError, formState: { errors, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { itemNumber: '', name: '', description: '', partIds: [] },
  });

  useEffect(() => {
    if (open) {
      reset({
        itemNumber: item?.itemNumber ?? '',
        name: item?.name ?? '',
        description: item?.description ?? '',
        partIds: item?.parts.map((p) => p.id) ?? [],
      });
    }
  }, [open, item, reset]);

  const mutation = useMutation({
    mutationFn: (v: FormValues) =>
      editing
        ? itemsApi.update(item!.id, { name: v.name, description: v.description, partIds: v.partIds, version: item!.version })
        : itemsApi.create({ itemNumber: v.itemNumber, name: v.name, description: v.description, partIds: v.partIds }),
    onSuccess: (saved) => {
      void qc.invalidateQueries({ queryKey: ['items'] });
      void qc.invalidateQueries({ queryKey: ['parts'] });
      qc.setQueryData(['item', saved.id], saved);
      toast.success(
        editing
          ? saved.lifecycleStatus === 'CHANGE_PENDING' && item?.lifecycleStatus === 'APPROVED'
            ? `${saved.itemNumber} changed — now CHANGE PENDING (rev ${saved.revision}); rev ${saved.approvedRevision} stays approved until the change is approved`
            : `${saved.itemNumber} saved`
          : `${saved.itemNumber} created as DRAFT`,
      );
      onSaved?.(saved);
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        Object.entries(e.fieldErrors).forEach(([field, message]) => setError(field as keyof FormValues, { message }));
      }
    },
  });

  const approvedWarning = editing && item?.lifecycleStatus === 'APPROVED';
  const reviewBlock = editing && item?.lifecycleStatus === 'IN_REVIEW';

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Edit ${item!.itemNumber}` : 'Create Item'}
      size="lg"
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancel</button>
          <button
            className="btn-primary"
            disabled={mutation.isPending || reviewBlock || (editing && !isDirty)}
            onClick={handleSubmit((v) => mutation.mutate(v))}
          >
            {mutation.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create item'}
          </button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => mutation.mutate(v))}>
        {approvedWarning && (
          <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-amber-900">
            This item is <b>APPROVED</b> (revision {item!.approvedRevision}). Saving a change opens a change request, moves the
            item to <b>CHANGE PENDING</b> and starts revision {nextRevision(item!.approvedRevision)}. Revision {item!.approvedRevision}{' '}
            remains the released revision until the change is approved.
          </div>
        )}
        {reviewBlock && (
          <div className="rounded-md border border-sky-300 bg-sky-50 p-3 text-sky-900">
            This item is in review and cannot be changed until the approval workflow completes.
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Item number" error={errors.itemNumber?.message} htmlFor="itemNumber">
            <input id="itemNumber" className="input" disabled={editing} placeholder="ITEM-1001" {...register('itemNumber')} />
          </Field>
          <Field label="Name" error={errors.name?.message} htmlFor="name">
            <input id="name" className="input" placeholder="Hydraulic Pump Assembly" disabled={reviewBlock} {...register('name')} />
          </Field>
        </div>
        <Field label="Description" error={errors.description?.message} htmlFor="description">
          <textarea id="description" rows={3} className="input" disabled={reviewBlock} {...register('description')} />
        </Field>
        <Field label="Parts" error={errors.partIds?.message} hint="Search by part number or name; select several. Click × to remove a part.">
          <Controller
            control={control}
            name="partIds"
            render={({ field }) => (
              <MultiSelect
                ariaLabel="Parts"
                options={options}
                loading={loading}
                value={field.value}
                onChange={field.onChange}
                disabled={reviewBlock}
                placeholder="Search parts…"
              />
            )}
          />
        </Field>
        <InlineError error={mutation.error} />
      </form>
    </Modal>
  );
}

function nextRevision(rev?: string) {
  if (!rev) return 'A';
  const chars = rev.split('');
  for (let i = chars.length - 1; i >= 0; i--) {
    if (chars[i] !== 'Z') {
      chars[i] = String.fromCharCode(chars[i].charCodeAt(0) + 1);
      return chars.join('');
    }
    chars[i] = 'A';
  }
  return 'A' + chars.join('');
}
