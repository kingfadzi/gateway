-- Migration: Add source_date column to document_version table
-- Date: 2025-08-27
-- Description: Add source_date column to store the date from the source system (commit date, page modified date, etc.)

-- Add the new column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'document_version' AND column_name = 'source_date'
    ) THEN
        ALTER TABLE document_version ADD COLUMN source_date timestamptz;
        
        -- Add comment for documentation
        COMMENT ON COLUMN document_version.source_date IS 'Date from source system (commit date, page last modified, etc.)';
        
        RAISE NOTICE 'Added source_date column to document_version table';
    ELSE
        RAISE NOTICE 'source_date column already exists in document_version table';
    END IF;
END $$;