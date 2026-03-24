-- =============================================================================
-- DATABASE SCHEMA — executed automatically on first PostgreSQL container start.
--
-- This file is mounted into /docker-entrypoint-initdb.d via docker-compose.yml.
-- PostgreSQL runs all .sql files in that directory alphabetically on first boot.
-- It does NOT re-run on subsequent starts (data is persisted in the Docker volume).
--
-- PURPOSE: Create the 'dog' table and seed it with sample adoption data.
-- The dog descriptions serve double duty:
--   1. Displayed to users as information about available dogs
--   2. Embedded as vectors by Cohere (via Spring AI) for semantic search (RAG)
-- =============================================================================

-- SEQUENCE: auto-incrementing ID generator for the dog table.
-- WHY a manual sequence instead of SERIAL or GENERATED ALWAYS AS IDENTITY?
--   — Explicit control over the sequence behavior (start, increment, caching).
--   — SERIAL is syntactic sugar for this same pattern but hides the sequence name.
--   — GENERATED ALWAYS AS IDENTITY (SQL standard) is the modern alternative,
--     but this explicit approach works across all PostgreSQL versions.
CREATE SEQUENCE IF NOT EXISTS dog_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE CACHE 1;

-- DOG TABLE: the core relational table for the adoption agency.
--
-- Column notes:
--   id          — auto-assigned from dog_id_seq; primary key for lookups
--   name        — NOT NULL because every dog needs a name for identification
--   owner       — NULLABLE: NULL means the dog is available for adoption,
--                  a name means the dog is already adopted/owned.
--                  This is a simple way to track adoption status without a
--                  separate boolean column or status enum.
--   description — NOT NULL: the text used for both display AND vector embedding.
--                  In the RAG pipeline, this text is sent to Cohere embed-english-v3
--                  to generate a vector stored in PGVector's vector_store table.
--
-- NOTE: The PGVector vector_store table (for embeddings) is NOT created here —
-- Spring AI's PGVector auto-configuration creates it at application startup with
-- columns: id (UUID), content (text), metadata (JSON), embedding (vector type).
CREATE TABLE IF NOT EXISTS dog
(
    id          integer      DEFAULT nextval('dog_id_seq') PRIMARY KEY,
    name        text NOT NULL,
    owner       text,
    description text NOT NULL
);

-- SEED DATA: 10 sample dogs for testing.
-- Dogs with NULL owner (Charlie, Cooper, Sadie) are "available for adoption."
-- Dogs with an owner name are already adopted and serve as context for the LLM
-- to understand the full roster.
-- The descriptions are crafted to be semantically rich so that vector similarity
-- search returns meaningful results (e.g., "good with children" matches queries
-- about family-friendly dogs even if the exact words differ).
INSERT INTO dog (name, owner, description) VALUES ('Buddy', 'John Smith', 'Friendly golden retriever, 3 years old');
INSERT INTO dog (name, owner, description) VALUES ('Max', 'Sarah Johnson', 'Energetic border collie, loves to play fetch');
INSERT INTO dog (name, owner, description) VALUES ('Bella', 'Michael Brown', 'Calm and gentle labrador, good with children');
INSERT INTO dog (name, owner, description) VALUES ('Luna', 'Emily Davis', 'Playful husky with blue eyes, enjoys long walks');
INSERT INTO dog (name, owner, description) VALUES ('Charlie', NULL, 'Rescue poodle mix, very affectionate');
INSERT INTO dog (name, owner, description) VALUES ('Lucy', 'David Wilson', 'Senior beagle, enjoys sunbathing and short walks');
INSERT INTO dog (name, owner, description) VALUES ('Cooper', NULL, 'German shepherd puppy, smart and trainable');
INSERT INTO dog (name, owner, description) VALUES ('Daisy', 'Jennifer Taylor', 'Friendly bulldog, loves belly rubs');
INSERT INTO dog (name, owner, description) VALUES ('Rocky', 'Robert Martinez', 'Athletic boxer, great running companion');
INSERT INTO dog (name, owner, description) VALUES ('Sadie', NULL, 'Gentle mixed breed, good with other pets');
