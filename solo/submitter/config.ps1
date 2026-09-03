# Monster Maze SOLO - submission config (Discord webhooks only, no backend/IP).
#
# The submitter posts EVERY PB to the #solo-runs feed (the default webhook below).
# The leaderboard bot watches #solo-runs, then posts the ranked boards into the
# per-mode #leaderboard-* channels. Keep the per-mode map EMPTY so runs always
# land in the feed - never in the leaderboard channels directly (that would widen
# the boards with raw PB messages and collide with the bot's standings).
$WEBHOOKS = @{ }

# The #solo-runs feed webhook. Every PB lands here and the bot reads it.
$DEFAULT_WEBHOOK = ""
