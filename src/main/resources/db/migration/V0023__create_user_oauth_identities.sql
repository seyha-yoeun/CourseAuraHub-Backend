-- V0023: OAuth2 identities linked to users (one account can link several providers)
CREATE TABLE IF NOT EXISTS user_oauth_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_oauth_identities_provider_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_user_oauth_identities_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_user_oauth_identities_user ON user_oauth_identities(user_id);

-- Carry over identities that were stored directly on users
INSERT INTO user_oauth_identities (id, user_id, provider, provider_user_id, email, email_verified, created_at)
SELECT gen_random_uuid(), u.id, u.provider, u.provider_id, u.email, TRUE, COALESCE(u.created_at, CURRENT_TIMESTAMP)
FROM users u
WHERE u.provider <> 'LOCAL'
  AND u.provider_id IS NOT NULL
ON CONFLICT DO NOTHING;
