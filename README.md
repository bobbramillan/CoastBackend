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

## AWS Account Info

- **AWS Account ID:** 601084006791
- **Region:** us-east-1 (N. Virginia) — all resources live here
- **Console:** https://console.aws.amazon.com — sign in with your AWS root or IAM account

---

## Finding Everything in the AWS Console

Once logged in, use the search bar at the top to find each service.

**Lambda** → search "Lambda"
- Click Functions in the left sidebar
- Filter by "coast" to see all six functions:
    - coast-fetch-user-by-email
    - coast-fetch-existing-user-ids
    - coast-email-exists
    - coast-insert-user
    - coast-update-user
    - coast-delete-user
- Click any function to test it, view logs, or check its configuration

**API Gateway** → search "API Gateway"
- Click on `coast-api`
- Under Resources you'll see all six routes and their methods
- Under Stages → prod you'll find the base URL
- Base URL: https://zw4lbcamf5.execute-api.us-east-1.amazonaws.com/prod

**Secrets Manager** → search "Secrets Manager"
- Click on `coast/supabase`
- Click "Retrieve secret value" to see your SUPABASE_URL and SUPABASE_API_KEY
- This is the only place credentials are stored

**IAM** → search "IAM"
- Users → `coast-admin` — the user your CLI and GitHub Actions use to deploy
- Roles → `coast-lambda-role` — the role your Lambda functions run as
- If you need new access keys: coast-admin → Security credentials → Create access key

**CloudWatch** → search "CloudWatch"
- Log groups → search `/aws/lambda/coast-` to see logs for each function
- Use this to debug Lambda errors

---

## AWS Setup Reference

| Resource | Name / ID |
|---|---|
| API Gateway ID | zw4lbcamf5 |
| API Gateway stage | prod |
| IAM User | coast-admin |
| IAM Role | coast-lambda-role |
| Secret | coast/supabase |
| Region | us-east-1 |

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

To view or update GitHub secrets:
- Go to github.com/bobbramillan/CoastBackend → Settings → Secrets and variables → Actions
- Secrets are write-only — you cannot view them once saved
- If you lose your AWS keys, generate new ones in IAM → coast-admin → Security credentials

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
- The API Gateway base URL alone returns {"message":"Missing Authentication Token"} — this is normal, you must include a valid path like /fetch-existing-user-ids
- The deployed JAR uses com.coast.* package names
