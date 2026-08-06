# Design Philosophy: AI Enhances, Logic Decides

There's one rule that holds across all four AI features in Portiq:

**Deterministic code makes the decisions. AI writes the words.**

The portfolio performance math is plain deterministic Java arithmetic. Risk metrics like beta, Sharpe ratio, drawdown, and VaR come from deterministic statistical formulas. The recommendation action — BUY, HOLD, or SELL — comes out of a fixed quantitative scoring algorithm. Netting BUY/SELL rows during import runs through weighted-average-cost accounting written in Java, and validating and coercing data is handled entirely by the Java service layer.

Generative AI, on the other hand, only ever touches the language side of things. It explains findings in plain language, summarises a whole portfolio in plain English, makes sense of inconsistent broker export formats, and — via the vision model — reads holdings out of a photo.

The practical upshot: if you strip out the AI configuration entirely, Portiq keeps working — you just get slightly plainer text instead of a polished narrative. It also means an AI hiccup (timeout, bad response, whatever) can never corrupt your financial data or quietly change an investment signal. The numbers are never in the model's hands.
