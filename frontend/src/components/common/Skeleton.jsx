function Skeleton({ width = "100%", height = "14px", className = "", style = {} }) {
  return <span className={`skeleton ${className}`.trim()} style={{ width, height, ...style }} />;
}

export default Skeleton;
