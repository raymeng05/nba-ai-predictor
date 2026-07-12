package com.nba.nba_predictor.player;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
// every method returns a domain object instead of a view

@RequestMapping(path = "api/player") 
public class PlayerController {

    private final PlayerService playerService;

    @Autowired
    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    // handles get requests -> gets players from the database
    @GetMapping
    public List<Player> getPlayers(
            @RequestParam(required = false) String team,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String pos) {

        if (team != null && pos != null) {
            return playerService.getPlayersByTeamAndPosition(team, pos);
        } else if (team != null) {
            return playerService.getPlayersFromTeam(team);
        } else if (name != null) {
            return playerService.getPlayersByName(name);
        } else if (pos != null) {
            return playerService.getPlayersByPosition(pos);
        } else {
            return playerService.getPlayers();
        }
    }

    // handles post requests -> adds player to database
    @PostMapping
    public ResponseEntity<Player> addPlayer(@RequestBody Player player){
        Player newPlayer = playerService.addPlayer(player);
        return new ResponseEntity<>(newPlayer, HttpStatus.CREATED);
    }

    // handles put requests -> updates a player in the repository
    @PutMapping
    public ResponseEntity<Player> updatePlayer(@RequestBody Player newPlayer) { // param newPlayer is the new info of the player we want to update
        Player resultPlayer = playerService.updatePlayer(newPlayer);
        if (resultPlayer != null) {
            return new ResponseEntity<>(resultPlayer, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    // handles delete requests -> deletes a player from the db
    @DeleteMapping("/{name}")
    public ResponseEntity<String> deletePlayer(@PathVariable String name) {
        playerService.deletePlayer(name);
        return new ResponseEntity<>("Successful deletion", HttpStatus.OK);
    }

    
}

