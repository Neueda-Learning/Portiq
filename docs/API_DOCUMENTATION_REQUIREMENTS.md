PORTIQ - API REQUIREMENTS DOCUMENT (PRE-DEVELOPMENT STYLE)
Version: 1.0
Base URL: http://localhost:4001
Content Type: application/json (except file upload/download endpoints)
Authentication: Bearer JWT required for all /api/** endpoints except /api/auth/**

================================================================
COMMON RESPONSE FORMATS
================================================================

Validation Error (400 Bad Request)
{
  "timestamp": "2026-08-04T10:20:30",
  "status": 400,
  "errors": {
    "fieldName": "Validation message"
  }
}

Generic Error
{
  "timestamp": "2026-08-04T10:20:30",
  "status": 500,
  "message": "An unexpected error occurred"
}

Unauthorized (login/auth failures)
{
  "message": "Invalid username or password"
}

================================================================
1. DASHBOARD / STATS (FLAT HOLDINGS SUMMARY)
================================================================

Endpoint: /api/holdings
- Method: GET
- Description: Retrieves statistical data for dashboard totals and holding-level performance.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "portfolioId": null,
      "portfolioName": "All Holdings",
      "totalCostBasis": 150000.00,
      "totalCurrentValue": 165750.50,
      "totalGainLoss": 15750.50,
      "gainLossPercent": 10.50,
      "holdings": [
        {
          "id": 10,
          "ticker": "AAPL",
          "name": "Apple Inc.",
          "type": "STOCK",
          "quantity": 12,
          "purchasePrice": 150.00,
          "currentPrice": 184.23,
          "costBasis": 1800.00,
          "currentValue": 2210.76,
          "gainLoss": 410.76,
          "gainLossPercent": 22.82,
          "purchaseDate": "2024-01-15"
        }
      ]
    }

================================================================
2. HOLDINGS - ADD / UPDATE / DELETE (FLAT)
================================================================

Endpoint: /api/holdings
- Method: POST
- Description: Adds a new holding in default portfolio, or merges into existing ticker.
- Request Body:
  {
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "type": "STOCK",
    "quantity": 10,
    "purchasePrice": 150.00,
    "purchaseDate": "2024-01-15"
  }
- Response:
  - Status Code: 201 Created
  - Body:
    {
      "id": 11,
      "ticker": "AAPL",
      "name": "Apple Inc.",
      "type": "STOCK",
      "quantity": 10,
      "purchasePrice": 150.00,
      "purchaseDate": "2024-01-15"
    }

Endpoint: /api/holdings/{id}
- Method: PUT
- Description: Updates an existing holding by ID.
- Request Body:
  {
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "type": "STOCK",
    "quantity": 20,
    "purchasePrice": 152.00,
    "purchaseDate": "2024-01-15"
  }
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "id": 11,
      "ticker": "AAPL",
      "name": "Apple Inc.",
      "type": "STOCK",
      "quantity": 20,
      "purchasePrice": 152.00,
      "purchaseDate": "2024-01-15"
    }

Endpoint: /api/holdings/{id}
- Method: DELETE
- Description: Deletes an existing holding by ID.
- Response:
  - Status Code: 204 No Content
  - Body: (empty)

================================================================
3. HOLDINGS IMPORT APIS
================================================================

Endpoint: /api/holdings/import/csv
- Method: POST
- Description: Imports holdings from a CSV file and merges duplicate tickers.
- Request: multipart/form-data with field name 'file'
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "imported": 7,
      "errors": [
        "Row 5: Quantity must be greater than zero"
      ]
    }

Endpoint: /api/holdings/import/image
- Method: POST
- Description: Extracts holdings from statement image and imports them.
- Request: multipart/form-data with field name 'file'
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "imported": 3,
      "errors": []
    }
- Alternative Responses:
  - Status Code: 503 Service Unavailable
  - Body:
    {
      "message": "Image import is not configured on this server"
    }
  - Status Code: 400 Bad Request
  - Body:
    {
      "message": "Could not read a holdings list from the image"
    }

Endpoint: /api/holdings/import/csv/sample
- Method: GET
- Description: Downloads sample CSV template.
- Response:
  - Status Code: 200 OK
  - Content-Type: text/csv
  - Body: file download (sample-holdings.csv)

================================================================
4. HOLDINGS EXPORT APIS
================================================================

Endpoint: /api/holdings/export/csv
- Method: GET
- Description: Exports holdings report as CSV.
- Response:
  - Status Code: 200 OK
  - Content-Type: text/csv
  - Body: file download (portiq-holdings.csv)

Endpoint: /api/holdings/export/pdf
- Method: GET
- Description: Exports holdings report as PDF.
- Response:
  - Status Code: 200 OK
  - Content-Type: application/pdf
  - Body: file download (portiq-holdings.pdf)

================================================================
5. PORTFOLIO VALUE HISTORY API
================================================================

Endpoint: /api/holdings/history?range=1d|1w|1m|all
- Method: GET
- Description: Returns historical points for portfolio trend chart.
- Response:
  - Status Code: 200 OK
  - Body:
    [
      {
        "timestamp": 1722412800,
        "value": 154320.45
      },
      {
        "timestamp": 1722499200,
        "value": 155010.12
      }
    ]

================================================================
6. AUTHENTICATION APIS
================================================================

Endpoint: /api/auth/login
- Method: POST
- Description: Authenticates username/password and returns JWT token.
- Request Body:
  {
    "username": "owner",
    "password": "ChangeMe123!"
  }
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "token": "<jwt-token>",
      "username": "owner",
      "biometricEnabled": true
    }
- Alternative Response:
  - Status Code: 401 Unauthorized
  - Body:
    {
      "message": "Invalid username or password"
    }

Endpoint: /api/auth/me
- Method: GET
- Description: Returns logged-in user profile for active token.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "username": "owner",
      "biometricEnabled": true
    }

================================================================
7. WEBAUTHN (BIOMETRIC) APIS
================================================================

Endpoint: /api/auth/webauthn/registration/options
- Method: POST
- Description: Generates registration challenge/options for current user.
- Response:
  - Status Code: 200 OK
  - Body (example shape):
    {
      "challenge": "<base64url>",
      "rp": {
        "id": "localhost",
        "name": "Portiq"
      },
      "user": {
        "id": "<base64url>",
        "name": "owner",
        "displayName": "owner"
      },
      "pubKeyCredParams": [
        {
          "type": "public-key",
          "alg": -7
        }
      ],
      "timeout": 60000,
      "attestation": "none",
      "authenticatorSelection": {
        "authenticatorAttachment": "platform",
        "userVerification": "required",
        "residentKey": "preferred"
      },
      "excludeCredentials": []
    }

Endpoint: /api/auth/webauthn/registration/verify
- Method: POST
- Description: Verifies registration response and stores credential.
- Request Body: browser WebAuthn credential payload (+ optional label)
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "registered": true
    }
- Alternative Response:
  - Status Code: 400 Bad Request
  - Body:
    {
      "message": "Origin mismatch - expected http://localhost:5173"
    }

Endpoint: /api/auth/webauthn/login/options
- Method: POST
- Description: Generates login challenge/options for biometric sign-in.
- Response:
  - Status Code: 200 OK
  - Body (example shape):
    {
      "challenge": "<base64url>",
      "rpId": "localhost",
      "timeout": 60000,
      "userVerification": "required",
      "allowCredentials": [
        {
          "type": "public-key",
          "id": "<credential-id>"
        }
      ]
    }
- Alternative Response:
  - Status Code: 400 Bad Request
  - Body:
    {
      "message": "No biometric credential is registered yet"
    }

Endpoint: /api/auth/webauthn/login/verify
- Method: POST
- Description: Verifies WebAuthn assertion and returns JWT session.
- Request Body: browser WebAuthn assertion payload
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "token": "<jwt-token>",
      "username": "owner",
      "biometricEnabled": true
    }
- Alternative Response:
  - Status Code: 401 Unauthorized
  - Body:
    {
      "message": "Signature verification failed"
    }

================================================================
8. NEWS API
================================================================

Endpoint: /api/news
- Method: GET
- Description: Returns market headlines related to held tickers plus general market news.
- Response:
  - Status Code: 200 OK
  - Body:
    [
      {
        "title": "Apple shares rise after earnings",
        "link": "https://example.com/article",
        "source": "Yahoo Finance",
        "publishedAt": "Tue, 04 Aug 2026 08:30:00 GMT",
        "relatedTicker": "AAPL"
      }
    ]

================================================================
9. INSIGHTS SUMMARY API
================================================================

Endpoint: /api/insights/summary
- Method: GET
- Description: Returns plain-language summary of portfolio performance.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "summary": "Your portfolio is currently up 10.5%. AAPL is your strongest performer while XYZ is lagging."
    }
- Alternative Responses:
  - Status Code: 503 Service Unavailable
  - Body:
    {
      "message": "Summaries are not configured on this server"
    }
  - Status Code: 502 Bad Gateway
  - Body:
    {
      "message": "The summary service returned no choices"
    }

================================================================
10. PORTFOLIO MANAGEMENT APIS (LEGACY BUT AVAILABLE)
================================================================

Endpoint: /api/portfolios
- Method: GET
- Description: Lists all portfolios.
- Response:
  - Status Code: 200 OK
  - Body:
    [
      {
        "id": 1,
        "name": "Tech Growth",
        "description": "US tech portfolio",
        "createdAt": "2026-08-04T08:00:00",
        "holdings": []
      }
    ]

Endpoint: /api/portfolios/{id}
- Method: GET
- Description: Returns portfolio by ID.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "id": 1,
      "name": "Tech Growth",
      "description": "US tech portfolio",
      "createdAt": "2026-08-04T08:00:00",
      "holdings": []
    }

Endpoint: /api/portfolios
- Method: POST
- Description: Creates a portfolio.
- Request Body:
  {
    "name": "Dividend Basket",
    "description": "Income-focused stocks"
  }
- Response:
  - Status Code: 201 Created
  - Body:
    {
      "id": 2,
      "name": "Dividend Basket",
      "description": "Income-focused stocks",
      "createdAt": "2026-08-04T09:00:00",
      "holdings": []
    }

Endpoint: /api/portfolios/{id}
- Method: PUT
- Description: Updates portfolio details.
- Request Body:
  {
    "name": "Dividend Basket Updated",
    "description": "Updated strategy"
  }
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "id": 2,
      "name": "Dividend Basket Updated",
      "description": "Updated strategy",
      "createdAt": "2026-08-04T09:00:00",
      "holdings": []
    }

Endpoint: /api/portfolios/{id}
- Method: DELETE
- Description: Deletes a portfolio.
- Response:
  - Status Code: 204 No Content
  - Body: (empty)

Endpoint: /api/portfolios/{id}/performance
- Method: GET
- Description: Returns performance summary for one portfolio.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "portfolioId": 2,
      "portfolioName": "Dividend Basket Updated",
      "totalCostBasis": 50000.00,
      "totalCurrentValue": 54200.00,
      "totalGainLoss": 4200.00,
      "gainLossPercent": 8.40,
      "holdings": []
    }

================================================================
11. PORTFOLIO HOLDINGS APIS (LEGACY PER-PORTFOLIO)
================================================================

Endpoint: /api/portfolios/{portfolioId}/holdings
- Method: GET
- Description: Lists holdings within a specific portfolio.
- Response:
  - Status Code: 200 OK
  - Body:
    [
      {
        "id": 10,
        "ticker": "AAPL",
        "name": "Apple Inc.",
        "type": "STOCK",
        "quantity": 10,
        "purchasePrice": 150.00,
        "purchaseDate": "2024-01-15"
      }
    ]

Endpoint: /api/portfolios/{portfolioId}/holdings/{id}
- Method: GET
- Description: Returns one holding in a portfolio.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "id": 10,
      "ticker": "AAPL",
      "name": "Apple Inc.",
      "type": "STOCK",
      "quantity": 10,
      "purchasePrice": 150.00,
      "purchaseDate": "2024-01-15"
    }

Endpoint: /api/portfolios/{portfolioId}/holdings
- Method: POST
- Description: Adds a holding to a specific portfolio.
- Request Body:
  {
    "ticker": "MSFT",
    "name": "Microsoft Corp.",
    "type": "STOCK",
    "quantity": 5,
    "purchasePrice": 320.00,
    "purchaseDate": "2024-02-01"
  }
- Response:
  - Status Code: 201 Created
  - Body:
    {
      "id": 12,
      "ticker": "MSFT",
      "name": "Microsoft Corp.",
      "type": "STOCK",
      "quantity": 5,
      "purchasePrice": 320.00,
      "purchaseDate": "2024-02-01"
    }

Endpoint: /api/portfolios/{portfolioId}/holdings/{id}
- Method: PUT
- Description: Updates a holding in a specific portfolio.
- Request Body:
  {
    "ticker": "MSFT",
    "name": "Microsoft Corp.",
    "type": "STOCK",
    "quantity": 7,
    "purchasePrice": 325.00,
    "purchaseDate": "2024-02-01"
  }
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "id": 12,
      "ticker": "MSFT",
      "name": "Microsoft Corp.",
      "type": "STOCK",
      "quantity": 7,
      "purchasePrice": 325.00,
      "purchaseDate": "2024-02-01"
    }

Endpoint: /api/portfolios/{portfolioId}/holdings/{id}
- Method: DELETE
- Description: Deletes holding from a specific portfolio.
- Response:
  - Status Code: 204 No Content
  - Body: (empty)

================================================================
12. NON-FUNCTIONAL / DOCUMENTATION ENDPOINTS
================================================================

Endpoint: /swagger-ui.html
- Method: GET
- Description: Interactive Swagger UI for API exploration.
- Response:
  - Status Code: 200 OK
  - Content-Type: text/html

Endpoint: /api-docs
- Method: GET
- Description: OpenAPI JSON document.
- Response:
  - Status Code: 200 OK
  - Content-Type: application/json

Endpoint: /actuator/health
- Method: GET
- Description: Health check endpoint.
- Response:
  - Status Code: 200 OK
  - Body:
    {
      "status": "UP"
    }

================================================================
AUTH HEADER REQUIREMENT FOR PROTECTED ENDPOINTS
================================================================
Authorization: Bearer <jwt-token>

================================================================
NOTES
================================================================
1) All request/response examples above are representative requirement samples.
2) Numeric fields are shown as numbers; actual serialization may include decimal scale.
3) File export/import endpoints use multipart or binary response, not JSON body payloads.
4) For frontend Dashboard stats usage, the core endpoint is GET /api/holdings.
