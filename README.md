# CoastBackend

Private AWS Lambda backend for Coast. Handles all database operations — no credentials are ever exposed to the client.

---

## What This Is

CoastBackend is a serverless backend built with AWS Lambda and API Gateway. It sits between the Coast terminal app (and web app) and Supabase, so that no Supabase credentials ever touch a user's machine or a public repository.

Every time a Coast client needs to read or write data, it calls an HTTPS endpoint on API Gateway, which triggers a Lambda function, which fetches Supabase credentials from AWS Secrets Manager and executes the operation.

---

## Architecture

    Coast Client → API Gateway → Lambda → Secrets Manager → Supabase

---

## AWS Setup

- **AWS Account ID:** 601084006791
- **Region:** us-east-1
- **API Gateway ID:** zw4lbcamf5
- **Base URL:** https://zw4lbcamf5.execute-api.us-east-1.amazonaws.com/prod
- **IAM Role:** coast-lambda-role
- **Secret Name:** coast/supabase (stores SUPABASE_URL and SUPABASE_API_KEY)

---

## Endpoints

| Method | Path | Lambda Function | Description |
|---|---|---|---|
| GET | /fetch-user-by-email | coast-fetch-user-by-email | Fetch user by email |
| GET | /fetch-existing-user-ids | coast-fetch-existing-user-ids | Fetch all user IDs |
| GET | /email-exists | coast-email-exists | Check if email is taken |
| POST | /insert-user | coast-insert-user | Create a new user |
| PATCH | /update-user | coast-update-user | Update any user field |
| DELETE | /delete-user | coast-delete-user | Delete a user |

---

## Project Structure

    src/main/java/com/coast/
    ├── supabase/
    │   └── SupabaseClient.java       — fetches credentials from Secrets Manager, builds HTTP requests
    └── handlers/
        ├── fetch/
        │   ├── FetchUserByEmailHandler.java
        │   ├── FetchExistingUserIdsHandler.java
        │   └── EmailExistsHandler.java
        ├── insert/
        │   └── InsertUserHandler.java
        ├── update/
        │   └── UpdateUserHandler.java
        └── delete/
            └── DeleteUserHandler.java

---

## CI/CD

Every push to `main` triggers a GitHub Actions workflow that:
1. Sets up Java 17
2. Builds the fat JAR with `mvn package`
3. Authenticates with AWS using `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` (stored as GitHub secrets)
4. Deploys all six Lambda functions with `aws lambda update-function-code`

---

## CORS

CORS is handled at two levels:
- **API Gateway** — OPTIONS preflight routes configured for GET, POST, PATCH, DELETE endpoints
- **Lambda responses** — every handler returns `Access-Control-Allow-Origin: *` headers

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 | Lambda runtime |
| Maven | Build tool |
| AWS Lambda | Serverless function execution |
| AWS API Gateway | Public HTTPS endpoints |
| AWS Secrets Manager | Credential storage |
| OkHttp | HTTP client for Supabase calls |
| Gson | JSON parsing |
| GitHub Actions | CI/CD — auto deploy on push |

---

## To Redeploy Manually

```bash
mvn package
aws lambda update-function-code --function-name coast-fetch-user-by-email --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar
aws lambda update-function-code --function-name coast-fetch-existing-user-ids --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar
aws lambda update-function-code --function-name coast-email-exists --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar
aws lambda update-function-code --function-name coast-insert-user --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar
aws lambda update-function-code --function-name coast-update-user --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar
aws lambda update-function-code --function-name coast-delete-user --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar
```

Or just push to main and CI/CD handles it automatically.

---

## Notes

- This repo is private — the Lambda handler structure and API Gateway URL are sensitive
- Supabase credentials are never stored in code — only in AWS Secrets Manager
- The deployed JAR uses `com.coast.*` package names
