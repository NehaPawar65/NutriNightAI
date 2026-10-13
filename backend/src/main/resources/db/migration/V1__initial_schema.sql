-- NutriNight AI initial schema.
-- No personal data is stored: order identifiers are one-way hashed, and only city-level geography is kept.

CREATE TABLE nutrition_sources (
    id              BIGSERIAL PRIMARY KEY,
    source_name     VARCHAR(255)  NOT NULL UNIQUE,
    source_url      VARCHAR(1000),
    reference_date  DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE food_items (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200)  NOT NULL,
    normalized_name     VARCHAR(200)  NOT NULL UNIQUE,
    category            VARCHAR(40)   NOT NULL,
    category_source     VARCHAR(20)   NOT NULL DEFAULT 'PROVIDED',
    -- serving size is in grams, or millilitres when serving_unit = 'ml'
    serving_size        NUMERIC(8, 2),
    serving_unit        VARCHAR(4),
    calories_kcal       NUMERIC(8, 2),
    sugar_g             NUMERIC(8, 2),
    sodium_mg           NUMERIC(8, 2),
    saturated_fat_g     NUMERIC(8, 2),
    protein_g           NUMERIC(8, 2),
    nutrition_source_id BIGINT REFERENCES nutrition_sources (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_food_category CHECK (category IN
        ('FAST_FOOD', 'FRIED_SNACKS', 'SUGARY_BEVERAGES', 'DESSERTS', 'BALANCED_MEALS', 'FRUITS_VEGETABLES', 'OTHER')),
    CONSTRAINT chk_category_source CHECK (category_source IN ('PROVIDED', 'AI_RULE_BASED', 'AI_ML_MODEL', 'DEFAULT')),
    CONSTRAINT chk_serving_unit CHECK (serving_unit IS NULL OR serving_unit IN ('g', 'ml')),
    CONSTRAINT chk_serving_size CHECK (serving_size IS NULL OR serving_size > 0),
    CONSTRAINT chk_nutrients_non_negative CHECK (
        (calories_kcal IS NULL OR calories_kcal >= 0) AND (sugar_g IS NULL OR sugar_g >= 0) AND
        (sodium_mg IS NULL OR sodium_mg >= 0) AND (saturated_fat_g IS NULL OR saturated_fat_g >= 0) AND
        (protein_g IS NULL OR protein_g >= 0))
);
CREATE INDEX idx_food_items_category ON food_items (category);

CREATE TABLE import_batches (
    id                BIGSERIAL PRIMARY KEY,
    file_name         VARCHAR(255) NOT NULL,
    rows_total        INT          NOT NULL,
    rows_imported     INT          NOT NULL,
    rows_invalid      INT          NOT NULL,
    rows_duplicate    INT          NOT NULL,
    food_items_created INT         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE food_orders (
    id                BIGSERIAL PRIMARY KEY,
    -- SHA-256 of data_source + ':' + the source order id; the raw id is never stored
    external_order_id VARCHAR(64)  NOT NULL UNIQUE,
    order_timestamp   TIMESTAMPTZ  NOT NULL,
    -- derived from order_timestamp in the configured analysis time zone at import time
    local_date_time   TIMESTAMP    NOT NULL,
    local_hour        SMALLINT     NOT NULL CHECK (local_hour BETWEEN 0 AND 23),
    -- the "analysis day" runs from the end of the late-night window (04:00) to 04:00 next day,
    -- so 00:00-03:59 orders belong to the previous evening
    analysis_date     DATE         NOT NULL,
    is_late_night     BOOLEAN      NOT NULL,
    food_item_id      BIGINT       NOT NULL REFERENCES food_items (id),
    quantity          INT          NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    city              VARCHAR(100) NOT NULL,
    data_source       VARCHAR(50)  NOT NULL,
    import_batch_id   BIGINT       REFERENCES import_batches (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_food_orders_timestamp ON food_orders (order_timestamp);
CREATE INDEX idx_food_orders_analysis_date ON food_orders (analysis_date);
CREATE INDEX idx_food_orders_late_night_date ON food_orders (is_late_night, analysis_date);
CREATE INDEX idx_food_orders_city ON food_orders (city);
CREATE INDEX idx_food_orders_food_item ON food_orders (food_item_id);

CREATE TABLE analysis_results (
    id                    BIGSERIAL PRIMARY KEY,
    analysis_type         VARCHAR(50) NOT NULL,
    analysis_period_start DATE,
    analysis_period_end   DATE,
    filters_json          JSONB,
    result_json           JSONB       NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_analysis_results_type_created ON analysis_results (analysis_type, created_at DESC);

CREATE TABLE recommendations (
    id                       BIGSERIAL PRIMARY KEY,
    source_food_item_id      BIGINT        NOT NULL REFERENCES food_items (id) ON DELETE CASCADE,
    alternative_food_item_id BIGINT        NOT NULL REFERENCES food_items (id) ON DELETE CASCADE,
    improvement_score        NUMERIC(6, 2) NOT NULL,
    explanation              TEXT          NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_recommendation_pair UNIQUE (source_food_item_id, alternative_food_item_id),
    CONSTRAINT chk_recommendation_distinct CHECK (source_food_item_id <> alternative_food_item_id)
);

CREATE TABLE model_runs (
    id                 BIGSERIAL PRIMARY KEY,
    model_name         VARCHAR(100) NOT NULL,
    model_version      VARCHAR(100) NOT NULL,
    evaluation_metrics JSONB,
    dataset_version    VARCHAR(100),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_model_run UNIQUE (model_name, model_version)
);
