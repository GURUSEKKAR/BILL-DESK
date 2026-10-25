-- ============================================================================
-- Remove PDF generation entirely.
--
-- PDF generation was resource-heavy and is no longer used — invoices are
-- delivered to customers by email only. Drop the now-unused column. Any
-- queued 'generate_pdf' jobs are harmless (no handler is registered; the
-- worker marks unknown types failed), but there should be none in practice.
-- ============================================================================

ALTER TABLE invoices DROP COLUMN IF EXISTS pdf_path;
