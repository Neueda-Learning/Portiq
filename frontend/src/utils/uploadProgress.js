/**
 * Shared state and wording for the import progress bar.
 *
 * Lives here rather than in each page because the desktop and mobile holdings screens run the
 * identical flow. Two copies is the arrangement where one quietly gains a fix the other does not,
 * and "the progress bar says something different on my phone" is a silly bug to ship.
 */

/**
 * Nothing in flight. A named constant so resetting is one obvious assignment rather than four
 * fields that can drift apart.
 */
export const NO_UPLOAD = { active: false, phase: null, percent: 0, filename: "", kind: "file" };

/**
 * What to show for each phase.
 *
 * The processing wording differs by import kind because the waits genuinely differ: a CSV is
 * parsed in milliseconds, while a statement image is sent to a vision model and can take the best
 * part of a minute. Saying so is the difference between a user waiting and a user reloading.
 */
export function uploadCopy(phase, kind) {
  if (phase === "uploading") {
    return { label: "Uploading", detail: null };
  }

  return kind === "image"
    ? {
        label: "Reading your statement",
        detail: "Extracting holdings from the image. This can take up to a minute.",
      }
    : {
        label: "Processing",
        detail: "Reading the file and matching it against your holdings.",
      };
}
