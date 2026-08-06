# Portiq — Product Backlog & User Story Documentation

## 1. Introduction

Portiq is an intelligent investment portfolio management platform designed to help investors monitor, organize, and analyze their investments from a single dashboard. While most portfolio trackers focus only on displaying holdings and profit/loss, Portiq aims to provide deeper insights by combining live market data, portfolio analytics, AI-assisted summaries, and explainable investment recommendations.

The project was developed with a strong focus on security, usability, and scalability. Features such as encrypted data storage, biometric authentication, AI-assisted document import, and portfolio risk analysis were introduced to simplify investment management while ensuring user data remains protected.

The following user stories describe the major functional requirements implemented during the development of Portiq. Each story represents a business requirement from the user's perspective and defines the expected behaviour of the system.

---

## Epic 1 – User Authentication & Security

This epic focuses on optional secure access controls and strong data protection. The client accepted that the product can run without secure login in trusted/demo environments, while security features are available and recommended for production use.

### US-01: Optional Secure Login to Access Portfolio

#### User Story

> As an investor, I want secure login to be available so that only authorized users can access my portfolio and financial information when authentication is enabled.

#### Background

Investment portfolios contain sensitive financial information, including investment amounts, purchase prices, and portfolio performance. Protecting this information was one of the first priorities during development. The client decision was to keep secure login as a configurable feature: enabled by default for real deployments, but optional for trusted internal/demo usage.

#### Acceptance Criteria

- Users should be able to log in using valid credentials.
- Invalid username or password should display an appropriate error message.
- A successful login should generate a secure authentication token.
- Protected APIs should only be accessible after authentication.
- Expired sessions should require the user to log in again.
- Passwords should never be stored in plain text.
- The application can be run in a trusted/demo mode without secure login if explicitly approved by the client.

#### Technical Notes

- Authentication implemented using Spring Security.
- JWT (JSON Web Token) used for session management.
- Passwords encrypted using BCrypt hashing.
- All protected endpoints validate the authentication token before processing requests.

Priority: High
Story Points: 5

---

### US-02: Support Biometric Authentication (Additional Feature)

#### User Story

> As an investor, I want optional biometric authentication so that I can access my portfolio more conveniently without always entering my password.

#### Background

While passwords provide security, frequent logins can reduce convenience for users. As an additional feature beyond baseline login, Portiq supports biometric authentication using the WebAuthn standard, allowing compatible devices to authenticate users through fingerprint or facial recognition.

Unlike many applications where biometric authentication simply replaces the password locally, Portiq verifies biometric credentials securely on the backend before granting access.

#### Acceptance Criteria

- Users should be able to register a biometric credential.
- Registered credentials should be stored securely.
- Authentication requests should be verified before granting access.
- Invalid signatures should be rejected.
- Replay attacks should be prevented.
- Users should still have the option to log in using their password.

#### Technical Notes

- WebAuthn protocol implemented.
- ES256 signature verification.
- Secure credential registration and validation.
- Sign-count verification implemented to prevent replay attacks.

Priority: High
Story Points: 8

---

## Epic 2 – Portfolio & Holdings Management

Managing investments is the core functionality of Portiq. This epic covers everything related to creating, maintaining, importing, and securing investment holdings.

### US-03: Create and Maintain Investment Holdings

#### User Story

> As an investor, I want to add and manage my investment holdings so that I always have an up-to-date record of my portfolio.

#### Background

Many investors maintain their investment records manually using spreadsheets or across multiple broker applications. Portiq was designed to centralize this information, allowing users to maintain all their holdings in one place while making future analysis significantly easier.

Users should be able to record all essential investment details without unnecessary complexity.

#### Acceptance Criteria

Users should be able to:

- Add a new investment holding.
- Edit existing holdings.
- Delete holdings that are no longer required.
- View all holdings within their portfolio.

Each holding should store:

- Stock ticker
- Company name
- Asset type
- Quantity
- Purchase price
- Purchase date

Changes made by the user should immediately be reflected throughout the application.

#### Technical Notes

- CRUD operations implemented using REST APIs.
- Holding information stored using Spring Data JPA.
- Portfolio updates reflected dynamically across dashboard components.

Priority: High
Story Points: 8

---

### US-04: Automatically Merge Duplicate Holdings

#### User Story

> As an investor, I want Portiq to intelligently merge duplicate stock entries so that my portfolio remains clean and accurate without requiring manual calculations.

#### Background

During discussions, we identified a common issue faced by investors. Users often purchase the same stock multiple times at different prices. Creating a separate record for every purchase would quickly clutter the portfolio and make analysis difficult.

Instead of creating duplicate entries, the application automatically combines investments belonging to the same stock.

#### Acceptance Criteria

- When a stock already exists in the portfolio, a duplicate record should not be created.
- The quantity should be updated automatically.
- Purchase price should be recalculated using a weighted average.
- The updated holding should appear as a single consolidated investment.

#### Technical Notes

Implemented using the portfolio merge logic within the Holding Service.

Example — existing holding:

| Stock | Quantity | Purchase Price |
|-------|----------|-----------------|
| TCS   | 10       | ₹3,000          |

New entry:

| Stock | Quantity | Purchase Price |
|-------|----------|-----------------|
| TCS   | 5        | ₹3,300          |

Result:

| Stock | Quantity | Average Price              |
|-------|----------|-----------------------------|
| TCS   | 15       | Automatically recalculated |

Priority: High
Story Points: 5

---

### US-05: Protect Sensitive Financial Information

#### User Story

> As an investor, I want my financial information to remain encrypted while stored in the database so that my investment details remain secure even if database access is compromised.

#### Background

Financial information is highly sensitive. Simply protecting user accounts through authentication is not sufficient because database-level attacks can still expose confidential data.

To strengthen security, Portiq encrypts sensitive portfolio information before storing it in the database. The application automatically decrypts this information only when authorized users access it.

Operational clarification:

- Environment variables are not generated by the application after reboot. They must be supplied by deployment configuration (for example Docker Compose env file, CI/CD secrets, systemd service environment, or cloud secret manager).
- On every restart, the server reads DB_ENCRYPTION_KEY from that runtime environment again.
- If the key changes, existing encrypted records become unreadable. Therefore, the same key must persist across reboots and deployments.

Why AES-256-GCM and not SHA-256:

- SHA-256 is a one-way hash. It cannot be decrypted back to original values.
- Portfolio fields (ticker, quantity, purchase price, dates) must be read back for calculations and display, so reversible encryption is required.
- AES-256-GCM provides reversible encryption plus integrity protection (tamper detection), which is appropriate for stored financial data.
- SHA-256 is suitable for passwords/checksums, which is why password storage uses BCrypt hashing.

#### Acceptance Criteria

- Sensitive portfolio fields should be encrypted before storage.
- Authorized users should be able to retrieve decrypted information transparently.
- Encryption keys should not be exposed within application code.
- Unauthorized database access should not reveal readable financial data.

#### Technical Notes

- AES-256-GCM encryption implemented.
- JPA Attribute Converters handle automatic encryption and decryption.
- Encryption applied to sensitive holding information before database persistence.

Priority: Critical
Story Points: 8

---

## Epic 3 – Portfolio Dashboard & Performance Analytics

Once investment data is available, users need meaningful insights rather than raw numbers. This epic focuses on presenting portfolio information in an intuitive and visually informative manner.

### US-06: Visualize Portfolio Performance

#### User Story

> As an investor, I want to view my portfolio through an interactive dashboard so that I can quickly understand how my investments are performing without manually calculating returns.

#### Background

Simply displaying tables of investment data provides limited value to investors. During the design phase, the team decided that key portfolio metrics should be visible immediately after login, allowing users to understand their portfolio's overall performance at a glance.

The dashboard combines investment values, portfolio summaries, charts, and performance indicators into a single screen.

#### Acceptance Criteria

The dashboard should display:

- Total amount invested.
- Current portfolio value.
- Overall profit or loss.
- Individual holding performance.
- Portfolio distribution.
- Visual charts representing portfolio composition and growth.

The dashboard should update automatically whenever portfolio data changes.

#### Technical Notes

- Dashboard developed using React.
- Interactive visualizations implemented using Chart.js.
- Data retrieved through REST APIs.

Priority: High
Story Points: 8

---

## Epic 4 – Market Data Integration

A portfolio management platform is only useful when it reflects current market conditions. This epic focuses on integrating live market data so that portfolio valuations and investment insights remain accurate and up to date without requiring manual updates from the user.

### US-07: Retrieve Live Market Prices

#### User Story

> As an investor, I want Portiq to fetch the latest stock prices automatically so that I can always view the current value of my investments.

#### Background

The value of an investment portfolio changes continuously with market movements. Asking users to manually update stock prices would defeat the purpose of using a portfolio management application. To address this, Portiq retrieves market data directly from Yahoo Finance and uses it to calculate the latest portfolio valuation.

Since market prices do not change every second for most investors' needs, caching was introduced to reduce unnecessary API calls while maintaining near real-time accuracy.

#### Acceptance Criteria

- The application should retrieve the latest available stock prices automatically.
- Portfolio valuation should be recalculated whenever new market data is received.
- If price data is temporarily unavailable, the application should continue functioning without crashing.
- Frequently requested market data should be served from cache whenever possible.
- Users should always see the most recent successfully retrieved market prices.

#### Technical Notes

- Yahoo Finance Chart API used for historical and current market prices.
- Market data cached using Caffeine Cache to reduce repeated API requests.
- Price updates automatically reflected throughout dashboard calculations.

Priority: High
Story Points: 8

---

## Epic 5 – AI-Assisted Portfolio Management

Managing investment portfolios often involves repetitive tasks such as entering transactions, importing statements, or interpreting portfolio performance. This epic introduces AI-assisted features that simplify these activities while keeping the actual financial calculations deterministic and explainable.

### US-08: Generate an AI Portfolio Summary

#### User Story

> As an investor, I want a simple explanation of my portfolio performance so that I can quickly understand how my investments are doing without interpreting multiple charts and financial metrics.

#### Background

Not every investor is comfortable interpreting technical financial information. While charts and numerical indicators provide detailed insights, many users simply want a concise explanation of their portfolio's current status.

Portiq generates a natural language summary of the user's portfolio by combining calculated portfolio statistics with AI-generated narration. Importantly, the AI does not calculate portfolio performance — it only converts computed results into readable explanations.

#### Acceptance Criteria

- Users should be able to request an AI-generated portfolio summary.
- The summary should explain overall portfolio performance in plain language.
- The explanation should highlight important portfolio observations.
- If the AI service is unavailable, the application should still provide a system-generated summary.

#### Technical Notes

- Portfolio statistics generated by backend services.
- Groq LLM used only for language generation.
- AI output supplements, but does not replace, portfolio calculations.

Priority: Medium
Story Points: 8

---

### US-09: Import Holdings from Investment Statement Images

#### User Story

> As an investor, I want to upload an image of my investment statement so that my portfolio can be created automatically without manually entering every holding.

#### Background

Entering dozens of investments manually can be tedious and time-consuming, especially for new users migrating from another investment platform. To simplify onboarding, Portiq allows users to upload screenshots or scanned statements and automatically extracts investment details.

The extracted information is reviewed before being added to the user's portfolio, reducing manual effort while maintaining accuracy.

#### Acceptance Criteria

- Users should be able to upload supported investment statement images.
- The application should identify investment details from the uploaded image.
- Extracted holdings should include available stock information such as ticker, quantity, purchase price, and purchase date.
- Users should be able to review extracted information before importing it.
- Successfully imported holdings should appear within the portfolio.

#### Technical Notes

- Vision-enabled language model used for document understanding.
- Extracted information converted into Holding objects.
- Validation performed before portfolio update.

Priority: High
Story Points: 13

---

### US-10: Import Portfolio Data from CSV or Excel Files

#### User Story

> As an investor, I want to import my existing investment records from CSV or Excel files so that I can start using Portiq without recreating my portfolio manually.

#### Background

Investors often maintain records using spreadsheets or export portfolio reports from brokerage platforms. However, every broker follows a different file format. Instead of forcing users to match one predefined template, Portiq intelligently interprets different column structures and imports the relevant information automatically.

This significantly improves usability for users switching from other investment platforms.

#### Acceptance Criteria

- Users should be able to upload CSV files.
- Different column arrangements should be interpreted automatically.
- Valid investment records should be imported successfully.
- Invalid or incomplete records should be reported without stopping the import process.
- Successfully imported holdings should immediately appear in the portfolio.

#### Technical Notes

- CSV processing implemented using OpenCSV.
- Excel support handled through Apache POI.
- AI-assisted parsing used for flexible file interpretation.

Priority: High
Story Points: 8

---

## Epic 6 – Portfolio Reports & Data Export

While dashboards provide an interactive way to view portfolio information, users may also need downloadable reports for record keeping, sharing, or offline analysis. This epic focuses on exporting portfolio information into commonly used formats.

### US-11: Export Portfolio Data as CSV

#### User Story

> As an investor, I want to export my portfolio as a CSV file so that I can analyse my investments using spreadsheet software or maintain personal records.

#### Background

Many investors continue to use Excel or similar tools for custom financial analysis. Providing CSV export ensures users are not locked into the application and can easily use their investment data elsewhere whenever required.

#### Acceptance Criteria

- Users should be able to export their portfolio with a single action.
- Exported files should contain all investment details.
- Data should be structured in a spreadsheet-friendly format.
- Exported values should match portfolio information displayed within the application.

#### Technical Notes

- CSV generation implemented within backend export services.
- Compatible with Microsoft Excel and Google Sheets.

Priority: Medium
Story Points: 3

---

### US-12: Generate Portfolio Reports in PDF Format

#### User Story

> As an investor, I want to download my portfolio as a professionally formatted PDF report so that I can maintain investment records or share portfolio summaries when required.

#### Background

PDF reports provide a consistent format that is suitable for printing, archiving, or sharing. Unlike spreadsheets, PDFs preserve formatting and are easier to read during meetings or financial reviews.

Portiq generates downloadable reports containing important portfolio information in a structured layout.

#### Acceptance Criteria

- Users should be able to generate a PDF version of their portfolio.
- Reports should include investment details and portfolio summaries.
- Generated PDFs should maintain consistent formatting across devices.
- Downloaded reports should accurately reflect current portfolio data.

#### Technical Notes

- PDF generation implemented using OpenPDF.
- Portfolio data formatted into printable report templates.

Priority: Medium
Story Points: 5

---

## Epic 7 – Market News & Investment Information

Investors do not make decisions based only on numbers. Market movements are often influenced by external events, company updates, and broader financial news. This epic focuses on providing users with relevant market information alongside their portfolio insights so they can better understand factors affecting their investments.

### US-13: View Relevant Market News

#### User Story

> As an investor, I want to view relevant financial news related to my investments so that I can stay informed about market events that may impact my portfolio.

#### Background

During the design phase, we identified that portfolio performance alone does not explain why a stock price changes. Investors often need additional context, such as market updates, company-related news, or sector-level developments.

To provide this context, Portiq integrates financial news sources and displays relevant updates alongside portfolio information. The feature was designed to improve awareness without requiring users to search multiple platforms separately.

#### Acceptance Criteria

- Users should be able to view recent financial news.
- News should be relevant to tracked investments whenever possible.
- Articles should contain important information such as title, source, and reference link.
- The application should continue functioning even if a news source temporarily fails.
- Frequently requested news should not generate unnecessary external requests.

#### Technical Notes

- Financial news retrieved through RSS feeds.
- Yahoo Finance RSS and Google News RSS sources integrated.
- News responses cached to improve performance.
- News retrieval separated from portfolio calculation logic.

Priority: Medium
Story Points: 5

---

## Epic 8 – Intelligent Portfolio Risk Analysis

This epic represents one of the major analytical components of Portiq. Instead of simply showing profit and loss, the system evaluates investment risk using multiple financial indicators. The objective was to provide users with an understandable risk assessment while keeping the calculations transparent and explainable.

The team intentionally avoided relying on external risk-rating APIs because such systems are often black-box solutions. Instead, Portiq calculates risk internally using historical market data.

### US-14: Calculate Risk Score for Individual Investments

#### User Story

> As an investor, I want to understand the risk level of each stock in my portfolio so that I can identify investments that may require closer attention.

#### Background

A stock's risk cannot be determined by looking at only one factor. For example, a stock may have low daily fluctuations but still represent a significant risk if it occupies a large portion of the portfolio.

After evaluating different approaches, Portiq uses multiple financial indicators to generate a more balanced risk assessment:

- Price volatility
- Market sensitivity (beta)
- Maximum historical decline
- Portfolio concentration
- Value-at-Risk

Each factor represents a different dimension of investment risk, allowing the final score to provide a more complete picture.

#### Acceptance Criteria

The system should:

- Calculate a risk score for individual holdings.
- Consider multiple financial risk indicators.
- Generate a score between 0 and 100.
- Assign an understandable risk category.

Risk categories:

| Score Range | Risk Level |
|-------------|------------|
| Below 25    | Low        |
| 25–50       | Moderate   |
| 50–75       | High       |
| Above 75    | Very High  |

- Users should be able to understand why a stock received a particular risk level.

#### Technical Notes

Implemented using:

- `RiskAnalysisService`
- `MetricsCalculator`
- `MarketDataFetcher`

Risk components:

| Metric | Purpose |
|--------|---------|
| Annualized Volatility | Measures daily price fluctuation |
| Beta | Measures sensitivity compared to market movement |
| Maximum Drawdown | Identifies worst historical decline |
| Concentration | Measures portfolio dependency on one holding |
| Historical VaR | Estimates potential downside risk |

Additional design decisions:

- Missing data does not cause system failure.
- Available metrics are weighted dynamically.
- Cash holdings are considered risk-free.

Priority: High
Story Points: 13

---

### US-15: Evaluate Overall Portfolio Risk

#### User Story

> As an investor, I want to understand the overall risk of my portfolio so that I can improve diversification and manage my investment exposure.

#### Background

During implementation discussions, the team identified that simply averaging individual stock risk scores would not represent actual portfolio behaviour.

For example, two highly volatile stocks may reduce overall portfolio risk if they do not move together. Therefore, Portiq evaluates the portfolio as a combined investment rather than treating every holding independently.

The application creates a synthetic portfolio return series by combining individual stock returns based on their portfolio weights.

#### Acceptance Criteria

The system should:

- Calculate portfolio-level risk.
- Consider individual holding weights.
- Account for diversification effects.
- Generate warnings for potentially risky portfolio conditions.

The system should identify:

- Single stock allocation exceeding 25%.
- Portfolio containing fewer than five holdings.
- Portfolio beta greater than 1.3.
- Presence of very high-risk investments.
- Missing market data.

#### Technical Notes

Implementation approach:

- Historical daily returns collected for portfolio holdings.
- Individual returns weighted according to portfolio allocation.
- Combined portfolio return series generated.
- Risk metrics calculated on the complete portfolio.

This approach provides a more realistic representation of actual investor exposure.

Priority: High
Story Points: 13

---

## Epic 9 – Explainable Investment Recommendation Engine

This epic focuses on providing actionable investment guidance. Instead of generating unexplained AI suggestions, Portiq uses a deterministic scoring engine where every recommendation can be traced back to specific financial signals.

The recommendation system was designed around the principle that investment suggestions should be explainable and reproducible.

### US-16: Generate Personalized Stock Recommendations

#### User Story

> As an investor, I want personalized investment recommendations based on market behaviour and my portfolio so that I can identify possible opportunities and risks.

#### Background

A common issue with recommendation systems is that users receive suggestions without understanding the reasoning behind them.

Portiq addresses this by combining multiple market signals and generating a transparent opportunity score. Each recommendation is supported by measurable indicators rather than being generated randomly.

#### Acceptance Criteria

The system should:

- Analyze available market data.
- Generate an opportunity score for stocks.
- Provide a recommended action.
- Explain the factors contributing to the recommendation.

Recommendation actions:

| Score | Action |
|-------|--------|
| 40 or above | ACCUMULATE |
| -15 to 40 | HOLD |
| -40 to -15 | TRIM |
| Below -40 | SELL |

#### Technical Notes

Implemented using:

- `RecommendationService`

Signals considered:

| Signal | Purpose |
|--------|---------|
| 90-day Momentum | Identifies recent price movement |
| SMA50/SMA200 Trend | Determines long-term trend direction |
| RSI | Identifies overbought/oversold conditions |
| 52-week Range | Evaluates relative price position |
| Risk Adjustment | Reduces confidence for highly risky stocks |

The final score is generated using weighted rules, ensuring repeatable results.

Priority: High
Story Points: 13

---

### US-17: Generate Position-Aware Recommendations

#### User Story

> As an investor, I want recommendations to consider my existing holdings so that suggestions are relevant to my actual portfolio situation.

#### Background

A recommendation that ignores an investor's current holdings can be misleading. For example, buying more of a stock that already represents 40% of the portfolio may increase concentration risk.

Therefore, Portiq adjusts recommendations based on the user's current position size and investment performance.

#### Acceptance Criteria

The system should consider:

- Current portfolio allocation.
- Existing profit/loss percentage.
- Holding concentration.
- Current market trend.

The system should:

- Reduce confidence for oversized positions.
- Suggest trimming highly concentrated holdings.
- Consider strong gains or losses when generating advice.

#### Technical Notes

Position-based adjustments include:

- Loss greater than 20% with negative trend → recommendation penalty.
- Profit greater than 20% with positive trend → positive adjustment.
- Allocation exceeding 25% → concentration penalty.

Priority: Medium
Story Points: 8

---

### US-18: Identify New Investment Opportunities

#### User Story

> As an investor, I want suggestions for stocks that I do not currently own so that I can discover potential additions to my portfolio.

#### Background

Apart from analyzing existing investments, Portiq also evaluates potential opportunities from a predefined stock universe.

The objective is not to suggest every available stock but to filter opportunities and show only those that satisfy the required conditions.

#### Acceptance Criteria

- System should evaluate stocks outside the user's current holdings.
- Existing investments should be excluded from suggestions.
- Only stocks meeting the minimum opportunity threshold should be displayed.
- Each suggestion should include supporting reasoning.

#### Technical Notes

- Configurable NSE large-cap stock universe.
- Parallel processing used for evaluating multiple stocks.
- Minimum score threshold applied to avoid unnecessary recommendations.
- Dedicated executor pool used for market-data processing.

Priority: Medium
Story Points: 8

---

## Epic 10 – AI-Assisted Explanation & Investor Insights

This epic focuses on improving the user experience by converting complex financial calculations into simple, understandable explanations. The core decision-making logic in Portiq is intentionally deterministic, while AI is used only as an enhancement layer to improve communication.

The design approach was based on the principle that AI should assist users in understanding decisions rather than making uncontrolled financial decisions on their behalf.

### US-19: Generate Human-Friendly Investment Explanations

#### User Story

> As an investor, I want complex portfolio analysis results to be explained in simple language so that I can understand the reasoning behind risk assessments and recommendations.

#### Background

Financial metrics such as volatility, beta, RSI, momentum, and drawdown can be difficult for non-expert investors to interpret. Although Portiq calculates these metrics accurately, presenting only numerical values would reduce their usefulness.

To solve this problem, an AI-based explanation layer was introduced. The AI receives already calculated results and converts them into meaningful explanations that users can understand.

A key design decision was made to keep financial calculations independent from AI. This ensures that recommendations remain consistent, explainable, and reliable even when the AI service is unavailable.

#### Acceptance Criteria

The system should:

- Convert technical financial results into understandable explanations.
- Explain why a particular risk level was assigned.
- Explain why a stock received a specific recommendation.
- Provide readable portfolio summaries.
- Continue functioning even if the AI service fails.
- Clearly differentiate between AI-generated explanations and system-generated explanations.

#### Technical Notes

Implementation approach:

- Financial calculations performed by Java-based services.
- AI used only for generating natural language explanations.
- Groq LLM integrated as an optional enhancement layer.
- Fallback explanations generated using predefined system logic.

Design advantages:

- No dependency on AI availability for core functionality.
- Recommendations remain reproducible.
- Reduced risk of AI hallucination affecting financial decisions.

Priority: Medium
Story Points: 8

---

## Epic 11 – Application Deployment & Developer Support

This epic focuses on making the application easier to deploy, maintain, and extend. Along with building user-facing features, the project required proper development practices to ensure consistency between different environments.

### US-20: Deploy Application Using Containerization

#### User Story

> As a developer, I want Portiq to run in a consistent deployment environment so that the application can be easily configured, tested, and deployed across different systems.

#### Background

During development, differences between local environments can create issues related to dependencies, database configuration, and application setup. Containerization helps eliminate these inconsistencies by packaging the application and its required services together.

Portiq uses container-based deployment practices to simplify setup and improve reliability.

#### Acceptance Criteria

The application should:

- Start successfully in a fresh environment.
- Maintain consistent configurations across systems.
- Allow required services to run together.
- Use environment variables for sensitive configuration values.
- Support easy deployment and testing.

#### Technical Notes

Implemented using:

- Docker
- Docker Compose
- Environment-based configuration

Benefits:

- Simplified developer setup.
- Reduced environment-related errors.
- Easier deployment workflow.

Priority: High
Story Points: 5

---

### US-21: Maintain API Documentation

#### User Story

> As a developer, I want well-documented APIs so that frontend and backend development can be integrated efficiently and future changes can be managed easily.

#### Background

Portiq follows a frontend-backend separation approach where the React application communicates with backend services through REST APIs.

Without proper API documentation, maintaining communication between frontend and backend becomes difficult. Therefore, API documentation was maintained throughout development to improve collaboration and future scalability.

#### Acceptance Criteria

API documentation should include:

- Available endpoints.
- Request formats.
- Response structures.
- Required parameters.
- Authentication requirements.

Developers should be able to understand and test APIs without needing to inspect backend code directly.

#### Technical Notes

Implemented using:

- OpenAPI documentation.
- Swagger support.
- REST API documentation standards.

Priority: Medium
Story Points: 3

---

## Overall Project Statistics

| Metric | Value |
|--------|-------|
| Total Epics | 11 |
| Total User Stories | 22 |
| Total Estimated Story Points | 168 |

---

## Conclusion

The Portiq backlog represents the complete functionality developed as part of the project lifecycle. The stories cover not only the visible user features but also the engineering decisions required to build a reliable financial application.

The project progressed beyond basic portfolio tracking by introducing:

- Secure authentication mechanisms.
- Encrypted financial data storage.
- Automated portfolio importing.
- Live market data integration.
- AI-assisted portfolio insights.
- Explainable risk analysis.
- Deterministic recommendation algorithms.
- Scalable deployment practices.

A key design philosophy throughout development was maintaining transparency and reliability. While AI was used to enhance user experience, all financial calculations and recommendation decisions remain based on explainable logic, ensuring users can understand the reasoning behind every insight provided by Portiq.
