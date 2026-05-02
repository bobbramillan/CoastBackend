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

## Setting Up From Scratch

If you are reading this with no memory of what was done, follow every step in order.

### Step 1 — Create an AWS Account
Go to https://aws.amazon.com and sign up. You will need a credit card. The services used here are free tier eligible at low traffic.

### Step 2 — Install the AWS CLI
On Mac (Apple Silicon):
```bash
curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
sudo installer -pkg AWSCLIV2.pkg -target /
aws --version
```

### Step 3 — Create an IAM User
IAM is how AWS manages permissions. You need a user that your terminal and GitHub Actions can use to deploy.

1. Go to https://console.aws.amazon.com
2. Search "IAM" in the top bar
3. Click Users → Create user
4. Name it `coast-admin`
5. Click Next → Attach policies directly
6. Search and attach ALL of these policies:
  - `SecretsManagerReadWrite`
  - `IAMFullAccess`
  - `AWSLambda_FullAccess`
  - `AmazonAPIGatewayAdministrator`
7. Click Create user
8. Click on `coast-admin` → Security credentials → Create access key
9. Select "Command Line Interface (CLI)" → Create
10. Copy both the Access Key ID and Secret Access Key — you won't see the secret again

### Step 4 — Configure the AWS CLI
```bash
aws configure
```
Enter:
- AWS Access Key ID: your access key
- AWS Secret Access Key: your secret key
- Default region: us-east-1
- Default output format: json

Verify it worked:
```bash
aws sts get-caller-identity
```

### Step 5 — Store Supabase Credentials in Secrets Manager
Get your Supabase URL and API key from:
https://supabase.com/dashboard/project/ivnzgcdzytwcaftktaxc → Project Settings → API

Then run:
```bash
aws secretsmanager create-secret \
    --name "coast/supabase" \
    --description "Supabase credentials for Coast app" \
    --secret-string '{"SUPABASE_URL":"https://ivnzgcdzytwcaftktaxc.supabase.co","SUPABASE_API_KEY":"your-anon-key"}'
```

### Step 6 — Create the Lambda IAM Role
Lambda functions need a role that gives them permission to run and access Secrets Manager.

```bash
aws iam create-role \
    --role-name coast-lambda-role \
    --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}'

aws iam attach-role-policy \
    --role-name coast-lambda-role \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

aws iam attach-role-policy \
    --role-name coast-lambda-role \
    --policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite
```

### Step 7 — Build the JAR
In IntelliJ open CoastBackend and run:
```bash
mvn package
```
This creates `target/CoastBackend-1.0-SNAPSHOT.jar` — the fat JAR containing all dependencies.

### Step 8 — Deploy the Lambda Functions
Replace `YOUR_ACCOUNT_ID` with your AWS account ID (find it by running `aws sts get-caller-identity`):

```bash
aws lambda create-function \
    --function-name coast-fetch-user-by-email \
    --runtime java17 \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/coast-lambda-role \
    --handler com.coast.handlers.fetch.FetchUserByEmailHandler::handleRequest \
    --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512

aws lambda create-function \
    --function-name coast-fetch-existing-user-ids \
    --runtime java17 \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/coast-lambda-role \
    --handler com.coast.handlers.fetch.FetchExistingUserIdsHandler::handleRequest \
    --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512

aws lambda create-function \
    --function-name coast-email-exists \
    --runtime java17 \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/coast-lambda-role \
    --handler com.coast.handlers.fetch.EmailExistsHandler::handleRequest \
    --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512

aws lambda create-function \
    --function-name coast-insert-user \
    --runtime java17 \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/coast-lambda-role \
    --handler com.coast.handlers.insert.InsertUserHandler::handleRequest \
    --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512

aws lambda create-function \
    --function-name coast-update-user \
    --runtime java17 \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/coast-lambda-role \
    --handler com.coast.handlers.update.UpdateUserHandler::handleRequest \
    --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512

aws lambda create-function \
    --function-name coast-delete-user \
    --runtime java17 \
    --role arn:aws:iam::YOUR_ACCOUNT_ID:role/coast-lambda-role \
    --handler com.coast.handlers.delete.DeleteUserHandler::handleRequest \
    --zip-file fileb://target/CoastBackend-1.0-SNAPSHOT.jar \
    --timeout 30 \
    --memory-size 512
```

### Step 9 — Create API Gateway
```bash
aws apigateway create-rest-api \
    --name coast-api \
    --region us-east-1
```
Copy the `id` from the response — this is your API Gateway ID.
Copy the `rootResourceId` — this is your root resource ID.

### Step 10 — Create API Gateway Resources
Replace `API_ID` with your API Gateway ID and `ROOT_ID` with your root resource ID:

```bash
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part fetch-user-by-email
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part fetch-existing-user-ids
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part email-exists
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part insert-user
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part update-user
aws apigateway create-resource --rest-api-id API_ID --parent-id ROOT_ID --path-part delete-user
```
Each response has an `id` — save all six resource IDs.

### Step 11 — Create Methods
Replace each RESOURCE_ID with the corresponding ID from Step 10:

```bash
aws apigateway put-method --rest-api-id API_ID --resource-id FETCH_USER_ID --http-method GET --authorization-type NONE
aws apigateway put-method --rest-api-id API_ID --resource-id FETCH_IDS_ID --http-method GET --authorization-type NONE
aws apigateway put-method --rest-api-id API_ID --resource-id EMAIL_EXISTS_ID --http-method GET --authorization-type NONE
aws apigateway put-method --rest-api-id API_ID --resource-id INSERT_ID --http-method POST --authorization-type NONE
aws apigateway put-method --rest-api-id API_ID --resource-id UPDATE_ID --http-method PATCH --authorization-type NONE
aws apigateway put-method --rest-api-id API_ID --resource-id DELETE_ID --http-method DELETE --authorization-type NONE
```

### Step 12 — Link Methods to Lambda Functions
Replace API_ID, RESOURCE_IDs, and YOUR_ACCOUNT_ID:

```bash
aws apigateway put-integration --rest-api-id API_ID --resource-id FETCH_USER_ID --http-method GET --type AWS_PROXY --integration-http-method POST --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:coast-fetch-user-by-email/invocations

aws apigateway put-integration --rest-api-id API_ID --resource-id FETCH_IDS_ID --http-method GET --type AWS_PROXY --integration-http-method POST --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:coast-fetch-existing-user-ids/invocations

aws apigateway put-integration --rest-api-id API_ID --resource-id EMAIL_EXISTS_ID --http-method GET --type AWS_PROXY --integration-http-method POST --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:coast-email-exists/invocations

aws apigateway put-integration --rest-api-id API_ID --resource-id INSERT_ID --http-method POST --type AWS_PROXY --integration-http-method POST --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:coast-insert-user/invocations

aws apigateway put-integration --rest-api-id API_ID --resource-id UPDATE_ID --http-method PATCH --type AWS_PROXY --integration-http-method POST --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:coast-update-user/invocations

aws apigateway put-integration --rest-api-id API_ID --resource-id DELETE_ID --http-method DELETE --type AWS_PROXY --integration-http-method POST --uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:YOUR_ACCOUNT_ID:function:coast-delete-user/invocations
```

### Step 13 — Give API Gateway Permission to Invoke Lambda
Note: wrap ARNs in quotes to prevent zsh from interpreting the * as a wildcard:

```bash
aws lambda add-permission --function-name coast-fetch-user-by-email --statement-id apigateway-invoke --action lambda:InvokeFunction --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:YOUR_ACCOUNT_ID:API_ID/*/GET/fetch-user-by-email"

aws lambda add-permission --function-name coast-fetch-existing-user-ids --statement-id apigateway-invoke --action lambda:InvokeFunction --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:YOUR_ACCOUNT_ID:API_ID/*/GET/fetch-existing-user-ids"

aws lambda add-permission --function-name coast-email-exists --statement-id apigateway-invoke --action lambda:InvokeFunction --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:YOUR_ACCOUNT_ID:API_ID/*/GET/email-exists"

aws lambda add-permission --function-name coast-insert-user --statement-id apigateway-invoke --action lambda:InvokeFunction --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:YOUR_ACCOUNT_ID:API_ID/*/POST/insert-user"

aws lambda add-permission --function-name coast-update-user --statement-id apigateway-invoke --action lambda:InvokeFunction --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:YOUR_ACCOUNT_ID:API_ID/*/PATCH/update-user"

aws lambda add-permission --function-name coast-delete-user --statement-id apigateway-invoke --action lambda:InvokeFunction --principal apigateway.amazonaws.com --source-arn "arn:aws:execute-api:us-east-1:YOUR_ACCOUNT_ID:API_ID/*/DELETE/delete-user"
```

### Step 14 — Enable CORS
Run for each resource ID (insert, update, delete endpoints need OPTIONS preflight):

```bash
for RESOURCE_ID in INSERT_ID UPDATE_ID DELETE_ID; do
  aws apigateway put-method --rest-api-id API_ID --resource-id $RESOURCE_ID --http-method OPTIONS --authorization-type NONE 2>/dev/null || true
  aws apigateway put-integration --rest-api-id API_ID --resource-id $RESOURCE_ID --http-method OPTIONS --type MOCK --request-templates '{"application/json":"{\"statusCode\": 200}"}' 2>/dev/null || true
  aws apigateway put-method-response --rest-api-id API_ID --resource-id $RESOURCE_ID --http-method OPTIONS --status-code 200 --response-parameters '{"method.response.header.Access-Control-Allow-Headers":false,"method.response.header.Access-Control-Allow-Methods":false,"method.response.header.Access-Control-Allow-Origin":false}' 2>/dev/null || true
  aws apigateway put-integration-response --rest-api-id API_ID --resource-id $RESOURCE_ID --http-method OPTIONS --status-code 200 --response-parameters '{"method.response.header.Access-Control-Allow-Headers":"'"'"'Content-Type'"'"'","method.response.header.Access-Control-Allow-Methods":"'"'"'GET,POST,PATCH,DELETE,OPTIONS'"'"'","method.response.header.Access-Control-Allow-Origin":"'"'"'*'"'"'"}' 2>/dev/null || true
done
```

### Step 15 — Deploy API Gateway
```bash
aws apigateway create-deployment --rest-api-id API_ID --stage-name prod
```

Your base URL will be:

    https://API_ID.execute-api.us-east-1.amazonaws.com/prod

### Step 16 — Set Up CI/CD with GitHub Actions
1. Go to your CoastBackend GitHub repo → Settings → Secrets and variables → Actions
2. Add two repository secrets:
  - `AWS_ACCESS_KEY_ID` — your coast-admin access key
  - `AWS_SECRET_ACCESS_KEY` — your coast-admin secret key
3. The `.github/workflows/deploy.yml` file in this repo handles the rest automatically

### Step 17 — Update the BASE_URL in Coast
In the Coast terminal app, open `SupabaseClient.java` and update:
```java
private static final String BASE_URL = "https://YOUR_NEW_API_ID.execute-api.us-east-1.amazonaws.com/prod";
```

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
- Note: hitting the base URL alone returns {"message":"Missing Authentication Token"} — this is normal, always include a path like /fetch-existing-user-ids

**Secrets Manager** → search "Secrets Manager"
- Click on `coast/supabase`
- Click "Retrieve secret value" to see your SUPABASE_URL and SUPABASE_API_KEY
- This is the only place credentials are stored — never in code

**IAM** → search "IAM"
- Users → `coast-admin` — the user your CLI and GitHub Actions use to deploy
- Roles → `coast-lambda-role` — the role your Lambda functions run as
- If you need new access keys: coast-admin → Security credentials → Create access key
- Then update the GitHub secrets at: github.com/bobbramillan/CoastBackend → Settings → Secrets and variables → Actions

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

## Supabase

- **Dashboard:** https://supabase.com/dashboard/project/ivnzgcdzytwcaftktaxc
- **Project ID:** ivnzgcdzytwcaftktaxc
- **Project URL:** https://ivnzgcdzytwcaftktaxc.supabase.co
- **API keys:** Dashboard → Project Settings → API
- **SQL Editor:** Dashboard → SQL Editor — use this to run raw SQL against the database
- **Table Editor:** Dashboard → Table Editor — view and edit the users table directly
- **RLS:** Currently disabled — Lambda acts as the security layer instead

### Database Schema

    users
    ├── user_id       varchar  (primary key, 12-char alphanumeric, generated on account creation)
    ├── name          varchar
    ├── email         varchar  (unique)
    ├── password      varchar  (SHA-256 hash of password + user_id salt — never plain text)
    ├── birth_date    date
    ├── about         text     (nullable)
    ├── created_at    timestamp (default: now())
    ├── last_sign_in  timestamp (nullable)
    └── last_sign_out timestamp (nullable)

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
| Supabase (PostgreSQL) | Database |
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
- The API Gateway base URL alone returns {"message":"Missing Authentication Token"} — this is normal, always include a valid path
- The deployed JAR uses com.coast.* package names