UPDATE label_sets
SET definition = jsonb_set(
        definition,
        '{labels}',
        COALESCE(
                (
                    SELECT jsonb_agg(labels.item ORDER BY labels.ordinality)
                    FROM jsonb_array_elements(definition -> 'labels') WITH ORDINALITY AS labels(item, ordinality)
                    WHERE labels.item ->> 'scope' IS DISTINCT FROM 'line'
                ),
                '[]'::jsonb
        )
)
WHERE definition ? 'labels'
  AND jsonb_typeof(definition -> 'labels') = 'array'
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements(definition -> 'labels') AS labels(item)
      WHERE labels.item ->> 'scope' = 'line'
  );
