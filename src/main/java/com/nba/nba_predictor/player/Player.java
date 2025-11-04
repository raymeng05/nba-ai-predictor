package com.nba.nba_predictor.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name ="player_statistic")
public class Player {

    @Id
    @Column(name = "player_name", unique = true)
    private String name;

    private int age;

    private String team;

    private String position;

    private int games;

    private int gamesStarted;

    private double minutesPlayed;

    private double fieldGoals;
    
    private double fieldGoalsAttempted;

    private double fieldGoalPct;

    private double threePointers;

    private double threePointersAttempted;

    private double threePointerPct;

    private double twoPointers;

    private double twoPointersAttempted;

    private double twoPointerPct;

    private double effectiveFieldGoalPct;

    private double freeThrows;

    private double freeThrowsAttempted;

    private double freeThrowPct;

    private double offensiveRebounds;

    private double defensiveRebounds;

    private double totalRebounds;

    private double assists;

    private double steals;

    private double blocks;

    private double turnovers;

    private double personalFouls;

    private double points;

    //constructors
    public Player() {
    }
    // Full-args constructor
    public Player(String name, int age, String team, String position, int games, int gamesStarted,
                  double minutesPlayed, double fieldGoals, double fieldGoalsAttempted, double fieldGoalPct,
                  double threePointers, double threePointersAttempted, double threePointerPct,
                  double twoPointers, double twoPointersAttempted, double twoPointerPct,
                  double effectiveFieldGoalPct, double freeThrows, double freeThrowsAttempted, double freeThrowPct,
                  double offensiveRebounds, double defensiveRebounds, double totalRebounds,
                  double assists, double steals, double blocks, double turnovers,
                  double personalFouls, double points) {
        this.name = name;
        this.age = age;
        this.team = team;
        this.position = position;
        this.games = games;
        this.gamesStarted = gamesStarted;
        this.minutesPlayed = minutesPlayed;
        this.fieldGoals = fieldGoals;
        this.fieldGoalsAttempted = fieldGoalsAttempted;
        this.fieldGoalPct = fieldGoalPct;
        this.threePointers = threePointers;
        this.threePointersAttempted = threePointersAttempted;
        this.threePointerPct = threePointerPct;
        this.twoPointers = twoPointers;
        this.twoPointersAttempted = twoPointersAttempted;
        this.twoPointerPct = twoPointerPct;
        this.effectiveFieldGoalPct = effectiveFieldGoalPct;
        this.freeThrows = freeThrows;
        this.freeThrowsAttempted = freeThrowsAttempted;
        this.freeThrowPct = freeThrowPct;
        this.offensiveRebounds = offensiveRebounds;
        this.defensiveRebounds = defensiveRebounds;
        this.totalRebounds = totalRebounds;
        this.assists = assists;
        this.steals = steals;
        this.blocks = blocks;
        this.turnovers = turnovers;
        this.personalFouls = personalFouls;
        this.points = points;
    }

    public Player(String name) {
        this.name = name;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }
    public String getTeam() {
        return team;
    }
    public void setTeam(String team) {
        this.team = team;
    }
    public String getPosition() {
        return position;
    }
    public void setPosition(String position) {
        this.position = position;
    }
    public int getGames() {
        return games;
    }
    public void setGames(int games) {
        this.games = games;
    }
    public int getGamesStarted() {
        return gamesStarted;
    }
    public void setGamesStarted(int gamesStarted) {
        this.gamesStarted = gamesStarted;
    }
    public double getMinutesPlayed() {
        return minutesPlayed;
    }
    public void setMinutesPlayed(double minutesPlayed) {
        this.minutesPlayed = minutesPlayed;
    }
    public double getFieldGoals() {
        return fieldGoals;
    }
    public void setFieldGoals(double fieldGoals) {
        this.fieldGoals = fieldGoals;
    }
    public double getFieldGoalsAttempted() {
        return fieldGoalsAttempted;
    }
    public void setFieldGoalsAttempted(double fieldGoalsAttempted) {
        this.fieldGoalsAttempted = fieldGoalsAttempted;
    }
    public double getFieldGoalPct() {
        return fieldGoalPct;
    }
    public void setFieldGoalPct(double fieldGoalPct) {
        this.fieldGoalPct = fieldGoalPct;
    }
    public double getThreePointers() {
        return threePointers;
    }
    public void setThreePointers(double threePointers) {
        this.threePointers = threePointers;
    }
    public double getThreePointersAttempted() {
        return threePointersAttempted;
    }
    public void setThreePointersAttempted(double threePointersAttempted) {
        this.threePointersAttempted = threePointersAttempted;
    }
    public double getThreePointerPct() {
        return threePointerPct;
    }
    public void setThreePointerPct(double threePointerPct) {
        this.threePointerPct = threePointerPct;
    }
    public double getTwoPointers() {
        return twoPointers;
    }
    public void setTwoPointers(double twoPointers) {
        this.twoPointers = twoPointers;
    }
    public double getTwoPointersAttempted() {
        return twoPointersAttempted;
    }
    public void setTwoPointersAttempted(double twoPointersAttempted) {
        this.twoPointersAttempted = twoPointersAttempted;
    }
    public double getTwoPointerPct() {
        return twoPointerPct;
    }
    public void setTwoPointerPct(double twoPointerPct) {
        this.twoPointerPct = twoPointerPct;
    }
    public double getEffectiveFieldGoalPct() {
        return effectiveFieldGoalPct;
    }
    public void setEffectiveFieldGoalPct(double effectiveFieldGoalPct) {
        this.effectiveFieldGoalPct = effectiveFieldGoalPct;
    }
    public double getFreeThrows() {
        return freeThrows;
    }
    public void setFreeThrows(double freeThrows) {
        this.freeThrows = freeThrows;
    }
    public double getFreeThrowsAttempted() {
        return freeThrowsAttempted;
    }
    public void setFreeThrowsAttempted(double freeThrowsAttempted) {
        this.freeThrowsAttempted = freeThrowsAttempted;
    }
    public double getFreeThrowPct() {
        return freeThrowPct;
    }
    public void setFreeThrowPct(double freeThrowPct) {
        this.freeThrowPct = freeThrowPct;
    }
    public double getOffensiveRebounds() {
        return offensiveRebounds;
    }
    public void setOffensiveRebounds(double offensiveRebounds) {
        this.offensiveRebounds = offensiveRebounds;
    }
    public double getDefensiveRebounds() {
        return defensiveRebounds;
    }
    public void setDefensiveRebounds(double defensiveRebounds) {
        this.defensiveRebounds = defensiveRebounds;
    }
    public double getTotalRebounds() {
        return totalRebounds;
    }
    public void setTotalRebounds(double totalRebounds) {
        this.totalRebounds = totalRebounds;
    }
    public double getAssists() {
        return assists;
    }
    public double getSteals() {
        return steals;
    }
    public void setSteals(double steals) {
        this.steals = steals;
    }
    public double getBlocks() {
        return blocks;
    }
    public void setBlocks(double blocks) {
        this.blocks = blocks;
    }
    public double getTurnovers() {
        return turnovers;
    }
    public void setTurnovers(double turnovers) {
        this.turnovers = turnovers;
    }
    public double getPersonalFouls() {
        return personalFouls;
    }
    public void setPersonalFouls(double personalFouls) {
        this.personalFouls = personalFouls;
    }
    public double getPoints() {
        return points;
    }
    public void setPoints(double points) {
        this.points = points;
    }
}
