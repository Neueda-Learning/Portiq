import Card from "../common/Card";
import RecommendationCard from "./RecommendationCard";
import Disclaimer from "./Disclaimer";

/**
 * Shared body for the desktop and mobile recommendation views, so the two never drift apart on
 * what a recommendation actually shows.
 */
function RecommendationsPanel({ data }) {
  if (!data) return null;

  const holdings = data.holdings || [];
  const ideas = data.ideas || [];

  return (
    <>
      {data.narrative && (
        <div className="summary-banner">
          {data.narrative}
          {data.llmNarrated && <span className="narrated-badge">AI worded</span>}
        </div>
      )}

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Your Holdings</h2>
          <span className="meta-line">{holdings.length} analysed</span>
        </div>
        {holdings.length === 0 ? (
          <Card>
            <p className="meta-line">
              No holdings to review yet. Add some on the Holdings page and they will be scored here.
            </p>
          </Card>
        ) : (
          <div className="grid reco-grid">
            {holdings.map((recommendation) => (
              <RecommendationCard key={recommendation.ticker} recommendation={recommendation} />
            ))}
          </div>
        )}
      </section>

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Ideas to Consider</h2>
          <span className="meta-line">Not currently held</span>
        </div>
        {ideas.length === 0 ? (
          <Card>
            <p className="meta-line">
              Nothing outside your portfolio screens well enough to suggest right now.
            </p>
          </Card>
        ) : (
          <div className="grid reco-grid">
            {ideas.map((recommendation) => (
              <RecommendationCard key={recommendation.ticker} recommendation={recommendation} />
            ))}
          </div>
        )}
      </section>

      <Disclaimer text={data.disclaimer} />
    </>
  );
}

export default RecommendationsPanel;
