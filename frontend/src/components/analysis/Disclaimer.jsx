function Disclaimer({ text }) {
  if (!text) return null;
  return <p className="analysis-disclaimer">{text}</p>;
}

export default Disclaimer;
