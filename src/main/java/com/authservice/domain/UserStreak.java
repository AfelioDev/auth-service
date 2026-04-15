package com.authservice.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class UserStreak {

    private Long userId;
    private int currentStreak;
    private int longestStreak;
    private LocalDate lastSolveDate;
    private String lastTimezone;
    private LocalDateTime startedAt;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public LocalDate getLastSolveDate() { return lastSolveDate; }
    public void setLastSolveDate(LocalDate lastSolveDate) { this.lastSolveDate = lastSolveDate; }

    public String getLastTimezone() { return lastTimezone; }
    public void setLastTimezone(String lastTimezone) { this.lastTimezone = lastTimezone; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
