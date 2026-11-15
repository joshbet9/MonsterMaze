"""Logging helpers for GitHub webhook work dispatched to the Discord loop."""


def observe_background_future(future, event, logger=print):
    """Log asynchronous webhook failures that occur after HTTP acceptance."""

    def completed(done_future):
        try:
            done_future.result()
        except Exception as exc:
            logger(
                f"[github-webhook] asynchronous processing failed for "
                f"{event}: {exc!r}",
                flush=True,
            )

    future.add_done_callback(completed)
    return future
