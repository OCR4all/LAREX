-- Dataset items follow the tags of their source page; items whose page was deleted keep their last tags.
DELETE FROM dataset_item_tags t
USING dataset_items i
WHERE t.dataset_item_id = i.id
  AND EXISTS (SELECT 1 FROM pages p WHERE p.id = i.source_page_id);

INSERT INTO dataset_item_tags (dataset_item_id, tag)
SELECT i.id, pt.tag
FROM dataset_items i
JOIN page_tags pt ON pt.page_id = i.source_page_id;
