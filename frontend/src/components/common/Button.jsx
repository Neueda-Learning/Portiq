function Button({
  children,
  variant = "primary",
  type = "button",
  onClick,
  className = "",
  loading = false,
  disabled = false,
  ...rest
}) {
  return (
    <button
      type={type}
      className={`button ${variant === "ghost" ? "ghost" : "primary"} ${loading ? "is-loading" : ""} ${className}`.trim()}
      onClick={onClick}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <span className={`spinner ${variant === "ghost" ? "dark" : ""}`.trim()} aria-hidden="true" />}
      <span>{children}</span>
    </button>
  );
}

export default Button;
