-- Older serializers retained output mode keys even after removal from YAML,
-- including null values. Remove only these obsolete keys before definitions load.
UPDATE action_processor_definitions
SET parsed_json = (
    parsed_json::jsonb
    #- '{outputs,xml,mode}'
    #- '{outputs,images,mode}'
)::text
WHERE (parsed_json::jsonb #> '{outputs,xml}') ? 'mode'
   OR (parsed_json::jsonb #> '{outputs,images}') ? 'mode';
