import { ChevronDown, ChevronRight } from 'lucide-react';

import type { HierarchyItemNode, HierarchyNode, HierarchyPartNode } from '../../types/hierarchy';
import { itemLink, partLink } from '../table/cells';
import { HIERARCHY_COLUMNS } from './hierarchyColumns';
import { itemChildren, partChildren, visibleRows } from './treeRows';

interface Props {
  roots: HierarchyNode[];
  expanded: ReadonlySet<string>;
  onToggle: (key: string) => void;
  isLoading: boolean;
  emptyMessage: string;
}

const SKELETON_ROWS = 8;
const INDENT_PX = 24;
const COLUMN_COUNT = HIERARCHY_COLUMNS.length + 3; // tree column + grid columns + 2 part columns

// Borders sit on cells (not rows) so cells spanning several part lines line up.
const cellBase = 'border-b border-gray-200 px-5 py-[18px] align-middle';
// The tree column stays visible while the wide grid scrolls sideways.
const stickyCell = 'sticky left-0 z-[1] border-r border-gray-200';
const partCell = `${cellBase} whitespace-nowrap border-l text-blue-600`;

/**
 * Expandable tree grid: products → BOM items (item_bom, expandable rows). Each item's
 * sourced parts (item_sources, the table config's "subrow:0" section) are listed inside
 * the item's row in the Part Number / Manufacturer columns, one line per part.
 */
export function TreeTable({ roots, expanded, onToggle, isLoading, emptyMessage }: Props) {
  const rows = isLoading ? [] : visibleRows(roots, expanded);

  return (
    <div className="min-h-0 flex-1 overflow-auto border-t border-gray-200 bg-white">
      <table className="w-full min-w-max border-separate border-spacing-0 text-left text-[17px] text-gray-800" aria-busy={isLoading}>
        <caption className="sr-only">Item hierarchy</caption>
        <thead className="sticky top-0 z-10 bg-gray-50">
          <tr>
            <th scope="col" className={`${stickyCell} z-[2] border-b bg-gray-50 py-4 pr-5 pl-10 font-semibold whitespace-nowrap text-gray-900`}>
              Item Number
            </th>
            {[...HIERARCHY_COLUMNS, { id: 'partNumber', header: 'Part Number' }, { id: 'manufacturer', header: 'Manufacturer' }].map(
              (column) => (
                <th
                  key={column.id}
                  scope="col"
                  className={`border-r border-b border-gray-200 px-5 py-4 font-semibold whitespace-nowrap text-gray-900 last:border-r-0 ${
                    'align' in column && column.align === 'right' ? 'text-right' : ''
                  }`}
                >
                  {column.header}
                </th>
              ),
            )}
          </tr>
        </thead>
        <tbody>
          {isLoading &&
            Array.from({ length: SKELETON_ROWS }, (_, row) => (
              <tr key={row}>
                {Array.from({ length: COLUMN_COUNT }, (_, cell) => (
                  <td key={cell} className={`${cellBase} first:pl-10`}>
                    <span className="block h-4 w-24 animate-pulse rounded bg-gray-200" />
                  </td>
                ))}
              </tr>
            ))}

          {rows.map(({ node, depth }) => (
            <ItemRows
              key={node.key}
              node={node}
              depth={depth}
              isOpen={expanded.has(node.key)}
              onToggle={onToggle}
            />
          ))}

          {!isLoading && rows.length === 0 && (
            <tr>
              <td colSpan={COLUMN_COUNT} className="px-10 py-16 text-center text-gray-500">
                {emptyMessage}
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

interface ItemRowsProps {
  node: HierarchyItemNode;
  depth: number;
  isOpen: boolean;
  onToggle: (key: string) => void;
}

/** One item: its cells span one line per sourced part (at least one line). */
function ItemRows({ node, depth, isOpen, onToggle }: ItemRowsProps) {
  const parts = partChildren(node);
  const hasChildren = itemChildren(node).length > 0;
  const span = Math.max(1, parts.length);

  const partCells = (part: HierarchyPartNode | undefined) => (
    <>
      <td className={partCell}>{part ? partLink(part.id, part.part.partNumber) : ''}</td>
      <td className={partCell}>{part?.part.manufacturer ?? ''}</td>
    </>
  );

  return (
    <>
      <tr>
        <td rowSpan={span} className={`${stickyCell} ${cellBase} bg-white pr-5 whitespace-nowrap`}>
          <div className="flex items-center gap-1.5" style={{ paddingLeft: 24 + depth * INDENT_PX }}>
            {hasChildren ? (
              <button
                type="button"
                onClick={() => onToggle(node.key)}
                aria-expanded={isOpen}
                aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${node.item.itemNumber}`}
                className="-ml-1 rounded p-0.5 text-gray-600 hover:bg-gray-200"
              >
                {isOpen ? <ChevronDown className="size-4" aria-hidden /> : <ChevronRight className="size-4" aria-hidden />}
              </button>
            ) : (
              <span className="inline-block w-5" aria-hidden />
            )}
            {itemLink(node.id, node.item.itemNumber)}
          </div>
        </td>
        {HIERARCHY_COLUMNS.map((column) => (
          <td
            key={column.id}
            rowSpan={span}
            className={`${cellBase} ${column.wide ? 'max-w-sm truncate' : 'whitespace-nowrap'} ${
              column.align === 'right' ? 'text-right tabular-nums' : ''
            }`}
            title={column.wide ? (column.text?.(node) ?? undefined) : undefined}
          >
            {column.render(node)}
          </td>
        ))}
        {partCells(parts[0])}
      </tr>
      {parts.slice(1).map((part) => (
        <tr key={part.key}>{partCells(part)}</tr>
      ))}
    </>
  );
}
