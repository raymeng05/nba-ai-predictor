package com.nba.nba_predictor.player;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PlayerService {
    
    private final PlayerRepository playerRepository;

    @Autowired
    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public List<Player> getPlayers() {
        return playerRepository.findAll();
    }
    public List<Player> getPlayersFromTeam(String team) {
        return playerRepository.findAll().stream().filter(player -> player.getTeam().equalsIgnoreCase(team)).toList();
    }
}
