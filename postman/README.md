# Postman - Course Aura Hub Auth API

This folder contains a Postman collection and local environment for testing the authentication module.

## Files

- `CourseAuraHub-Auth.postman_collection.json` - authentication requests, assertions, and token-capture scripts
- `CourseAuraHub-Local.postman_environment.json` - local `baseUrl` environment

## Import

1. Open Postman.
2. Select **Import** and import both JSON files from this folder.
3. Select the **Course Aura Hub - Local** environment.
4. Make sure the backend is running on `http://localhost:8080`.

If the backend runs on a different port, update `baseUrl` in the imported environment.

## Run order

Use **Collection Runner** and run the whole collection. The requests are ordered so that:

1. `Register` creates a unique test account and saves the returned tokens.
2. `Login` signs in with that account.
3. `Get Current User` uses the access token as a Bearer token.
4. `Refresh Token (body)` rotates the tokens.
5. `Refresh Token (cookie only)` verifies the cookie-based refresh flow.
6. `Logout` revokes the refresh token.
7. `Refresh After Logout` expects `401`.
8. The negative and OAuth2 folders verify error and redirect behavior.

The collection generates a new email for every run, so it can be executed repeatedly without deleting data.

## Token handling

The test scripts automatically store the latest tokens in collection variables:

- `accessToken`
- `refreshToken`
- `userId`

Protected requests use `Authorization: Bearer {{accessToken}}`.

## OAuth2 requests

The OAuth2 requests only verify the initial `302` redirect from the API. Completing a real OAuth2 login requires provider credentials, a browser, and the provider callback configuration.

## Newman

Run the collection from the command line:

```bash
newman run postman/CourseAuraHub-Auth.postman_collection.json \
  -e postman/CourseAuraHub-Local.postman_environment.json
```
