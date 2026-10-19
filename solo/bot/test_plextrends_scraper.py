"""Regression tests for keyless PlexTrends live count extraction."""
import unittest

from plextrends_scraper import parse_live_counts


class PlexTrendsScraperTests(unittest.TestCase):
    def test_parses_network_total_and_current_game_rows(self):
        html = """
        <main>
          <h2>Currently Active Players</h2>
          <div>Online: 48</div>
          <div class="game"><span>Lobby</span><strong>25</strong></div>
          <div class="game"><span>Nanogames</span><strong>9</strong></div>
          <div class="game"><span>Supersmashmobslegacy</span><strong>8</strong></div>
          <div class="game"><span>Mixed Arcade</span><strong>3</strong></div>
          <h2>Total Unique Players</h2>
          <div>Historical Player Trends</div>
          <div>Mixed Arcade 999</div>
        </main>
        """
        result = parse_live_counts(html)
        self.assertEqual(result["network_players"], 48)
        self.assertEqual(result["mixed_arcade_count"], 3)
        self.assertTrue(result["mixed_arcade_explicit"])
        self.assertEqual(
            {row["name"]: row["count"] for row in result["game_counts"]},
            {"Lobby": 25, "Nano Games": 9, "Super Smash Mobs Legacy": 8, "Mixed Arcade": 3},
        )

    def test_omitted_game_defaults_inactive_only_when_live_section_is_valid(self):
        html = """
        <main>
          <h2>Currently Active Players</h2>
          <div>Online: 35</div>
          <div>Lobby 28</div>
          <div>Nano Games 7</div>
          <h2>Total Unique Players</h2>
          <div>Historical Player Trends</div>
          <div>Mixed Arcade 20</div>
        </main>
        """
        result = parse_live_counts(html)
        self.assertEqual(result["mixed_arcade_count"], 0)
        self.assertFalse(result["mixed_arcade_explicit"])

    def test_missing_live_section_is_a_scrape_failure_not_zero(self):
        with self.assertRaises(ValueError):
            parse_live_counts("<html><body>Temporary maintenance</body></html>")

    def test_ignores_script_content(self):
        html = """
        <h2>Currently Active Players</h2>
        <div>Online: 10</div>
        <div>Lobby 10</div>
        <script>Mixed Arcade 99</script>
        <h2>Total Unique Players</h2>
        """
        result = parse_live_counts(html)
        self.assertEqual(result["mixed_arcade_count"], 0)


if __name__ == "__main__":
    unittest.main()
