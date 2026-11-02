/**
 * Item details models, built from the Enterprise partial-object response
 * (`services/itemDetails.ts`). Sources use the Part model (see ./part.ts).
 */
export interface OverviewField {
  label: string;
  /** Display-ready value; null when the item has no value. */
  value: string | null;
  /** Why a calculated value is what it is (risk fields). */
  reason: string | null;
}

export interface OverviewSection {
  title: string;
  fields: OverviewField[];
}

export interface ItemOverview {
  id: number;
  itemNumber: string | null;
  description: string | null;
  revision: string | null;
  itemType: string | null;
  itemStatus: string | null;
  sections: OverviewSection[];
}
