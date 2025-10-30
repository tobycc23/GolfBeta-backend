-- Enables fast fuzzy search on names.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Trigram GIN index for ILIKE / similarity / word_similarity on name.
CREATE INDEX IF NOT EXISTS user_profile_name_trgm_idx
  ON user_profile
  USING gin (name gin_trgm_ops);