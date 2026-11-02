/** Shared cell helpers for entity column definitions. */
import { Link } from 'react-router-dom';

export const dash = <span className="text-gray-400">—</span>;

export const textCell = (value: string | null) => value ?? dash;

/** The identifying number of a row, styled like a link as in the reference design. */
export const keyCell = (value: string | null) => (value ? <span className="text-blue-600">{value}</span> : dash);

/** An item number that opens the item details view; plain key text when the item id is unknown. */
export const itemLink = (itemId: number | null, itemNumber: string | null) =>
  itemId === null || !itemNumber ? (
    keyCell(itemNumber)
  ) : (
    <Link to={`/items/${itemId}`} className="text-blue-600 hover:underline focus-visible:underline focus-visible:outline-none">
      {itemNumber}
    </Link>
  );

/** A company name that opens the company details view; plain key text when the company id is unknown. */
export const companyLink = (companyId: number | null, companyName: string | null) =>
  companyId === null || !companyName ? (
    keyCell(companyName)
  ) : (
    <Link to={`/companies/${companyId}`} className="text-blue-600 hover:underline focus-visible:underline focus-visible:outline-none">
      {companyName}
    </Link>
  );

/** A part number that opens the part details view; plain key text when the part id is unknown. */
export const partLink = (partId: number | null, partNumber: string | null) =>
  partId === null || !partNumber ? (
    keyCell(partNumber)
  ) : (
    <Link to={`/parts/${partId}`} className="text-blue-600 hover:underline focus-visible:underline focus-visible:outline-none">
      {partNumber}
    </Link>
  );
