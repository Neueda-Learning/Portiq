PORTIQ - Risk Analysis and Recommendations Feature
Design, Brainstorming and Implementation Notes
===================================================

OVERVIEW
--------
We wanted to go beyond simple P&L tracking and give users something genuinely useful:
an honest assessment of how risky their portfolio is, and actionable guidance on what
to do with each position. The goal was to keep everything deterministic and explainable.


BRAINSTORMING - What Should Risk Even Mean?
-------------------------------------------
Our first question was: what does it mean to call a stock risky?

We considered three approaches:

  1. Just use volatility -- simple, but it ignores concentration and drawdown.
  2. Pull a risk rating from an external API -- introduces a dependency and a cost.
  3. Compute it ourselves from price history -- full control, no API key, explainable.

We went with option 3. The inputs we settled on after discussion:

  - How much does the price bounce day-to-day? (volatility)
  - Does it fall harder than the broad market when things go south? (beta)
  - What is the worst it has crashed over the past year? (max drawdown)
  - Is too much of the portfolio sitting in this one position? (concentration)
  - On a bad day, how deep does it typically cut? (Value-at-Risk)

Each of these captures a different dimension of risk. Volatility alone misses
concentration; beta alone misses how bad the worst-case scenario actually was.


DATA FOUNDATION
---------------
We needed one year of daily closing prices per ticker. Yahoo Finance v8/finance/chart
provides this at no cost.

We extracted the fetching logic into its own Spring bean -- MarketDataFetcher --
specifically so that @Cacheable goes through the Spring proxy. If the price-fetching
logic lived as a private method on the service that calls it, Spring AOP would be
bypassed and the cache would silently do nothing. Making it a separate bean was a
deliberate design decision to avoid that problem.

All the math lives in MetricsCalculator, a plain Java class with no Spring dependencies
and no I/O. It takes arrays of doubles and returns doubles. Any metric it cannot compute
honestly returns NaN rather than throwing an exception -- a ticker with only 12 trading
days is valid input, not an error condition. We set MIN_OBSERVATIONS = 30 as the minimum
threshold for metrics that need a statistically meaningful sample.

One alignment issue we caught during testing: Yahoo returns 250 bars for RELIANCE.NS
over a year but only 246 for ^NSEI over the same period. Zipping the two arrays by index
causes the windows to drift, collapsing covariance to near-zero and producing a
meaningless beta. We resolved this by aligning both series on their UTC dates before
computing anything -- only days present in both series are used.


RISK SCORING - Final Implementation (RiskAnalysisService)
----------------------------------------------------------
Each stock receives five component scores, each linearly mapped to 0-100 between a
defined floor and ceiling, then blended using fixed weights:

  Component                  Weight    Floor      Ceiling
  -------------------------  ------    -----      -------
  Annualised volatility        35%      10%         60%
  Beta (absolute value)        20%      0.5         2.0
  Max drawdown                 25%      10%         60%
  Portfolio concentration      10%       5%         40%
  Historical VaR (95%)         10%       1%          6%

Design decisions worth documenting:

  - We use absolute value of beta. A deeply negative beta carries just as much market
    exposure as a high positive one -- it moves hard with the market either way.

  - Concentration only contributes when the stock is actually held. Scoring
    concentration on a stock not in the portfolio makes no sense.

  - If a component cannot be computed, its weight is dropped and the remaining weights
    are renormalised to sum to 100%. This gives a stock with limited history an honest
    partial score rather than an artificially deflated one.

  - Cash short-circuits to score 0 / LOW rather than returning UNAVAILABLE, which
    would read as a data failure to the user.

Score to risk band:
  < 25   ->  LOW
  < 50   ->  MODERATE
  < 75   ->  HIGH
  >= 75  ->  VERY HIGH


PORTFOLIO-LEVEL RISK - The Diversification Problem
---------------------------------------------------
Early in our discussion, someone suggested simply averaging the individual stock scores
to get a portfolio score. We rejected this: averaging individual volatilities ignores
the fact that when one stock falls, another may rise. That correlation effect is the
entire mathematical justification for diversification, and ignoring it would always
overstate risk for a well-spread portfolio.

Instead, we build a synthetic daily return series for the whole portfolio. For each
trading day we sum each holding return multiplied by its weight. This produces a
realistic picture of how the portfolio actually moves as a unit, so diversification
genuinely shows up in the numbers.

Holdings without sufficient price history are excluded and the remaining weights are
renormalised to sum to 100%.

Portfolio-level warnings generated regardless of the overall score:

  - Any single position exceeds 25% of portfolio value
  - Fewer than 5 holdings (under-diversified)
  - Portfolio beta > 1.3 (more volatile than the market)
  - One or more holdings rated VERY HIGH risk
  - Missing price data for one or more holdings


RECOMMENDATIONS - Brainstorming the Signal Mix
-----------------------------------------------
We wanted recommendations a reasonably informed investor would agree with, using only
information derived from price history -- no fundamental data, no earnings, no analyst
targets, as those would all require paid APIs or scraping.

Signals we considered and settled on:

  Momentum   -- Has it been going up or down over the past 3 months? Momentum is
                the strongest near-term continuation signal in price-based analysis.

  Trend      -- Is the price above or below its long-term moving averages? Above both
                the 50-day and 200-day is a confirmed uptrend; below the 200-day is a
                structural downtrend.

  RSI        -- Is the stock oversold (cheap after a beating) or overbought (crowded)?
                Standard 14-day RSI with classic 30/70 thresholds.

  52-week    -- Near the 52-week low is a relative value signal;
  range         near the high is a caution flag.

  Risk       -- High-risk stocks can still be worth buying, but with lower conviction.
  penalty       We multiply the score by 0.7 when risk score exceeds 70, rather than
                blocking the recommendation entirely.

We debated inverting the recommendation outright on very high-risk stocks. We decided
against it: discounting conviction is honest; flipping the action would be misleading.


RECOMMENDATIONS - Final Implementation (RecommendationService)
---------------------------------------------------------------
Each stock receives an opportunity score from -100 to +100:

  Signal                         Contribution
  -----------------------------  --------------------------------------------------
  90-day momentum                Up to +/-30 (capped at the momentum percentage)
  Trend (SMA50 / SMA200)         +20 uptrend  /  -20 downtrend
  RSI-14                         +15 oversold (< 30)  /  -15 overbought (> 70)
  52-week position               +15 (bottom 20%)  /  -10 (above 85%)
  Risk penalty (score > 70)      x0.7 applied to the full score

For stocks already held, position-aware adjustments apply on top:

  - Down > 20% on cost AND in a downtrend:   -10
  - Up > 20% on cost AND trend intact:        +5
  - Position exceeds 25% of portfolio:       -20

Score to action:
  >= 40   ->  ACCUMULATE
  >= -15  ->  HOLD
  >= -40  ->  TRIM
  < -40   ->  SELL

An oversized position (> 25%) is forced to TRIM unless the score clears 20.

Suggested position size anchors on equal-weight (100% / max(holdings, 5)), capped at
25%. A TRIM target is always min(current x 0.7, baseline) so it genuinely reduces the
position rather than recommending no change.


NEW STOCK IDEAS
---------------
We maintain a configurable universe of 20 NSE large-caps in application.properties.
Stocks already held are excluded. The rest are scored identically. Only stocks that
score >= 15 surface as BUY candidates; anything below that threshold is left out.
A list of stocks that do not clear the bar is noise, not guidance.

Scoring runs in parallel across a dedicated 8-thread executor pool. Running blocking
Yahoo Finance HTTP calls on the shared ForkJoinPool would starve other application
threads under load, so this workload gets its own pool.


WHERE THE LLM FITS IN
---------------------
The language model never makes a decision. Java computes the score, picks the action,
and assembles the contributing signals. The model receives the finished decision and is
asked only to rewrite justifications as readable English and produce a short narrative.

This separation was an explicit design choice we made early on:

  - The recommendation is fully reproducible and explainable without the model.
  - If the API key is missing, the model times out, or the response is malformed,
    the endpoint falls back to the rule-generated reason string and continues normally.
    The feature never fails because of model behaviour.
  - The response DTO carries an llmNarrated flag so the frontend can indicate whether
    the displayed text was AI-written or system-generated.

Both /api/risk and /api/recommendations are fully functional with no API key configured.
The AI layer is an enhancement, not a dependency.