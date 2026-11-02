import type { ReactNode } from "react";
import { cx, fmt } from "@/utils/format";

export interface Column<T> {
  key: string;
  header: ReactNode;
  /** Defaults to fmt(row[key]) */
  render?: (row: T) => ReactNode;
  className?: string;
  align?: "left" | "right";
}

interface Props<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
  selectedKey?: string;
  dense?: boolean;
}

export function DataTable<T>({ columns, rows, rowKey, onRowClick, selectedKey }: Props<T>) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full border-separate border-spacing-0">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col" className={cx("th", c.align === "right" && "text-right", c.className)}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const key = rowKey(row);
            return (
              <tr
                key={key}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                onKeyDown={onRowClick ? (e) => (e.key === "Enter" || e.key === " ") && (e.preventDefault(), onRowClick(row)) : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                className={cx(onRowClick && "cursor-pointer hover:bg-slate-50 focus:bg-slate-50 focus:outline-none", selectedKey === key && "bg-blue-50/60")}
              >
                {columns.map((c) => (
                  <td key={c.key} className={cx("td", c.align === "right" && "text-right tabular-nums", c.className)}>
                    {c.render ? c.render(row) : fmt((row as Record<string, unknown>)[c.key])}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
