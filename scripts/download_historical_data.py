#!/usr/bin/env python3
"""Refresh the bundled historical player-season training data.

The source catalog is maintained by the open-source nba_data project and is
built from stats.nba.com. Seasons are represented by their ending year, so 2025
means the 2024-25 season.
"""

import argparse
import tempfile
from pathlib import Path

import duckdb


CATALOG_URL = "https://basketball-data.billmill.org/nba/nba.duckdb"
DEFAULT_OUTPUT = (
    Path(__file__).resolve().parents[1]
    / "src/main/resources/ml/historical_player_seasons.csv"
)


def download(start_season, end_season, output):
    if start_season >= end_season:
        raise ValueError("At least two consecutive seasons are required.")

    output = Path(output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    extension_directory = (
        Path(tempfile.gettempdir()) / "nba-predictor-duckdb-extensions"
    )

    connection = duckdb.connect()
    safe_extension_directory = str(extension_directory).replace("'", "''")
    connection.execute(
        f"SET extension_directory='{safe_extension_directory}'"
    )
    connection.execute("INSTALL httpfs")
    connection.execute("LOAD httpfs")
    connection.execute(f"ATTACH '{CATALOG_URL}' AS nba")

    query = f"""
        SELECT
            s.season,
            s.player_id AS "playerId",
            p.name,
            CAST(round(s.age) AS INTEGER) AS age,
            coalesce(t.nba_abbrev, 'UNK') AS team,
            s.gp AS games,
            round(s.min, 6) AS "minutesPlayed",
            round(s.pts, 6) AS points,
            round(s.reb, 6) AS "totalRebounds",
            round(s.ast, 6) AS assists,
            round(s.stl, 6) AS steals,
            round(s.blk, 6) AS blocks,
            round(s.tov, 6) AS turnovers,
            round(s.fg_pct, 6) AS "fieldGoalPct",
            round(s.fg3_pct, 6) AS "threePointerPct",
            round(s.ft_pct, 6) AS "freeThrowPct",
            round(s.efg_pct, 6) AS "effectiveFieldGoalPct"
        FROM nba.player_season_stats_per_game s
        JOIN nba.players p USING (player_id)
        LEFT JOIN nba.team_seasons t USING (season, team_id)
        WHERE s.season BETWEEN {int(start_season)} AND {int(end_season)}
          AND s.season_type = 'regular_season'
          AND s.gp > 0
        ORDER BY s.season, s.player_id
    """
    safe_output = str(output).replace("'", "''")
    connection.execute(
        f"COPY ({query}) TO '{safe_output}' (HEADER, DELIMITER ',')"
    )

    season_counts = connection.sql(
        f"SELECT season, count(*) AS players FROM ({query}) "
        "GROUP BY season ORDER BY season"
    ).fetchall()
    print(f"Wrote {sum(count for _, count in season_counts)} rows to {output}")
    for season, count in season_counts:
        print(f"  {season - 1}-{str(season)[-2:]}: {count} players")


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--start-season", type=int, default=2016)
    parser.add_argument("--end-season", type=int, default=2025)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    return parser.parse_args()


if __name__ == "__main__":
    arguments = parse_args()
    download(arguments.start_season, arguments.end_season, arguments.output)
