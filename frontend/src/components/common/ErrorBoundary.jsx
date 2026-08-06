import { Component } from "react";

/**
 * Catches render-time exceptions so one broken component does not blank the whole app.
 *
 * <p>React unmounts the entire tree when a render throws and nothing catches it. Without a
 * boundary the user gets a white page with no explanation and no way forward - and on a portfolio
 * screen that is indistinguishable from "my data is gone", which is the worst possible thing for
 * this particular app to imply.
 *
 * <p>Must be a class: `componentDidCatch` and `getDerivedStateFromError` have no hook equivalent.
 * This is one of the few places React still requires one.
 *
 * <p>Deliberately shows the error text only in development. In a production build the message can
 * carry component internals, and a user cannot act on it anyway - they get a reload button, which
 * is the only thing that actually helps.
 */
class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, errorInfo) {
    // Kept as console.error rather than swallowed: this is the only record that the crash
    // happened, and it is what a developer or a browser-side error reporter will look for.
    console.error("Unhandled render error", error, errorInfo);
  }

  handleReload = () => {
    this.setState({ error: null });
    window.location.reload();
  };

  render() {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }

    return (
      <div className="error-boundary" role="alert">
        <h1>Something went wrong on this page</h1>
        <p className="subtitle">
          Your holdings are safe - this is a display problem. Reloading usually clears it.
        </p>
        {import.meta.env.DEV && (
          <pre className="error-boundary-detail">{String(error?.stack || error)}</pre>
        )}
        <button type="button" className="btn btn-primary" onClick={this.handleReload}>
          Reload the page
        </button>
      </div>
    );
  }
}

export default ErrorBoundary;
