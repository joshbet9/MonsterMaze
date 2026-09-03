# Discord Channel Structure for Hierarchical Leaderboard

## Current Structure (Single Mode Channels)
- modern: 1543539094259236905
- speed: 1543539128921104384
- lagless: 1543542027101413506
- original: 1543539164387999795

## Required New Structure (Mode → Pattern → Kit)

### Main Mode Channels (Existing)
Each mode channel will need a sub-channel for maze patterns:
- `modern` → `maze-patterns`
- `speed` → `maze-patterns` 
- `lagless` → `maze-patterns`
- `original` → `maze-patterns`

### Pattern Channels (Nested under Mode)
Under each pattern channel, we'll need individual kit channels:
- `maze-patterns` → `kit-records`
- `maze-patterns` → `kit-records`

### Required New Channels
1. **Pattern sub-channels** (for each mode):
   - `modern/maze-patterns`
   - `speed/maze-patterns`
   - `lagless/maze-patterns`
   - `original/maze-patterns`

2. **Kit record sub-channels** (for each pattern):
   - `modern/maze-patterns/kit-records`
   - `speed/maze-patterns/kit-records`
   - `lagless/maze-patterns/kit-records`
   - `original/maze-patterns/kit-records`

## Recommendations
1. All new channels should be created as text channels for bot management
2. Each pattern channel will host multiple kit record sub-channels depending on available kits
3. Bot code needs to be updated to:
   - Read pattern and kit names from the PB embed (currently only reading mode, pattern, kit)
   - Store these in the new `pattern_name` and `kit_name` database columns  
   - Post leaderboard entries to the appropriate hierarchical channel path

## Implementation Notes
- The bot implementation needs to parse the new data from the PB embed posts
- Each new channel level will need to be managed by the bot with proper permissions
- Database schema supports the new name fields but logic flow must be updated to use them in hierarchical navigation