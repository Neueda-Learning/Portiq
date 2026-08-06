1.  DASHBOARD / STATS (FLAT HOLDINGS SUMMARY)

GET: /api/holdings - Description: Retrieves statistical data. -
Response: - Body: { portfolioId, portfolioName, totalCostBasis,
totalCurrentValue, totalGainLoss, gainLossPercent, holdings, { id,
ticker, name, type, quantity, purchasePrice, currentPrice, costBasis,
currentValue, gainLoss, gainLossPercent, purchaseDate, } ] }

2.  HOLDINGS - ADD / UPDATE / DELETE (FLAT)

POST: /api/holdings - Description: Adds a new holding in default
portfolio, or merges into existing ticker. - Request Body: { ticker,
name, type, quantity, purchasePrice, purchaseDate, } - Response: - Body:
{ id, ticker, name, type, quantity, purchasePrice, purchaseDate, }

PUT: /api/holdings/{id} - Description: Updates an existing holding by
ID. - Request Body: { ticker, name, type, quantity, purchasePrice,
purchaseDate, } - Response: - Body: { id, ticker, name, type, quantity,
purchasePrice, purchaseDate, }

DELETE: /api/holdings/{id} - Description: Deletes an existing holding by
ID. - Response: - Body:

3.  HOLDINGS IMPORT APIS

POST: /api/holdings/import/csv - Description: Imports holdings from a
CSV file and merges duplicate tickers. - Request: multipart/form-data
with field name ‘file’ - Response: - Body: { imported, errors, ] }

POST: /api/holdings/import/image - Description: Extracts holdings from
statement image and imports them. - Request: multipart/form-data with
field name ‘file’ - Response: - Body: { imported, errors, } -
Alternative Responses: - Body: { message, } - Body: { message, }

GET: /api/holdings/import/csv/sample - Description: Downloads sample CSV
template. - Response: - Content-Type: text/csv - Body: file download
(sample-holdings.csv)

4.  HOLDINGS EXPORT APIS

GET: /api/holdings/export/csv - Description: Exports holdings report as
CSV. - Response: - Content-Type: text/csv - Body: file download
(portiq-holdings.csv)

GET: /api/holdings/export/pdf - Description: Exports holdings report as
PDF. - Response: - Content-Type: application/pdf - Body: file download
(portiq-holdings.pdf)

5.  PORTFOLIO VALUE HISTORY API

GET: /api/holdings/history?range=1d|1w|1m|all - Description: Returns
historical points for portfolio trend chart. - Response: - Body: [ {
timestamp, value, }, { timestamp, value, } ]

6.  AUTHENTICATION APIS

POST: /api/auth/login - Description: Authenticates username/password and
returns JWT token. - Request Body: { username, password, } - Response: -
Body: { token, username, biometricEnabled, } - Alternative Response: -
Body: { message, }

GET: /api/auth/me - Description: Returns logged-in user profile for
active token. - Response: - Body: { username, biometricEnabled, }

7.  WEBAUTHN (BIOMETRIC) APIS

POST: /api/auth/webauthn/registration/options - Description: Generates
registration challenge/options for current user. - Response: - Body: {
challenge, rp, id, name, }, user, id, name, displayName, },
pubKeyCredParams, { type, alg, } ], timeout, attestation,
authenticatorSelection, authenticatorAttachment, userVerification,
residentKey, }, excludeCredentials, }

POST: /api/auth/webauthn/registration/verify - Description: Verifies
registration response and stores credential. - Request Body: browser
WebAuthn credential payload (+ optional label) - Response: - Body: {
registered, } - Alternative Response: - Body: { message, }

POST: /api/auth/webauthn/login/options - Description: Generates login
challenge/options for biometric sign-in. - Response: - Body: {
challenge, rpId, timeout, userVerification, allowCredentials, { type,
id, } ] } - Alternative Response: - Body: { message, }

POST: /api/auth/webauthn/login/verify - Description: Verifies WebAuthn
assertion and returns JWT session. - Request Body: browser WebAuthn
assertion payload - Response: - Body: { token, username,
biometricEnabled, } - Alternative Response: - Body: { message, }

8.  NEWS API

GET: /api/news - Description: Returns market headlines related to held
tickers plus general market news. - Response: - Body: [ { title, link,
source, publishedAt, relatedTicker, } ]

9.  INSIGHTS SUMMARY API

GET: /api/insights/summary - Description: Returns plain-language summary
of portfolio performance. - Response: - Body: { summary, } - Alternative
Responses: - Body: { message, } - Body: { message, }

10. PORTFOLIO MANAGEMENT APIS (LEGACY BUT AVAILABLE)

GET: /api/portfolios - Description: Lists all portfolios. - Response: -
Body: [ { id, name, description, createdAt, holdings, } ]

GET: /api/portfolios/{id} - Description: Returns portfolio by ID. -
Response: - Body: { id, name, description, createdAt, holdings, }

POST: /api/portfolios - Description: Creates a portfolio. - Request
Body: { name, description, } - Response: - Body: { id, name,
description, createdAt, holdings, }

PUT: /api/portfolios/{id} - Description: Updates portfolio details. -
Request Body: { name, description, } - Response: - Body: { id, name,
description, createdAt, holdings, }

DELETE: /api/portfolios/{id} - Description: Deletes a portfolio. -
Response: - Body:

GET: /api/portfolios/{id}/performance - Description: Returns performance
summary for one portfolio. - Response: - Body: { portfolioId,
portfolioName, totalCostBasis, totalCurrentValue, totalGainLoss,
gainLossPercent, holdings, }

11. PORTFOLIO HOLDINGS APIS (LEGACY PER-PORTFOLIO)

GET: /api/portfolios/{portfolioId}/holdings - Description: Lists
holdings within a specific portfolio. - Response: - Body: [ { id,
ticker, name, type, quantity, purchasePrice, purchaseDate, } ]

GET: /api/portfolios/{portfolioId}/holdings/{id} - Description: Returns
one holding in a portfolio. - Response: - Body: { id, ticker, name,
type, quantity, purchasePrice, purchaseDate, }

POST: /api/portfolios/{portfolioId}/holdings - Description: Adds a
holding to a specific portfolio. - Request Body: { ticker, name, type,
quantity, purchasePrice, purchaseDate, } - Response: - Body: { id,
ticker, name, type, quantity, purchasePrice, purchaseDate, }

PUT: /api/portfolios/{portfolioId}/holdings/{id} - Description: Updates
a holding in a specific portfolio. - Request Body: { ticker, name, type,
quantity, purchasePrice, purchaseDate, } - Response: - Body: { id,
ticker, name, type, quantity, purchasePrice, purchaseDate, }

DELETE: /api/portfolios/{portfolioId}/holdings/{id} - Description:
Deletes holding from a specific portfolio. - Response: - Body:

12. NON-FUNCTIONAL / DOCUMENTATION ENDPOINTS

GET: /swagger-ui.html - Description: Interactive Swagger UI for API
exploration. - Response: - Content-Type: text/html

GET: /api-docs - Description: OpenAPI JSON document. - Response: -
Content-Type: application/json

GET: /actuator/health - Description: Health check endpoint. -
Response: - Body: { status, }
