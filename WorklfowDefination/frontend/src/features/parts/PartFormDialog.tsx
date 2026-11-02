import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../api/client';
import { partsApi } from '../../api/endpoints';
import type { PartDetail } from '../../api/types';
import { MultiSelect } from '../../components/MultiSelect';
import { Modal } from '../../components/Overlay';
import { Field, InlineError } from '../../components/States';
import { useToast } from '../../components/Toast';
import { useItemOptions } from '../options';

const PART_TYPES = ['MECHANICAL', 'STRUCTURAL', 'ELECTRICAL', 'CONSUMABLE', 'HYDRAULIC', 'FASTENER'];

const schema = z.object({
  partNumber: z
    .string()
    .trim()
    .min(1, 'Part number is required')
    .max(64)
    .regex(/^[A-Za-z0-9][A-Za-z0-9._-]*$/, "Use letters, digits, '.', '_' or '-'"),
  name: z.string().trim().min(1, 'Name is required').max(200),
  partType: z.string().max(64).optional(),
  description: z.string().max(2000).optional(),
  itemIds: z.array(z.number()),
});
type FormValues = z.infer<typeof schema>;

export function PartFormDialog({ open, part, onClose }: { open: boolean; part?: PartDetail; onClose: () => void }) {
  const editing = !!part;
  const qc = useQueryClient();
  const toast = useToast();
  const { options, loading, items } = useItemOptions(open && !editing);
  const { register, handleSubmit, control, reset, setError, watch, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { partNumber: '', name: '', partType: '', description: '', itemIds: [] },
  });

  useEffect(() => {
    if (open) {
      reset({
        partNumber: part?.partNumber ?? '',
        name: part?.name ?? '',
        partType: part?.partType ?? '',
        description: part?.description ?? '',
        itemIds: [],
      });
    }
  }, [open, part, reset]);

  const mutation = useMutation({
    mutationFn: async (v: FormValues) => {
      if (editing) {
        await partsApi.update(part!.id, { name: v.name, description: v.description, partType: v.partType, version: part!.version });
        return { affected: [] as string[], number: part!.partNumber };
      }
      const r = await partsApi.create({ partNumber: v.partNumber, name: v.name, description: v.description, partType: v.partType, itemIds: v.itemIds });
      return {
        affected: r.affectedItems.filter((i) => i.lifecycleStatus === 'CHANGE_PENDING').map((i) => i.itemNumber),
        number: r.part.partNumber,
      };
    },
    onSuccess: ({ affected, number }) => {
      void qc.invalidateQueries({ queryKey: ['parts'] });
      void qc.invalidateQueries({ queryKey: ['part'] });
      void qc.invalidateQueries({ queryKey: ['items'] });
      toast.success(`${number} ${editing ? 'saved' : 'created'}` +
        (affected.length ? `\nChange requests opened for approved items: ${affected.join(', ')}` : ''));
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        Object.entries(e.fieldErrors).forEach(([f, m]) => setError(f as keyof FormValues, { message: m }));
      }
    },
  });

  const selected = watch('itemIds');
  const approvedSelected = items.filter((i) => selected.includes(i.id) && i.lifecycleStatus === 'APPROVED');

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? `Edit ${part!.partNumber}` : 'Create Part'}
      size="lg"
      footer={
        <>
          <button className="btn-secondary" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={mutation.isPending} onClick={handleSubmit((v) => mutation.mutate(v))}>
            {mutation.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create part'}
          </button>
        </>
      }
    >
      <form className="space-y-4" onSubmit={handleSubmit((v) => mutation.mutate(v))}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Part number" error={errors.partNumber?.message} htmlFor="partNumber">
            <input id="partNumber" className="input" disabled={editing} placeholder="PART-109" {...register('partNumber')} />
          </Field>
          <Field label="Name" error={errors.name?.message} htmlFor="partName">
            <input id="partName" className="input" {...register('name')} />
          </Field>
        </div>
        <Field label="Part type" error={errors.partType?.message} htmlFor="partType">
          <input id="partType" className="input" list="part-types" {...register('partType')} />
          <datalist id="part-types">{PART_TYPES.map((t) => <option key={t} value={t} />)}</datalist>
        </Field>
        <Field label="Description" error={errors.description?.message} htmlFor="partDescription">
          <textarea id="partDescription" rows={3} className="input" {...register('description')} />
        </Field>
        {!editing && (
          <Field label="Use in items" hint="Optional. Search items by number or name.">
            <Controller control={control} name="itemIds" render={({ field }) => (
              <MultiSelect ariaLabel="Items" options={options} loading={loading} value={field.value} onChange={field.onChange} placeholder="Search items…" />
            )} />
          </Field>
        )}
        {approvedSelected.length > 0 && (
          <p className="rounded-md bg-amber-50 px-3 py-2 text-amber-800">
            {approvedSelected.map((i) => i.itemNumber).join(', ')} {approvedSelected.length === 1 ? 'is' : 'are'} approved: adding this part
            opens a lifecycle change request (CHANGE PENDING).
          </p>
        )}
        {editing && <p className="text-xs text-slate-500">Editing part attributes does not change the lifecycle of items that use it.</p>}
        <InlineError error={mutation.error} />
      </form>
    </Modal>
  );
}
