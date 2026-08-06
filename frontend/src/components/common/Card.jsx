function Card({ children, className = "", padded = true }) {
  return <section className={`card ${padded ? "pad" : ""} ${className}`.trim()}>{children}</section>;
}

export default Card;
