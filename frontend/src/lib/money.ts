/**
 * All money is stored as paise (integers) in the backend.
 * These helpers are the ONLY place we do numeric conversion in the UI.
 * Never do float math directly in a component.
 */

/** 5000 paise → "₹50.00" */
export function formatINR(paise: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
  }).format(paise / 100)
}

/** "50" (rupees) → 5000 (paise). Returns NaN if input is invalid. */
export function parseToPaise(rupees: string): number {
  const n = parseFloat(rupees)
  if (isNaN(n)) return NaN
  return Math.round(n * 100)
}

/** 5000 → "50.00" (for input fields that show rupees) */
export function paiseToRupees(paise: number): string {
  return (paise / 100).toFixed(2)
}
