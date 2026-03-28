package de.uniwue.zpd.dachs.larex.backend.dto;

public record AdminErrorSummaryDto(
        int windowDays,
        long totalEvents,
        long serverErrors,
        long actionableClientErrors,
        long distinctUsers,
        long distinctWorkspaces
) {
}
