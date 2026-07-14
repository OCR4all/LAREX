UPDATE label_sets
SET definition = jsonb_set(
    definition #- '{meta,altoEnabled}',
    '{labels}',
    COALESCE(
        (
            SELECT jsonb_agg(item #- '{mapping,altoXml}')
            FROM jsonb_array_elements(definition -> 'labels') AS labels(item)
        ),
        '[]'::jsonb
    )
)
WHERE definition #> '{meta,altoEnabled}' IS NOT NULL
   OR EXISTS (
       SELECT 1
       FROM jsonb_array_elements(definition -> 'labels') AS labels(item)
       WHERE item #> '{mapping,altoXml}' IS NOT NULL
   );
