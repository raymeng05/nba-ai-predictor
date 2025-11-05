package com.nba.nba_predictor.player;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.transaction.Transactional;

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
        return playerRepository.findAll().stream().filter(player -> team.equals(player.getTeam())).collect(Collectors.toList());
    }

    public List<Player> getPlayersByName(String search) {
        return playerRepository.findAll().stream().filter(player -> player.getName().toLowerCase().contains(search.toLowerCase())).collect(Collectors.toList());
    }

    public List<Player> getPlayersByPosition(String search) {
        return playerRepository.findAll().stream().filter(player -> player.getPosition().toLowerCase().contains(search.toLowerCase())).collect(Collectors.toList());
    }

    public List<Player> getPlayersByTeamAndPosition(String team, String pos){
        return playerRepository.findAll().stream().filter(player -> team.equals(player.getTeam()) && pos.equals(player.getPosition())).collect(Collectors.toList());
    }

    public Player addPlayer(Player player) {
        playerRepository.save(player); // built into jpa repository as well
        return player;
    }

    public Player updatePlayer(Player newPlayer) {
        // find the player with the given name
        Optional<Player> existingPlayer = playerRepository.findByName(newPlayer.getName()); // the container that holds the value

        // update that player with the new info passed in
        if (existingPlayer.isPresent()) {
            Player playerToUpdate = existingPlayer.get(); // gets the actual value (Player)
            playerToUpdate.setName(newPlayer.getName());
            playerToUpdate.setTeam(newPlayer.getTeam());
            playerToUpdate.setPosition(newPlayer.getPosition());
            playerRepository.save(playerToUpdate);
            return playerToUpdate;
        }
        return null;
    }

    // ensures all db operations within this method are a single unit
    // maintains db integrity (if the delete fails, it will simply revert back, otherwise, transaction will go through)
    @Transactional
    public void deletePlayer(String name) {
        playerRepository.deleteByName(name);
    }
}
