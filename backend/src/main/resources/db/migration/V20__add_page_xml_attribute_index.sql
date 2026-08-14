CREATE TABLE public.page_xml_attribute_index (
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    element_name character varying(255) NOT NULL,
    attribute_name character varying(255) NOT NULL,
    attribute_value text NOT NULL,
    CONSTRAINT page_xml_attribute_index_pkey PRIMARY KEY (id),
    CONSTRAINT fk_page_xml_attribute_index_page
        FOREIGN KEY (page_id) REFERENCES public.pages(id) ON DELETE CASCADE
);

CREATE INDEX idx_page_xml_attribute_index_page_id
    ON public.page_xml_attribute_index USING btree (page_id);

CREATE INDEX idx_page_xml_attribute_index_name
    ON public.page_xml_attribute_index USING btree (attribute_name);

CREATE INDEX idx_page_xml_attribute_index_element_name
    ON public.page_xml_attribute_index USING btree (element_name, attribute_name);

CREATE INDEX idx_page_xml_attribute_index_value_exact
    ON public.page_xml_attribute_index USING hash (attribute_value);

CREATE INDEX idx_page_xml_attribute_index_value_trgm
    ON public.page_xml_attribute_index USING gin (attribute_value public.gin_trgm_ops);
