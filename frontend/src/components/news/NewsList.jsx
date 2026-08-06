import Card from "../common/Card";

/**
 * Headline links come from third-party RSS feeds. React escapes text but not URLs, so a feed item
 * with a `javascript:` href would run in the user's session on click. The backend already drops
 * those, and this repeats the check because it is the last step before the browser sees the value.
 */
function safeHref(link) {
  if (typeof link !== "string") return null;
  const trimmed = link.trim().toLowerCase();
  return trimmed.startsWith("http://") || trimmed.startsWith("https://") ? link.trim() : null;
}

function NewsList({ articles }) {
  return (
    <Card>
      {articles.length === 0 ? (
        <p className="subtitle">No news available right now</p>
      ) : (
        <ul className="news-list">
          {articles.map((article, index) => {
            const href = safeHref(article.link);
            return (
              <li key={index} className="news-item">
                {href ? (
                  <a href={href} target="_blank" rel="noopener noreferrer" className="news-title">
                    {article.title}
                  </a>
                ) : (
                  <span className="news-title">{article.title}</span>
                )}
                <div className="news-meta">
                  <span>{article.source}</span>
                  {article.relatedTicker && <span className="badge">{article.relatedTicker}</span>}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

export default NewsList;
