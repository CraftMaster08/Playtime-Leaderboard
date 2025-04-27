# Playtime Leaderboard

A Minecraft Forge mod for version 1.20.1 that adds a `/playtime` command to display playtime statistics for all players (online and offline) on a server or singleplayer world. The mod provides a detailed, formatted leaderboard with customizable colors, podium ranks, and alignment for an enhanced user experience.

## Features

### `/playtime` Command
- **Accessible to All Players**: Requires no permission, so anyone can use it.
- **Shows Playtime for All Players**:
  - Displays playtime for both online and offline players.
  - Reads player stats from `world/stats` folder.
  - Calculates playtime in hours by converting ticks to hours.
- **Sorted Leaderboard**:
  - Sorts players by playtime in descending order (highest to lowest).

## Usage
- **Command**: `/playtime`
- **Example Output**:
<image>

### Formatting
- **Days Display**:
  - For players with 100+ hours, shows days in parentheses (e.g., `(54.17d)`).
- **Hours Decimal Display**:
  - Below 1000h: Displays hours with 2 decimal places (e.g., `493.15h`).
  - At or above 1000h: Displays hours as integers (e.g., `1300h`).
- **Color Coding**:
<image>

### Technical Details
- **Error Handling while getting Usernames**:
  - Logs errors for stat file reading and Mojang API failures to `latest.log`.
  - Gets the Username from Mojang's API if the Username cache doesn't give a result.
  - Falls back to `Unknown_<uuid>` for usernames if API fails.
  
---

## Future Plans (Before Release)
- **Name Color in Config File**:
  - currently fixed as white
- **Config to Blacklist Players**:
  - config option to blacklist specific players, excluding them from the `/playtime` leaderboard
- **Better Formatting for Hours**:
  - Improve the hours display
  - configs for display colors

## License
- This mod is released under the [MIT License](LICENSE). Feel free to use, modify, and distribute it as per the license terms.