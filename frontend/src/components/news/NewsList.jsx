import Card from "../common/Card";

function NewsList({ articles }) {
  return (
    <Card>
      {articles.length === 0 ? (
        <p className="subtitle">No news available right now</p>
      ) : (
        <ul className="news-list">
          {articles.map((article, index) => (
            <li key={index} className="news-item">
              <a href={article.link} target="_blank" rel="noopener noreferrer" className="news-title">
                {article.title}
              </a>
              <div className="news-meta">
                <span>{article.source}</span>
                {article.relatedTicker && <span className="badge">{article.relatedTicker}</span>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

export default NewsList;
