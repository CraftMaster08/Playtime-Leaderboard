# Playtime Leaderboard

A Minecraft Forge mod for version 1.20.1 that adds a `/playtime` command to display playtime statistics for all players (online and offline) on a server or singleplayer world. The mod provides a detailed, formatted leaderboard with customizable colors, podium ranks, and alignment for an enhanced user experience.

## Features

### `/playtime` Command
- **Accessible to All Players**: Requires no permission, so anyone can use it.
- **Shows Playtime for All Players**:
  - Displays playtime for both online and offline players.
  - Hover over the hours display to see the time spent on this world today (Reset time can be edited in config)
  - Sorts players by playtime in descending order (highest to lowest).
  - Setting colors for specific usernames and blacklisting players from leaderboard is possible in the config (see `Configuration`)

## Usage
- **Command**: `/playtime`
- **Command**: `/playtime reload` to reload config (requires OP on Server)
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

## Configuration
- **Config File**: `config/playtimeleaderboard_config.json`

- **Custom Username Colors**: Specify colors for specific usernames in the leaderboard.
- **Blacklist Players**: Exclude specific players from the `/playtime` leaderboard.
- **Reloading Config**:
- Edit the config file while the server is running.
- Run `/playtime reload` (admin required) to apply changes without restarting the server.
- **Example**:
```json
{
  "username_colors": {
    "CraftMaster2008": "dark_aqua",
    "RedPlayer123": "red"
  },
  "blacklisted_players": [
    "MeanPlayer456",
    "SecretPlayer80"
  ],
  "daily_reset_time": "00:00:00 UTC"
}
```

---

## Future Plans
- **Better Formatting for Hours**:
  - Improve the hours display

## License
- This mod is released under the [MIT License](LICENSE). Feel free to use, modify, and distribute it as per the license terms.